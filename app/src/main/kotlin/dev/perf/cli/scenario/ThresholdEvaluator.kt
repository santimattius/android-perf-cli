package dev.perf.cli.scenario

import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.ThresholdBreach

/**
 * Applies [ThresholdConfig] against a [PerfReport] and returns a list of
 * threshold breaches as human-readable messages.
 *
 * Design decision: the evaluator is a pure function (no side effects) that
 * returns messages rather than mutating the report. The CLI command decides
 * what to do with breaches (print them, fail with exit code 1, etc.).
 * Keeping the evaluator pure makes it trivially testable without any fakes.
 *
 * Design decision: thresholds are evaluated AFTER the engines run — they are
 * an additional policy layer, not a replacement for engine severity logic.
 * A breach always results in a CRITICAL classification regardless of what
 * the engine reported.
 */
object ThresholdEvaluator {

    fun evaluate(report: PerfReport, thresholds: ThresholdConfig): List<ThresholdBreach> =
        buildList {
            val rendering = report.renderingIncident
            val totalFrames = rendering.totalFrames

            if (totalFrames > 0) {
                val jankyPercent = rendering.jankyFrames.toDouble() / totalFrames * 100
                if (jankyPercent > thresholds.maxJankyPercent) {
                    add(
                        ThresholdBreach(
                            "Janky frame rate %.1f%% exceeds threshold of %.1f%%"
                                .format(jankyPercent, thresholds.maxJankyPercent)
                        )
                    )
                }
            }

            if (rendering.frozenFrames > thresholds.maxFrozenFrames) {
                add(
                    ThresholdBreach(
                        "Frozen frames ${rendering.frozenFrames} exceeds threshold of ${thresholds.maxFrozenFrames}"
                    )
                )
            }

            if (report.strictModeViolations.size > thresholds.maxStrictModeViolations) {
                add(
                    ThresholdBreach(
                        "StrictMode violations ${report.strictModeViolations.size} " +
                            "exceeds threshold of ${thresholds.maxStrictModeViolations}"
                    )
                )
            }

            val anrRiskCount = report.potentialAnrHints.count { it.severity != Severity.INFO }
            if (anrRiskCount > thresholds.maxPotentialAnrHints) {
                add(
                    ThresholdBreach(
                        "Potential ANR-risk log signals ($anrRiskCount) exceed threshold of ${thresholds.maxPotentialAnrHints}"
                    )
                )
            }

            val maxCold = thresholds.maxColdStartMs
            val totalMs = report.coldStart?.totalTimeMs
            if (maxCold != null && totalMs != null && totalMs > maxCold) {
                add(
                    ThresholdBreach(
                        "Cold start TotalTime ${totalMs}ms exceeds threshold of ${maxCold}ms",
                    )
                )
            }
        }
}
