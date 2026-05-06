package dev.perf.analysis.startup

import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.startup.StartupSample
import dev.perf.analysis.model.startup.StartupSource
import dev.perf.analysis.model.startup.StartupStats
import dev.perf.analysis.model.startup.StartupType
import kotlin.math.ceil

object StartupAggregator {

    fun aggregate(
        type: StartupType,
        samples: List<StartupSample>,
        thresholds: ThresholdsView,
    ): StartupStats {
        val valid =
            samples.filter {
                it.ttidMs != null && !it.flags.contains("pre_condition_drift")
            }
        val ttidValues = valid.mapNotNull { it.ttidMs }.sorted()
        val ttfdValues = valid.mapNotNull { it.ttfdMs }.sorted()

        val p50 = nearestRankPercentile(ttidValues, 50)
        val p95 = nearestRankPercentile(ttidValues, 95)
        val ttfdP50 =
            if (valid.isNotEmpty() && ttfdValues.size * 2 >= valid.size) {
                nearestRankPercentile(ttfdValues, 50)
            } else {
                null
            }

        val hasTtfdSignal = ttfdP50 != null
        val severity = computeSeverity(p50, type, thresholds, hasTtfdSignal)
        val message = formatAggregateMessage(type, p50, p95, samples.size, valid.size)

        return StartupStats(
            samples = samples,
            p50Ms = p50,
            p95Ms = p95,
            ttfdP50Ms = ttfdP50,
            severity = severity,
            message = message,
        )
    }

    fun computeParsedOk(allSamples: List<StartupSample>): Boolean {
        if (allSamples.isEmpty()) return true
        val fallback =
            allSamples.count {
                it.source == StartupSource.AM_START_FALLBACK ||
                    it.flags.contains("no_displayed_line")
            }
        return fallback * 2 <= allSamples.size
    }

    fun computeReportMessage(parsedOk: Boolean, allSamples: List<StartupSample>): String =
        when {
            !parsedOk ->
                "More than 50% of startup samples used am-start-fallback (no Displayed line in time window)."
            allSamples.any { it.flags.contains("pre_condition_drift") } ->
                "Some samples were flagged pre_condition_drift and excluded from P50/P95."
            else -> ""
        }.trim()

    internal fun vitalsThresholdMs(type: StartupType): Int =
        when (type) {
            StartupType.COLD -> 5000
            StartupType.WARM -> 2000
            StartupType.HOT -> 1500
        }

    internal fun computeSeverity(
        p50: Int?,
        type: StartupType,
        thresholds: ThresholdsView,
        hasTtfdSignal: Boolean,
    ): Severity {
        if (p50 == null) return Severity.WARNING
        val vitals = vitalsThresholdMs(type)
        val warnLine = (vitals * 0.6).toInt()
        val userMax =
            when (type) {
                StartupType.COLD -> thresholds.maxColdStartMs
                StartupType.WARM -> thresholds.maxWarmStartMs
                StartupType.HOT -> thresholds.maxHotStartMs
            }
        return when {
            userMax != null && p50 > userMax && !hasTtfdSignal -> Severity.CRITICAL
            p50 >= vitals -> Severity.CRITICAL
            userMax != null && p50 > userMax -> Severity.CRITICAL
            p50 >= warnLine -> Severity.WARNING
            else -> Severity.INFO
        }
    }

    internal fun nearestRankPercentile(sorted: List<Int>, p: Int): Int? {
        if (sorted.isEmpty()) return null
        val n = sorted.size
        val rank = ceil(p / 100.0 * n).toInt().coerceIn(1, n)
        return sorted[rank - 1]
    }

    private fun formatAggregateMessage(
        type: StartupType,
        p50: Int?,
        p95: Int?,
        sampleCount: Int,
        validCount: Int,
    ): String {
        val head = "${type.name} startup — $validCount/$sampleCount samples used for aggregation"
        val tail =
            when {
                p50 == null && p95 == null -> " (no usable TTID)"
                else -> " — P50=${p50 ?: "—"}ms, P95=${p95 ?: "—"}ms"
            }
        return head + tail
    }
}
