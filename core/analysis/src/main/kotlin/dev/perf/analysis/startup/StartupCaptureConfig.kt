package dev.perf.analysis.startup

import dev.perf.analysis.model.startup.StartupType

data class ThresholdsView(
    val maxColdStartMs: Int? = null,
    val maxWarmStartMs: Int? = null,
    val maxHotStartMs: Int? = null,
)

data class StartupCaptureConfig(
    val startupTypes: Set<StartupType> = setOf(StartupType.COLD),
    val samplesPerType: Int = 3,
    val cooldownMs: Long = 1500L,
    val perSampleTimeoutMs: Long = 8000L,
    val streamWarmupMs: Long = 50L,
    val ttfdTailMs: Long = 3000L,
    val thresholds: ThresholdsView = ThresholdsView(),
) {
    val effectiveSamplesPerType: Int = samplesPerType.coerceIn(1, 10)
}
