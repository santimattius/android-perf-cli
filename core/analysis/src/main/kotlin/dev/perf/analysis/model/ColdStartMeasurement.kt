package dev.perf.analysis.model

/**
 * Result of a single **cold** launch measurement via `am start -W` after
 * `am force-stop` (see [dev.perf.analysis.startup.StartupCapture]).
 */
data class ColdStartMeasurement(
    /** Milliseconds from `TotalTime` / `ThisTime` in `am start -W` output. */
    val totalTimeMs: Int?,
    val waitTimeMs: Int?,
    val launchState: String?,
    val status: String?,
    /** Whether stdout was parsed successfully for timing fields. */
    val parsedOk: Boolean,
    val message: String,
    val severity: Severity,
)
