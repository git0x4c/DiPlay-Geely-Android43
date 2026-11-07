package com.shilapi.xcertplay

import com.shilapi.xcertplay.host.BuildConfig

/** Build-time only: no settings migration, focus forcing or automatic playback recovery. */
internal enum class Gl19AudioPolicy(val marker: String, val label: String) {
    BASELINE("BASELINE", "原始诊断版"),
    A("GL19-A-NO-KEY-FOCUS", "A档：媒体按键不申请焦点，保留RCC"),
    A_OPUS("GL19-A-OPUS-SW-TOOLS", "A导航修复：保留RCC，软件Opus解码及磁盘浏览"),
    B("GL19-B-NO-KEY-FOCUS-NO-RCC", "B档：媒体按键不申请焦点，关闭RCC");

    fun skipLegacyKeyFocus(api: Int) = api < 21 && this != BASELINE
    fun skipLegacyRcc(api: Int) = api < 21 && this == B

    companion object {
        fun fromBuild(value: String): Gl19AudioPolicy = when (value) {
            "baseline" -> BASELINE
            "a" -> A
            "aopus" -> A_OPUS
            "b" -> B
            else -> error("Unknown GL19 audio experiment")
        }
    }
}

internal object Gl19AudioExperiment {
    val policy = Gl19AudioPolicy.fromBuild(BuildConfig.GL19_AUDIO_TEST)
    val marker get() = policy.marker
    fun reportFileName(timestamp: String): String =
        "DiPlay-" +
            (if (policy == Gl19AudioPolicy.BASELINE) "" else "$marker-") + "诊断报告-$timestamp.txt"
    fun summary(api: Int): String =
        "GL19_AUDIO_TEST=$marker; ${policy.label}; api=$api; " +
            "skipLegacyKeyFocus=${policy.skipLegacyKeyFocus(api)}; skipLegacyRcc=${policy.skipLegacyRcc(api)}; " +
            "rendererFocus=unchanged; autoResume=false"
}
