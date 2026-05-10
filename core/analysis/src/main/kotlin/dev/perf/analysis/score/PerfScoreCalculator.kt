package dev.perf.analysis.score

import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.ExitType
import dev.perf.analysis.model.SessionRateSummary
import dev.perf.analysis.model.Severity
import kotlin.math.min
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Weights (sum to 100 when all active; reweighted by denominator otherwise)
// ---------------------------------------------------------------------------
private const val WEIGHT_EXIT_SIGNALS = 30
private const val WEIGHT_SESSION_RATES = 20
private const val WEIGHT_RENDERING = 20
private const val WEIGHT_STARTUP = 15
private const val WEIGHT_WAKELOCKS = 10
private const val WEIGHT_STRICT_ANR = 5

// ---------------------------------------------------------------------------
// Tier thresholds (Play Console aligned)
// ---------------------------------------------------------------------------
private const val TIER_GOOD = 80
private const val TIER_NEEDS_IMPROVEMENT = 50

// ---------------------------------------------------------------------------
// Hard floors / caps
// ---------------------------------------------------------------------------
private const val FROZEN_FRAME_CAP = 60

// ---------------------------------------------------------------------------
// Calibration anchors: List<Pair<rawValue, subScore>>, ascending by rawValue
// ---------------------------------------------------------------------------
private val SESSION_RATE_ANCHORS = listOf(0.0 to 100, 0.0047 to 70, 0.0109 to 40, 0.05 to 0)
private val STARTUP_ANCHORS = listOf(1500.0 to 100, 5000.0 to 60, 8000.0 to 20, 10000.0 to 0)
private val WAKELOCK_ANCHORS = listOf(0.0 to 100, 3_600_000.0 to 30, 7_200_000.0 to 0)
private val JANKY_ANCHORS = listOf(5.0 to 100, 10.0 to 70, 20.0 to 40, 30.0 to 0)
private val STRICT_MODE_ANCHORS = listOf(0.0 to 100, 5.0 to 0)
private val ANR_HINTS_ANCHORS = listOf(0.0 to 100, 5.0 to 0)

// ---------------------------------------------------------------------------
// Linear interpolation helper — clamps output to [0, 100]
// ---------------------------------------------------------------------------
private fun linearInterp(value: Double, anchors: List<Pair<Double, Int>>): Int {
    if (value <= anchors.first().first) return anchors.first().second
    if (value >= anchors.last().first) return anchors.last().second
    val (lo, hi) = anchors.zipWithNext().first { (a, b) -> value >= a.first && value <= b.first }
    val ratio = (value - lo.first) / (hi.first - lo.first)
    return (lo.second + ratio * (hi.second - lo.second)).roundToInt().coerceIn(0, 100)
}

// ---------------------------------------------------------------------------
// Calculator
// ---------------------------------------------------------------------------
object PerfScoreCalculator {

    fun compute(report: PerfReport, sessionRates: SessionRateSummary? = null): PerfScore {
        val signals = listOf(
            exitSignalsScore(report),
            sessionRatesScore(report, sessionRates),
            renderingScore(report),
            startupScore(report),
            wakelocksScore(report),
            strictAnrScore(report),
        )

        val activeSignals = signals.filter { it.active }
        val activeWeight = activeSignals.sumOf { it.weight }

        if (activeWeight == 0) {
            return PerfScore(
                score = 100,
                tier = PerfScoreTier.GOOD,
                breakdown = signals.sortedByDescending { it.weight },
                activeSignalCount = 0,
            )
        }

        val score = (activeSignals.sumOf { it.subScore * it.weight }.toDouble() / activeWeight)
            .roundToInt()
            .coerceIn(0, 100)

        val tier = when {
            score >= TIER_GOOD -> PerfScoreTier.GOOD
            score >= TIER_NEEDS_IMPROVEMENT -> PerfScoreTier.NEEDS_IMPROVEMENT
            else -> PerfScoreTier.BAD
        }

        return PerfScore(
            score = score,
            tier = tier,
            breakdown = signals.sortedByDescending { it.weight },
            activeSignalCount = activeSignals.size,
        )
    }

