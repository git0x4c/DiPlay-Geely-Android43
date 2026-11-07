package com.shilapi.xcertplay

import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Looper
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class AudioBehaviorDiagnosticsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun recordsMonotonicTimelineAndRedactsSecretsWithoutAudioContent() {
        val file = temporary.newFile("diplay.log")
        val log = SessionLogFile(file)
        AudioBehaviorDiagnostics.record(log, "iphonePlayback playing=false", 1_234L)
        AudioBehaviorDiagnostics.record(log, "mediaCommand source=pause index=2", 1_235L)
        AudioBehaviorDiagnostics.record(log, "token=private", 1_236L)
        assertTrue(AsyncDiagnosticLog.awaitIdle(2_000L))
        val saved = file.readText()
        assertTrue(saved.contains("AUDIO_EVENT uptimeMs=1234 iphonePlayback playing=false"))
        assertTrue(saved.contains("mediaCommand source=pause"))
        assertTrue(saved.contains("GL19_AUDIO_TEST=${Gl19AudioExperiment.marker}"))
        assertFalse(saved.contains("private"))
        log.close()
    }

    @Test fun broadcastLoggingKeepsStateButNeverNamesOrDeviceIdentifiers() {
        val event = AudioBehaviorDiagnostics.broadcastEvent(Intent(BluetoothA2dp.ACTION_PLAYING_STATE_CHANGED)
            .putExtra(BluetoothProfile.EXTRA_PREVIOUS_STATE, 10)
            .putExtra(BluetoothProfile.EXTRA_STATE, 11)
            .putExtra("name", "private-phone")
            .putExtra("address", "00:11:22:33:44:55"))!!
        assertEquals("broadcast a2dpPlaying previous=10 state=11", event)
        assertNull(AudioBehaviorDiagnostics.broadcastEvent(Intent("unknown.action")))
    }

    @Test fun summaryDisclosesFlushAndVisibilityLimits() {
        val summary = AudioBehaviorDiagnostics.exportSummary(false)
        assertTrue(summary.contains("drained=false"))
        assertTrue(summary.contains("No system-wide focus owner"))
        assertTrue(summary.contains("no recorded sound"))
    }

    @Test fun observesWhileActiveAndCancelsPeriodicWorkWhenClosed() {
        val file = temporary.newFile("observer.log")
        val log = SessionLogFile(file)
        val app = RuntimeEnvironment.getApplication()
        val observer = AudioBehaviorDiagnostics(app, log)
        observer.start()
        observer.start() // No duplicate receiver or timers.
        app.sendBroadcast(Intent(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idle()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        observer.close()
        assertTrue(AsyncDiagnosticLog.awaitIdle(2_000L))
        val before = file.readText()
        assertEquals(1, before.lineSequence().count { it.contains("observer start") })
        assertTrue(before.contains("broadcast audioBecomingNoisy"))
        assertTrue(before.contains("snapshot reason=periodic"))
        app.sendBroadcast(Intent(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(10))
        assertTrue(AsyncDiagnosticLog.awaitIdle(2_000L))
        assertEquals(before, file.readText())
        log.close()
    }
}
