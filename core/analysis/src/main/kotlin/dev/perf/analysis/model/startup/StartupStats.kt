package dev.perf.analysis.model.startup

import dev.perf.analysis.model.ColdStartMeasurement
import dev.perf.analysis.model.Severity
import kotlinx.serialization.Serializable

@Serializable
data class StartupStats(
    val samples: List<StartupSample>,
    val p50Ms: Int? = null,
    val p95Ms: Int? = null,
    val ttfdP50Ms: Int? = null,
    val severity: Severity,
    val message: String,
)

fun StartupStats.toLegacyColdStart(): ColdStartMeasurement {
    val p50 = p50Ms
    return ColdStartMeasurement(
        totalTimeMs = p50,
        waitTimeMs = samples.firstOrNull { it.amStartWaitTimeMs != null }?.amStartWaitTimeMs,
        launchState = samples.firstOrNull()?.amStartLaunchState,
        status = null,
        parsedOk = p50 != null,
        message =
            if (p50 != null) {
                "Cold start P50=${p50}ms (legacy view; use startup.cold for full data)"
            } else {
                "Cold start: no usable TTID samples (legacy view)"
            },
        severity = severity,
    )
}
