package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class DiagnosticLogPolicyTest {
    @Test fun releaseDropsOnlyPacketChatter() {
        for (line in listOf("iAP tunnel iap2 rx=0x5001", "iAP tunnel iap2 tx=0x5002",
            "iap2 rx=0xae01", "iap2 tx=0x4301 carplay-start-session",
            "AirPlay iAP tunnel received data", "AirPlay iAP tunnel package type=comm body=16")) {
            assertFalse(line, DiagnosticLogPolicy.shouldRecord(line, true))
            assertTrue(line, DiagnosticLogPolicy.shouldRecord(line, false))
        }
    }

    @Test fun releaseKeepsPlaybackErrorsAndPerformanceEvidence() {
        for (line in listOf("AUDIO_EVENT mediaCommand pause", "Audio: decoder selected codec=OPUS",
            "audio stats rx=234", "Video: video stats rx=19fps",
            "Video: recovery: video backlog exceeded 250 ms", "iAP tunnel ended reason=peer EOF",
            "CONNECTION_DIAGNOSTIC attempt=1 failure", "Wi-Fi P2P create rejected code=0")) {
            assertTrue(line, DiagnosticLogPolicy.shouldRecord(line, true))
        }
    }
}
