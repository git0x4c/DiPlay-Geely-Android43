package com.shilapi.xcertplay.media

import android.os.Build
import com.shilapi.xcertplay.airplay.AudioCodecKind
import com.shilapi.xcertplay.airplay.AudioFormat
import com.shilapi.xcertplay.airplay.AudioStreamId
import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusEncoder
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.util.ReflectionHelpers
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Runs SDK19 branches under the SDK28 Android shadows; not an Android19 device test. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SoftwareOpusRendererTest {
    @Test fun legacyNavigationProducesPcmAndStartsTrack() = render(101, "default")
    @Test fun legacySpeechDownlinkProducesPcmAndStartsTrack() = render(100, "speechrecognition")

    private fun render(type: Int, audioType: String) {
        val api = Build.VERSION.SDK_INT
        ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", 19)
        val events = Collections.synchronizedList(mutableListOf<String>())
        val played = CountDownLatch(1)
        val exited = CountDownLatch(1)
        val sink = AndroidMediaSink(onAudioDiagnostic = { line ->
            events.add(line)
            if (line.startsWith("Audio: track play")) played.countDown()
            if (line.startsWith("Audio: renderer worker exit")) exited.countDown()
        })
        try {
            val id = AudioStreamId(type, audioType)
            val format = AudioFormat(AudioCodecKind.OPUS, 48_000, 1, type, audioType)
            val encoder = OpusEncoder(48_000, 1, OpusApplication.OPUS_APPLICATION_AUDIO)
            val compressed = ByteArray(4_000)
            sink.onAudioStarted(id, format, 0)
            repeat(12) { frame ->
                val size = encoder.encode(ShortArray(960) { ((it % 40 - 20) * 250).toShort() }, 0, 960, compressed, 0, compressed.size)
                sink.onAudioRtp(id, format, ByteArray(12) + compressed.copyOf(size), frame * 960)
            }
            assertTrue(events.toString(), played.await(5, TimeUnit.SECONDS))
            assertTrue(events.any { it.contains("decoder selected codec=OPUS backend=concentus-software") })
            assertTrue(events.any { it.contains("decoder first output codec=OPUS") })
            assertTrue(events.any { it.contains("Audio: first PCM") })
            assertFalse(events.any { it.contains("renderer failed") || it.contains("decoder init failed") })
        } finally {
            sink.close()
            try {
                assertTrue("Renderer must stop", exited.await(5, TimeUnit.SECONDS))
            } finally {
                ReflectionHelpers.setStaticField(Build.VERSION::class.java, "SDK_INT", api)
            }
        }
    }
}
