package com.shilapi.xcertplay.airplay

import org.junit.Assert.*
import org.junit.Test

class AudioModeDiagnosticsTest {
    @Test fun keepsOnlyBoundedKnownStateFields() {
        val summary = AudioModeDiagnostics.summarize(mapOf(
            "resources" to listOf(mapOf("resourceID" to 2, "entity" to 1, "name" to "private-phone", "token" to "secret")),
            "appStates" to listOf(mapOf("appID" to "speech", "speechMode" to 3, "entity" to 2)),
        ))
        assertTrue(summary.contains("resourceID=2,entity=1"))
        assertTrue(summary.contains("appID=speech,entity=2,speechMode=3"))
        assertFalse(summary.contains("private"))
        assertFalse(summary.contains("secret"))
    }

    @Test fun doesNotSerializeUnknownValuesOrNestedStructures() {
        val summary = AudioModeDiagnostics.summarize(mapOf(
            "resources" to List(100) { mapOf("entity" to "private", "owner" to mapOf("secret" to 1), "state" to Long.MAX_VALUE) },
            "appStates" to "private",
        ))
        assertFalse(summary.contains("private"))
        assertFalse(summary.contains("secret"))
        assertTrue(summary.contains("appStates=unavailable"))
        assertTrue(summary.length < 200)
    }
}
