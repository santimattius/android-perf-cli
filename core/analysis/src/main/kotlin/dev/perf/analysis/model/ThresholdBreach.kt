package dev.perf.analysis.model

/**
 * A scenario threshold that was exceeded during the run.
 *
 * Design decision: lives in `core:analysis` (not in `app`) so [PerfReport]
 * can carry breaches without depending on the scenario module. The evaluator
 * in the CLI module produces these, reporters render them — both sides stay
 * decoupled.
 */
data class ThresholdBreach(
    val message: String,
    val severity: Severity = Severity.CRITICAL,
)
