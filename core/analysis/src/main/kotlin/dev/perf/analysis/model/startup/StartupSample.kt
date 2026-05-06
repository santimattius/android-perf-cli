package dev.perf.analysis.model.startup

import kotlinx.serialization.Serializable

@Serializable
data class StartupSample(
    val type: StartupType,
    val ttidMs: Int? = null,
    val ttfdMs: Int? = null,
    val amStartTotalTimeMs: Int? = null,
    val amStartWaitTimeMs: Int? = null,
    val amStartLaunchState: String? = null,
    val source: StartupSource,
    val flags: List<String> = emptyList(),
)
