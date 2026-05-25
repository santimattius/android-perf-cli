package dev.perf.cli.scenario

import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.PermissionDenialReport
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.ThresholdBreach
import dev.perf.analysis.model.WakelockReport
import dev.perf.trace.model.TraceAnalysisSummary
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

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

    /**
     * Computes threshold breaches and annotates [PerfReport.wakelockReport.severity]
     * when the wake-lock engine produced a report.
     */
    fun applyToReport(report: PerfReport, thresholds: ThresholdConfig): PerfReport {
        val breaches = evaluate(report, thresholds)
        val wl =
            report.wakelockReport?.let { base ->
                base.copy(severity = wakelockSeverity(base, breaches))
            }
        return report.copy(
            thresholdBreaches = breaches,
            wakelockReport = wl ?: report.wakelockReport,
        )
    }

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
                                .format(jankyPercent, thresholds.maxJankyPercent),
                        ),
                    )
                }
            }

            if (rendering.frozenFrames > thresholds.maxFrozenFrames) {
                add(
                    ThresholdBreach(
                        "Frozen frames ${rendering.frozenFrames} exceeds threshold of ${thresholds.maxFrozenFrames}",
                    ),
                )
            }

            if (report.strictModeViolations.size > thresholds.maxStrictModeViolations) {
                add(
                    ThresholdBreach(
                        "StrictMode violations ${report.strictModeViolations.size} " +
                            "exceeds threshold of ${thresholds.maxStrictModeViolations}",
                    ),
                )
            }

            val anrRiskCount = report.potentialAnrHints.count { it.severity != Severity.INFO }
            if (anrRiskCount > thresholds.maxPotentialAnrHints) {
                add(
                    ThresholdBreach(
                        "Potential ANR-risk log signals ($anrRiskCount) exceed threshold of ${thresholds.maxPotentialAnrHints}",
                    ),
                )
            }

            val maxCold = thresholds.maxColdStartMs
            val coldP50 = report.startup?.cold?.p50Ms ?: report.coldStart?.totalTimeMs
            if (maxCold != null && coldP50 != null && coldP50 > maxCold) {
                add(
                    ThresholdBreach(
                        "Cold start P50 ${coldP50}ms exceeds threshold of ${maxCold}ms",
                    ),
                )
            }

            val maxWarm = thresholds.maxWarmStartMs
            val warmP50 = report.startup?.warm?.p50Ms
            if (maxWarm != null && warmP50 != null && warmP50 > maxWarm) {
                add(
                    ThresholdBreach(
                        "Warm start P50 ${warmP50}ms exceeds threshold of ${maxWarm}ms",
                    ),
                )
            }

            val maxHot = thresholds.maxHotStartMs
            val hotP50 = report.startup?.hot?.p50Ms
            if (maxHot != null && hotP50 != null && hotP50 > maxHot) {
                add(
                    ThresholdBreach(
                        "Hot start P50 ${hotP50}ms exceeds threshold of ${maxHot}ms",
                    ),
                )
            }

            report.permissionDenials?.let { pd ->
                evaluatePermissionDenials(pd, thresholds, this)
            }

            evaluateTraceAnalysis(report.perfettoAnalyze, thresholds, this)

            val wl = report.wakelockReport ?: return@buildList
            val maxWakelockMs = thresholds.maxWakelockTimeMs
            if (maxWakelockMs != null && wl.totalDurationMs > maxWakelockMs) {
                add(
                    ThresholdBreach(
                        "Wake lock total duration ${wl.totalDurationMs}ms exceeds threshold of ${maxWakelockMs}ms",
                    ),
                )
            }
            val maxCount = thresholds.maxWakelockCount
            if (maxCount != null && wl.entries.size > maxCount) {
                add(
                    ThresholdBreach(
                        "Wake lock entry count ${wl.entries.size} exceeds threshold of $maxCount",
                    ),
                )
            }
            evaluateWakelockSessionPercent(wl, thresholds, this)
        }

    /**
     * Single-run approximation of the Play Console wake-lock session-percentage rule.
     *
     * Play Console definition: >5% of daily sessions contain a partial wake lock held for >1 hour
     * is a "bad behaviour" threshold. A single CLI run cannot compute a fleet percentage.
     *
     * Approximation: if any partial wake lock entry has `totalDurationMs > maxWakelockLongHeldMs`
     * (default: 1 hour = 3,600,000ms) in this run, we treat this run as 100% of sessions with a
     * long-held wake lock. Because 100% > the 5% threshold, a WARNING breach is emitted.
     *
     * Severity is intentionally WARNING (not CRITICAL) because this is an approximation: a single
     * run with a long wake lock does not confirm fleet-wide session-percentage behaviour.
     *
     * Note: `totalDurationMs` per entry is the aggregate duration for one wake lock name across all
     * holds in the session; we use ">1h aggregate per name" as a proxy for ">1h single hold".
     *
     * @param wl The wake lock report from the current run.
     * @param thresholds The threshold config containing [ThresholdConfig.maxWakelockSessionPercent]
     *   and [ThresholdConfig.maxWakelockLongHeldMs].
     * @param sink The mutable list to add breaches to.
     */
    private fun evaluateWakelockSessionPercent(
        wl: WakelockReport,
        thresholds: ThresholdConfig,
        sink: MutableList<ThresholdBreach>,
    ) {
        // Guard: only evaluate when the session-percent threshold is configured
        val maxSessionPercent = thresholds.maxWakelockSessionPercent ?: return
        val maxLongHeld = thresholds.maxWakelockLongHeldMs ?: VitalsDefaults.WAKELOCK_LONG_HELD_MS

        val longHeldEntry = wl.entries.find { it.totalDurationMs > maxLongHeld }
        if (longHeldEntry != null) {
            sink.add(
                ThresholdBreach(
                    message = "Wake lock '${longHeldEntry.name}' held >${maxLongHeld}ms in this run " +
                        "(single-run approximation of Play Console >${maxSessionPercent}% sessions / >1h rule). " +
                        "Aggregate duration: ${longHeldEntry.totalDurationMs}ms. " +
                        "Note: fleet session-% cannot be computed from a single run.",
                    severity = Severity.WARNING,
                ),
            )
        }
    }

    private fun evaluatePermissionDenials(
        pd: PermissionDenialReport,
        thresholds: ThresholdConfig,
        sink: MutableList<ThresholdBreach>,
    ) {
        val max = thresholds.maxPermissionDenials ?: return
        if (pd.denials.size > max) {
            sink.add(
                ThresholdBreach(
                    "Permission denials ${pd.denials.size} exceeds threshold of $max",
                ),
            )
        }
    }

    private fun evaluateTraceAnalysis(
        summary: TraceAnalysisSummary?,
        thresholds: ThresholdConfig,
        sink: MutableList<ThresholdBreach>,
    ) {
        if (summary == null) return
        val limit = thresholds.maxMainThreadBlockedMs ?: return
        val rendering = summary.domains["rendering"] ?: return
        val blocked =
            rendering.metrics["main_thread_blocked_ms"]?.let { el ->
                (el as? JsonPrimitive)?.longOrNull
            } ?: return
        if (blocked > limit) {
            sink.add(
                ThresholdBreach(
                    "Trace main thread blocked time ${blocked}ms exceeds threshold of ${limit}ms",
                ),
            )
        }
    }

    private fun wakelockSeverity(base: WakelockReport, breaches: List<ThresholdBreach>): Severity {
        if (base.entries.isEmpty()) {
            return Severity.INFO
        }
        val wakelockBreached =
            breaches.any {
                it.message.startsWith("Wake lock total duration") ||
                    it.message.startsWith("Wake lock entry count")
            }
        return if (wakelockBreached) Severity.CRITICAL else Severity.WARNING
    }
}
