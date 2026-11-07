package com.shilapi.xcertplay

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import java.io.Closeable

/** Passive, session-scoped observations only. Never requests focus, changes routes or records PCM. */
internal class AudioBehaviorDiagnostics(
    context: Context,
    private val target: SessionLogFile?,
) : Closeable {
    private val app = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var started = false
    private var closed = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (closed || intent == null) return
            broadcastEvent(intent)?.let(::event)
            snapshot("broadcast")
        }
    }
    private val poll = object : Runnable {
        override fun run() {
            if (closed) return
            snapshot("periodic")
            handler.postDelayed(this, 5_000L)
        }
    }

    fun start() {
        if (started || closed) return
        started = true
        event("observer start api=${Build.VERSION.SDK_INT}; app events only; OEM-private audio may be invisible")
        val filter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED)
            addAction(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            addAction(AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        runCatching {
            // These protected system actions may originate from the separate Bluetooth UID.
            if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else {
                @Suppress("DEPRECATION")
                app.registerReceiver(receiver, filter)
            }
            registered = true
        }.onFailure { event("observer broadcasts unavailable error=${it.javaClass.simpleName}") }
        snapshot("attach")
        handler.postDelayed(poll, 5_000L)
    }

    fun event(message: String) = record(target, message)

    @Suppress("DEPRECATION")
    private fun snapshot(reason: String) {
        runCatching {
            val manager = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return@runCatching event("snapshot reason=$reason unavailable")
            event("snapshot reason=$reason mode=${manager.mode} musicActive=${manager.isMusicActive} " +
                "a2dpOn=${manager.isBluetoothA2dpOn} scoOn=${manager.isBluetoothScoOn} " +
                "wiredOn=${manager.isWiredHeadsetOn} speakerOn=${manager.isSpeakerphoneOn} " +
                "musicVolume=${manager.getStreamVolume(AudioManager.STREAM_MUSIC)}")
        }.onFailure { event("snapshot reason=$reason unavailable error=${it.javaClass.simpleName}") }
    }

    override fun close() {
        if (closed) return
        closed = true
        handler.removeCallbacks(poll)
        if (registered) runCatching { app.unregisterReceiver(receiver) }
        registered = false
        event("observer stop")
    }

    companion object {
        fun record(target: SessionLogFile?, message: String, uptimeMillis: Long = SystemClock.elapsedRealtime()) {
            // The async writer redacts and bounds messages before storing them. No disk I/O here.
            AsyncDiagnosticLog.append(target, "AUDIO_EVENT uptimeMs=$uptimeMillis $message GL19_AUDIO_TEST=${Gl19AudioExperiment.marker}")
        }

        internal fun broadcastEvent(intent: Intent): String? = when (intent.action) {
            BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED ->
                "broadcast a2dpConnection previous=${intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)} " +
                    "state=${intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)}"
            BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED ->
                "broadcast a2dpPlaying previous=${intent.getIntExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, -1)} " +
                    "state=${intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)}"
            AudioManager.ACTION_AUDIO_BECOMING_NOISY -> "broadcast audioBecomingNoisy"
            AudioManager.ACTION_SCO_AUDIO_STATE_UPDATED ->
                "broadcast sco previous=${intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_PREVIOUS_STATE, -1)} " +
                    "state=${intent.getIntExtra(AudioManager.EXTRA_SCO_AUDIO_STATE, -1)}"
            Intent.ACTION_HEADSET_PLUG -> "broadcast wiredHeadset state=${intent.getIntExtra("state", -1)}"
            else -> null
        }

        fun exportSummary(flushed: Boolean): String =
            Gl19AudioExperiment.summary(Build.VERSION.SDK_INT) + "\n" +
                "Audio behavior: search AUDIO_EVENT, Audio:, and audio stats in session logs below.\n" +
                "Metadata only: commands, reported playback, focus, buffer and public route observations; no recorded sound.\n" +
                "No system-wide focus owner / OEM-private Bluetooth visibility; absence of an event is not proof it did not occur.\n" +
                "Audio snapshots are device-wide hints, not proof of this app's route.\n" +
                "Pending diagnostic writes drained=$flushed; dropped queued entries since process start=${AsyncDiagnosticLog.droppedCount}"
    }
}
