package dev.perf.analysis.wakelock

import dev.perf.analysis.model.StuckWakelockEntry
import dev.perf.analysis.model.StuckWakelockReport
import dev.perf.analysis.model.WakelockReport

/**
 * Pure detector that diffs two [WakelockReport]s to find locks that never released.
 *
 * No ADB I/O — accepts pre-captured snapshots from [WakelockCapture].
 *
 * Stuck heuristic: a lock is stuck when ALL of the following hold:
 * 1. The same lock name appears in both [start] and [end] entries.
 * 2. `end.count > start.count` (strictly increasing — the lock was acquired again).
 * 3. `end.totalDurationMs > start.totalDurationMs` (duration grew — still held).
 *
 * Reference: https://developer.android.com/topic/performance/vitals/stuck-wakelock
 */
object StuckWakelockDetector {

    fun detect(start: WakelockReport?, end: WakelockReport?): StuckWakelockReport {
        if (start == null || end == null) return StuckWakelockReport(emptyList())
        val startMap = start.entries.associateBy { it.name }
        val stuck = end.entries.mapNotNull { e ->
            val s = startMap[e.name] ?: return@mapNotNull null
            if (e.count > s.count && e.totalDurationMs > s.totalDurationMs) {
                StuckWakelockEntry(
                    name = e.name,
                    startCount = s.count,
                    endCount = e.count,
                    durationDeltaMs = e.totalDurationMs - s.totalDurationMs,
                )
            } else null
        }
        return StuckWakelockReport(stuckEntries = stuck)
    }
}
