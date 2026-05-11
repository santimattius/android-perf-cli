package dev.perf.analysis.model

import kotlinx.serialization.Serializable

/**
 * Container for permission denials captured during a session.
 *
 * [severity] is computed: INFO when empty (no denials), WARNING when at least one denial is
 * present. CRITICAL is never used — a denial degrades UX but does not crash the app.
 *
 * Does NOT propagate to [dev.perf.analysis.PerfReport.hasIssues] or affect exit codes by default.
 * Use [dev.perf.cli.scenario.ThresholdConfig.maxPermissionDenials] for optional CRITICAL escalation.
 */
@Serializable
data class PermissionDenialReport(
    val denials: List<PermissionDenial>,
    val severity: Severity = if (denials.isEmpty()) Severity.INFO else Severity.WARNING,
) {
    val hasDenials: Boolean get() = denials.isNotEmpty()
}
