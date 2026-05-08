package dev.perf.analysis.model

/**
 * A wake lock that was observed at both the start and end of a monitoring session
 * with a strictly increasing count — indicating it was never released.
 *
 * Reference: https://developer.android.com/topic/performance/vitals/stuck-wakelock
 */
data class StuckWakelockEntry(
    val name: String,
    val startCount: Int,
    val endCount: Int,
    /** Difference in totalDurationMs between end and start snapshots. */
    val durationDeltaMs: Long,
)

/**
 * Report produced by [dev.perf.analysis.wakelock.StuckWakelockDetector].
 *
 * Severity is always INFO in v1 — no threshold gates.
 */
data class StuckWakelockReport(
    val stuckEntries: List<StuckWakelockEntry>,
    val severity: Severity = Severity.INFO,
)
