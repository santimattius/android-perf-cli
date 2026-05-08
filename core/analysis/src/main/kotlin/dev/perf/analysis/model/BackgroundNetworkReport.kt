package dev.perf.analysis.model

/**
 * Session-total network traffic for the package's UID, measured as deltas
 * between two [dev.perf.analysis.network.NetworkSnapshot]s.
 *
 * Severity is always INFO in v1 — no threshold gates. Raw deltas only.
 *
 * Reference: https://developer.android.com/topic/performance/vitals/bg-network-usage
 */
data class BackgroundNetworkReport(
    val rxBytesDelta: Long,
    val txBytesDelta: Long,
    val durationMs: Long,
    val severity: Severity = Severity.INFO,
)
