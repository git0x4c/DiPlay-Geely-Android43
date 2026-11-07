package com.shilapi.xcertplay.media

import io.github.jaredmdobson.concentus.OpusDecoder
import java.io.Closeable

/** P21 software decode approach, with one decoder per renderer and no shared owner map. */
internal class SoftwareOpusDecoder(val sampleRate: Int, val channels: Int) : Closeable {
    private var decoder: OpusDecoder? = OpusDecoder(sampleRate, channels)
    private val maxFrames = sampleRate * 120 / 1_000
    private val samples = ShortArray(maxFrames * channels)

    /** Raw Opus access unit -> signed PCM16 little endian. Empty input is not implicit PLC. */
    fun decode(packet: ByteArray): ByteArray {
        val current = checkNotNull(decoder) { "Software Opus decoder is closed" }
        require(packet.isNotEmpty()) { "Empty Opus packet" }
        // Valid Opus/DTX packets can be shorter than the old four-byte filter.
        val frames = current.decode(packet, 0, packet.size, samples, 0, maxFrames, false)
        check(frames in 0..maxFrames) { "Invalid decoded frame count" }
        return ByteArray(frames * channels * 2).also { bytes ->
            for (index in 0 until frames * channels) {
                val sample = samples[index].toInt()
                bytes[index * 2] = sample.toByte()
                bytes[index * 2 + 1] = (sample shr 8).toByte()
            }
        }
    }

    override fun close() {
        decoder = null
        samples.fill(0)
    }

    companion object {
        fun usesSoftware(api: Int) = api < 21
    }
}
