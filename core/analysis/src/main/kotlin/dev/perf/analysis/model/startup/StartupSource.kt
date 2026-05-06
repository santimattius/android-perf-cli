package dev.perf.analysis.model.startup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class StartupSource {
    @SerialName("logcat-displayed")
    LOGCAT_DISPLAYED,

    @SerialName("am-start-fallback")
    AM_START_FALLBACK,
}
