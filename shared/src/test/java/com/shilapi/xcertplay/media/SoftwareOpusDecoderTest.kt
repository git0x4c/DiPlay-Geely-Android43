package com.shilapi.xcertplay.media

import io.github.jaredmdobson.concentus.OpusApplication
import io.github.jaredmdobson.concentus.OpusEncoder
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.sin

class SoftwareOpusDecoderTest {
    @Test fun oldSystemsUseSoftwareWithoutChangingModernSelection() {
        assertTrue(SoftwareOpusDecoder.usesSoftware(18))
        assertTrue(SoftwareOpusDecoder.usesSoftware(19))
        assertTrue(SoftwareOpusDecoder.usesSoftware(20))
        assertFalse(SoftwareOpusDecoder.usesSoftware(21))
        assertFalse(SoftwareOpusDecoder.usesSoftware(37))
    }

    @Test fun decodesFiftyReal20msPacketsToNonSilentPcm16() {
        for (channels in listOf(1, 2)) {
            val encoder = OpusEncoder(48_000, channels, OpusApplication.OPUS_APPLICATION_AUDIO)
            val decoder = SoftwareOpusDecoder(48_000, channels)
            val encoded = ByteArray(4_000)
            var nonZeroSamples = 0
            repeat(50) { frame ->
                val input = ShortArray(960 * channels) { index ->
                    (sin(2 * Math.PI * 440 * (frame * 960 + index / channels) / 48_000) * 8_000).toInt().toShort()
                }
                val count = encoder.encode(input, 0, 960, encoded, 0, encoded.size)
                assertTrue(count > 0)
                val pcm = decoder.decode(encoded.copyOf(count))
                assertEquals(960 * channels * 2, pcm.size)
                for (i in pcm.indices step 2) {
                    val sample = ((pcm[i].toInt() and 255) or (pcm[i + 1].toInt() shl 8)).toShort()
                    if (sample.toInt() != 0) nonZeroSamples++
                }
            }
            assertTrue(nonZeroSamples > 40_000 * channels)
            decoder.close()
        }
    }

    @Test fun acceptsThreeByteOpusSilenceAndDoesNotMistakeEmptyInputForPacketLoss() {
        val decoder = SoftwareOpusDecoder(48_000, 1)
        val pcm = decoder.decode(byteArrayOf(0xf8.toByte(), 0xff.toByte(), 0xfe.toByte()))
        assertEquals(960 * 2, pcm.size)
        try {
            decoder.decode(byteArrayOf())
            fail("Empty input must be rejected")
        } catch (_: IllegalArgumentException) { }
        assertEquals(960 * 2, decoder.decode(byteArrayOf(0xf8.toByte(), 0xff.toByte(), 0xfe.toByte())).size)
        decoder.close()
    }

    @Test fun independentStreamsAndCloseDoNotShareDecoderState() {
        val first = SoftwareOpusDecoder(48_000, 1)
        val second = SoftwareOpusDecoder(48_000, 1)
        val packet = byteArrayOf(0xf8.toByte(), 0xff.toByte(), 0xfe.toByte())
        assertArrayEquals(first.decode(packet), second.decode(packet))
        first.close()
        first.close()
        try {
            first.decode(packet)
            fail("Closed instance must not re-create the decoder")
        } catch (_: IllegalStateException) { }
        assertEquals(1_920, second.decode(packet).size)
        second.close()
    }

    @Test fun malformedPacketDoesNotPreventLaterValidDecode() {
        val decoder = SoftwareOpusDecoder(48_000, 1)
        try {
            decoder.decode(byteArrayOf(3)) // Code 3 requires another frame-count byte.
            fail("Truncated packet must be rejected")
        } catch (_: Exception) { }
        assertEquals(1_920, decoder.decode(byteArrayOf(0xf8.toByte(), 0xff.toByte(), 0xfe.toByte())).size)
        decoder.close()
    }
}
