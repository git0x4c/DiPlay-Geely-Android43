package com.shilapi.xcertplay

import org.junit.Assert.*
import org.junit.Test

class Gl19AudioExperimentTest {
    @Test fun baselineAndModernApisKeepExistingPolicy() {
        for (api in listOf(18, 19, 20, 21, 37)) {
            assertFalse(Gl19AudioPolicy.BASELINE.skipLegacyKeyFocus(api))
            assertFalse(Gl19AudioPolicy.BASELINE.skipLegacyRcc(api))
        }
        for (policy in Gl19AudioPolicy.values()) {
            assertFalse(policy.skipLegacyKeyFocus(21))
            assertFalse(policy.skipLegacyRcc(21))
        }
    }

    @Test fun onlyBRemovesRccAndBothSkipLegacyKeyFocus() {
        for (api in listOf(18, 19, 20)) {
            assertTrue(Gl19AudioPolicy.A.skipLegacyKeyFocus(api))
            assertFalse(Gl19AudioPolicy.A.skipLegacyRcc(api))
            assertTrue(Gl19AudioPolicy.B.skipLegacyKeyFocus(api))
            assertTrue(Gl19AudioPolicy.B.skipLegacyRcc(api))
            assertTrue(Gl19AudioPolicy.A_OPUS.skipLegacyKeyFocus(api))
            assertFalse(Gl19AudioPolicy.A_OPUS.skipLegacyRcc(api))
        }
    }

    @Test fun buildSelectionAndReportAreTraceable() {
        assertEquals(Gl19AudioPolicy.A, Gl19AudioPolicy.fromBuild("a"))
        assertEquals(Gl19AudioPolicy.B, Gl19AudioPolicy.fromBuild("b"))
        assertEquals(Gl19AudioPolicy.A_OPUS, Gl19AudioPolicy.fromBuild("aopus"))
        val summary = Gl19AudioExperiment.summary(19)
        assertTrue(summary.contains("GL19_AUDIO_TEST=${Gl19AudioExperiment.marker}"))
        assertTrue(summary.contains("rendererFocus=unchanged"))
        assertEquals(summary, DiagnosticRedactor.redact(summary))
        val name = Gl19AudioExperiment.reportFileName("20261106-235959-123")
        assertTrue(name.endsWith("诊断报告-20261106-235959-123.txt"))
        assertFalse(name.contains('/'))
        if (Gl19AudioExperiment.policy != Gl19AudioPolicy.BASELINE) assertTrue(name.contains(Gl19AudioExperiment.marker))
    }

    @Test(expected = IllegalStateException::class) fun invalidBuildIsRejected() {
        Gl19AudioPolicy.fromBuild("typo")
    }

    @Test fun optimizedReleaseHasDistinctMarkerWithoutChangingAudioPolicy() {
        if (!com.shilapi.xcertplay.host.BuildConfig.GL19_RELEASE) return
        assertEquals(Gl19AudioPolicy.A_OPUS, Gl19AudioExperiment.policy)
        assertEquals("GK19PLAY-RELEASE-A-OPUS-SW", Gl19AudioExperiment.marker)
        assertTrue(Gl19AudioExperiment.summary(19).contains("buildProfile=release-opt"))
        assertTrue(Gl19AudioExperiment.reportFileName("test").startsWith("GK19Play-GK19PLAY-RELEASE-A-OPUS-SW-"))
        assertTrue(Gl19AudioExperiment.policy.skipLegacyKeyFocus(19))
        assertFalse(Gl19AudioExperiment.policy.skipLegacyRcc(19))
    }
}
