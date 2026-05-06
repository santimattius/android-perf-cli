package dev.perf.analysis.model

/**
 * One partial wake-lock row from `dumpsys batterystats` for the target UID.
 */
data class WakelockEntry(
    val name: String,
    val count: Int,
    val totalDurationMs: Long,
)
