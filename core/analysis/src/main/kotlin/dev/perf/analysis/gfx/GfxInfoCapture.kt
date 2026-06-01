package dev.perf.analysis.gfx

import dev.perf.adb.AdbClient
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val JANKY_THRESHOLD_MS = 16.0
private const val FROZEN_THRESHOLD_MS = 700.0

/** Lower bound (exclusive) for the "slow session" bucket — frames strictly above this value. */
private const val SLOW_FRAME_LOWER_MS = 50.0

/** Upper bound (inclusive) for the "slow session" bucket — mirrors [FROZEN_THRESHOLD_MS] by design. */
private const val SLOW_FRAME_UPPER_MS = 700.0

/**
 * Captures and parses `dumpsys gfxinfo` for a given package.
 *
 * The `framestats` section exposes per-frame nanosecond timestamps. We compute:
 *  - Janky frames: render time > 16ms (one vsync at 60 fps)
 *  - Frozen frames: render time > 700ms (Android Vitals threshold)
 *  - Percentiles 90/95/99 of frame render times in ms
 */
class GfxInfoCapture(private val device: AdbClient) {

    suspend fun capture(packageName: String): RenderingIncident = withContext(Dispatchers.IO) {
        val raw = device.shell("dumpsys", "gfxinfo", packageName, "framestats")
        val frameTimes = parseFrameTimesMs(raw)

        if (frameTimes.isEmpty()) {
            return@withContext RenderingIncident(
                severity = Severity.INFO,
                message = "No frame data available for $packageName",
                jankyFrames = 0,
                frozenFrames = 0,
                totalFrames = 0,
                p90Ms = 0.0,
                p95Ms = 0.0,
                p99Ms = 0.0,
                slowFrames = 0,
            )
        }

        val sorted = frameTimes.sorted()
        val janky = sorted.count { it > JANKY_THRESHOLD_MS }
        val frozen = sorted.count { it > FROZEN_THRESHOLD_MS }
        val slow = sorted.count { it > SLOW_FRAME_LOWER_MS && it <= SLOW_FRAME_UPPER_MS }
        val total = sorted.size

        val p90 = sorted.percentile(90)
        val p95 = sorted.percentile(95)
        val p99 = sorted.percentile(99)

        val slowPercent = if (total > 0) slow.toDouble() / total else 0.0
        val slowSeverity = when {
            slowPercent > 0.05 -> Severity.CRITICAL
            slowPercent > 0.01 -> Severity.WARNING
            else -> Severity.INFO
        }
        val baseSeverity = when {
            frozen > 0 || (janky.toDouble() / total) > 0.05 -> Severity.CRITICAL
            (janky.toDouble() / total) > 0.01 -> Severity.WARNING
            else -> Severity.INFO
        }
        // Severity.CRITICAL has ordinal 0 — minOrNull picks the WORST (lowest ordinal)
        val severity = sequenceOf(baseSeverity, slowSeverity).minOrNull() ?: Severity.INFO

        RenderingIncident(
            severity = severity,
            message = buildSummary(janky, slow, frozen, total),
            jankyFrames = janky,
            frozenFrames = frozen,
            totalFrames = total,
            p90Ms = p90,
            p95Ms = p95,
            p99Ms = p99,
            slowFrames = slow,
        )
    }

    /**
     * Parses the PROFILEDATA section from framestats output.
     *
     * The column layout changed in newer Android API levels (FrameTimelineVsyncId was
     * inserted at position 1, shifting all subsequent columns). We parse the header row
     * to find IntendedVsync and FrameCompleted by name instead of hardcoding indices.
     *
     * Rows with Flags != 0 are skipped — they represent partial/invalid frames whose
     * timestamps are not comparable to regular frame timestamps.
     */
    private fun parseFrameTimesMs(raw: String): List<Double> {
        val lines = raw.lineSequence()
            .dropWhile { !it.startsWith("---PROFILEDATA---") }
            .drop(1) // skip the opening ---PROFILEDATA--- marker
            .takeWhile { it != "---PROFILEDATA---" && it.isNotBlank() }
            .toList()

        if (lines.isEmpty()) return emptyList()

        val headers = lines.first().split(",").map { it.trim() }
        val flagsIdx = headers.indexOf("Flags")
        val vsyncIdx = headers.indexOf("IntendedVsync")
        val completedIdx = headers.indexOf("FrameCompleted")

        if (vsyncIdx < 0 || completedIdx < 0) return emptyList()

        return lines.drop(1).mapNotNull { line ->
            val cols = line.split(",").map { it.trim() }
            val flags = if (flagsIdx >= 0) cols.getOrNull(flagsIdx)?.toLongOrNull() ?: 0L else 0L
            if (flags != 0L) return@mapNotNull null // skip partial/invalid frames
            val intendedVsync = cols.getOrNull(vsyncIdx)?.toLongOrNull() ?: return@mapNotNull null
            val frameCompleted = cols.getOrNull(completedIdx)?.toLongOrNull() ?: return@mapNotNull null
            if (intendedVsync == 0L || frameCompleted == 0L) return@mapNotNull null
            val durationMs = (frameCompleted - intendedVsync) / 1_000_000.0
            if (durationMs <= 0) return@mapNotNull null // guard against negative/garbage values
            durationMs
        }.toList()
    }

    private fun List<Double>.percentile(p: Int): Double {
        if (isEmpty()) return 0.0
        val idx = (size * p / 100.0).toInt().coerceIn(0, size - 1)
        return this[idx]
    }

    private fun buildSummary(janky: Int, slow: Int, frozen: Int, total: Int): String =
        "Frames: $total total | $janky janky (>${JANKY_THRESHOLD_MS.toInt()}ms) | " +
            "$slow slow (${SLOW_FRAME_LOWER_MS.toInt()}–${SLOW_FRAME_UPPER_MS.toInt()}ms) | " +
            "$frozen frozen (>${FROZEN_THRESHOLD_MS.toInt()}ms)"
}
