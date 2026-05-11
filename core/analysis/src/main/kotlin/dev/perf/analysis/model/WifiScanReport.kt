package dev.perf.analysis.model

/**
 * Wi-Fi scan count delta for a package during a monitoring session.
 *
 * Severity is always INFO in v1 — no threshold gates.
 *
 * Reference: https://developer.android.com/topic/performance/vitals/bg-wifi
 */
data class WifiScanReport(
    val scanCountDelta: Int,
    val durationMs: Long,
    val severity: Severity = Severity.INFO,
)
