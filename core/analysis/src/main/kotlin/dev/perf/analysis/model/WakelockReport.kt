package dev.perf.analysis.model

/**
 * Aggregated partial wake locks for a monitored package after a session.
 *
 * [severity] is finalized in the CLI layer ([dev.perf.cli.scenario.ThresholdEvaluator])
 * once scenario thresholds are applied; engines populate raw counts/durations only.
 */
data class WakelockReport(
    val entries: List<WakelockEntry>,
    val totalDurationMs: Long,
    val severity: Severity = Severity.INFO,
)
