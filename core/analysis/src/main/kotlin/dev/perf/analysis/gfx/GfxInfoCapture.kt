package dev.perf.analysis.gfx

import dev.perf.adb.AdbClient
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val JANKY_THRESHOLD_MS = 16.0
private const val FROZEN_THRESHOLD_MS = 700.0

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
            )
        }

        val sorted = frameTimes.sorted()
        val janky = sorted.count { it > JANKY_THRESHOLD_MS }
        val frozen = sorted.count { it > FROZEN_THRESHOLD_MS }
        val total = sorted.size

        val p90 = sorted.percentile(90)
        val p95 = sorted.percentile(95)
        val p99 = sorted.percentile(99)

        val severity = when {
            frozen > 0 || (janky.toDouble() / total) > 0.05 -> Severity.CRITICAL
            (janky.toDouble() / total) > 0.01 -> Severity.WARNING
            else -> Severity.INFO
        }

        RenderingIncident(
            severity = severity,
            message = buildSummary(janky, frozen, total),
            jankyFrames = janky,
            frozenFrames = frozen,
            totalFrames = total,
            p90Ms = p90,
            p95Ms = p95,
            p99Ms = p99,
        )
    }

    /**
     * Parses the PROFILEDATA section from framestats output.
     *
     * Each row has 14 timestamp columns (nanoseconds). Render time is:
     *   column[13] (FrameCompleted) - column[1] (IntendedVsync)
     */
    private fun parseFrameTimesMs(raw: String): List<Double> {
        val inSection = raw.lineSequence()
            .dropWhile { !it.startsWith("---PROFILEDATA---") }
            .drop(2) // skip header and column names line
            .takeWhile { it != "---PROFILEDATA---" && it.isNotBlank() }

        return inSection.mapNotNull { line ->
            val cols = line.split(",").mapNotNull { it.trim().toLongOrNull() }
            if (cols.size < 14) return@mapNotNull null
            val intendedVsync = cols[1]
            val frameCompleted = cols[13]
            // Timestamps of 0 indicate missing/invalid frames
            if (intendedVsync == 0L || frameCompleted == 0L) return@mapNotNull null
            (frameCompleted - intendedVsync) / 1_000_000.0
        }.toList()
    }

    private fun List<Double>.percentile(p: Int): Double {
        if (isEmpty()) return 0.0
        val idx = (size * p / 100.0).toInt().coerceIn(0, size - 1)
        return this[idx]
    }

    private fun buildSummary(janky: Int, frozen: Int, total: Int): String =
        "Frames: $total total | $janky janky (>${JANKY_THRESHOLD_MS.toInt()}ms) | $frozen frozen (>${FROZEN_THRESHOLD_MS.toInt()}ms)"
}
