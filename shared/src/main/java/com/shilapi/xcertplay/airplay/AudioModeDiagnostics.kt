package com.shilapi.xcertplay.airplay

/** Fixed field allowlist and bounded enum values only; no generic protocol serialization. */
internal object AudioModeDiagnostics {
    private val fields = listOf("resourceID", "appID", "entity", "entityID", "owner", "state", "speechMode")
    private val enums = setOf("speech", "phoneCall", "turnByTurn", "mainAudio", "screen")

    fun summarize(params: Map<String, Any?>): String = "Audio: modesChanged " +
        listOf("resources", "appStates").joinToString(" ") { group ->
            val entries = params[group] as? List<*>
            if (entries == null) "$group=unavailable" else "$group=" + entries.take(6).joinToString(",", "[", "]") { entry ->
                val map = entry as? Map<*, *>
                if (map == null) "unknown" else fields.mapNotNull { key ->
                    val value = map[key]
                    val safe = when {
                        value is Byte || value is Short || value is Int || value is Long ->
                            (value as Number).toLong().takeIf { it in -1L..1024L }?.toString()
                        value is Boolean -> value.toString()
                        value is String && value in enums -> value
                        else -> null
                    }
                    safe?.let { "$key=$it" }
                }.joinToString(",", "{", "}")
            }
        }
}