    // -----------------------------------------------------------------------
    // Exit signals sub-score (weight = 30)
    // -----------------------------------------------------------------------
    private fun exitSignalsScore(report: PerfReport): SignalScore {
        val active = report.exitSignalReport != null
        val subScore = if (active) {
            val events = report.exitSignalReport!!.events
            when {
                events.any { it.exitType == ExitType.CRASH } -> 0
                events.any { it.exitType == ExitType.ANR } -> 20
                events.any { it.exitType == ExitType.LMK } -> 60
                else -> 100
            }
        } else {
            0
        }
        return SignalScore(signal = "exitSignals", subScore = subScore, weight = WEIGHT_EXIT_SIGNALS, active = active)
    }

    // -----------------------------------------------------------------------
    // Session rates sub-score (weight = 20)
    // -----------------------------------------------------------------------
    private fun sessionRatesScore(report: PerfReport, sessionRates: SessionRateSummary?): SignalScore {
        val active = sessionRates != null && sessionRates.totalRuns >= 2
        val subScore = if (active) {
            val ratio = maxOf(sessionRates!!.anrRate, sessionRates.crashRate)
            linearInterp(ratio, SESSION_RATE_ANCHORS)
        } else {
            0
        }
        return SignalScore(signal = "sessionRates", subScore = subScore, weight = WEIGHT_SESSION_RATES, active = active)
    }

    // -----------------------------------------------------------------------
    // Rendering sub-score (weight = 20, ALWAYS active)
    // -----------------------------------------------------------------------
    private fun renderingScore(report: PerfReport): SignalScore {
        val incident = report.renderingIncident
        val jankyPct = if (incident.totalFrames == 0) {
            0.0
        } else {
            incident.jankyFrames.toDouble() / incident.totalFrames * 100.0
        }
        val jankySub = linearInterp(jankyPct, JANKY_ANCHORS)
        val frozenCap = if (incident.frozenFrames >= 1) FROZEN_FRAME_CAP else 100
        val subScore = min(jankySub, frozenCap)
        return SignalScore(signal = "rendering", subScore = subScore, weight = WEIGHT_RENDERING, active = true)
    }

    // -----------------------------------------------------------------------
    // Startup sub-score (weight = 15)
    // -----------------------------------------------------------------------
    @Suppress("DEPRECATION")
    private fun startupScore(report: PerfReport): SignalScore {
        val coldP50Ms: Double? = report.startup?.cold?.p50Ms?.toDouble()
            ?: report.coldStart?.totalTimeMs?.toDouble()

        val active = coldP50Ms != null
        val subScore = if (active) {
            linearInterp(coldP50Ms!!, STARTUP_ANCHORS)
        } else {
            0
        }
        return SignalScore(signal = "startup", subScore = subScore, weight = WEIGHT_STARTUP, active = active)
    }

    // -----------------------------------------------------------------------
    // Wake locks sub-score (weight = 10)
    // -----------------------------------------------------------------------
    private fun wakelocksScore(report: PerfReport): SignalScore {
        val active = report.wakelockReport != null
        val subScore = if (active) {
            linearInterp(report.wakelockReport!!.totalDurationMs.toDouble(), WAKELOCK_ANCHORS)
        } else {
            0
        }
        return SignalScore(signal = "wakelocks", subScore = subScore, weight = WEIGHT_WAKELOCKS, active = active)
    }

    // -----------------------------------------------------------------------
    // StrictMode + ANR hints sub-score (weight = 5, ALWAYS active)
    // -----------------------------------------------------------------------
    private fun strictAnrScore(report: PerfReport): SignalScore {
        val strictCount = report.strictModeViolations.size
        val hintsRisk = report.potentialAnrHints.count { it.severity != Severity.INFO }
        val strictSub = linearInterp(strictCount.toDouble(), STRICT_MODE_ANCHORS)
        val hintsSub = linearInterp(hintsRisk.toDouble(), ANR_HINTS_ANCHORS)
        val subScore = min(strictSub, hintsSub)
        return SignalScore(signal = "strictMode+anrHints", subScore = subScore, weight = WEIGHT_STRICT_ANR, active = true)
    }
}
