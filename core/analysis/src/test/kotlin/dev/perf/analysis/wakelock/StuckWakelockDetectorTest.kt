package dev.perf.analysis.wakelock

import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StuckWakelockReport
import dev.perf.analysis.model.WakelockEntry
import dev.perf.analysis.model.WakelockReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StuckWakelockDetectorTest {

    private fun report(vararg entries: WakelockEntry) =
        WakelockReport(entries = entries.toList(), totalDurationMs = entries.sumOf { it.totalDurationMs })

    private fun entry(name: String, count: Int, durationMs: Long) =
        WakelockEntry(name = name, count = count, totalDurationMs = durationMs)

    // ── T-4.1: model compilation check ────────────────────────────────────────

    @Test
    fun `StuckWakelockReport data class fields match spec`() {
        val entry = dev.perf.analysis.model.StuckWakelockEntry(
            name = "MY_LOCK",
            startCount = 1,
            endCount = 2,
            durationDeltaMs = 1000L,
        )
        val report = StuckWakelockReport(stuckEntries = listOf(entry))
        assertEquals("MY_LOCK", report.stuckEntries[0].name)
        assertEquals(1, report.stuckEntries[0].startCount)
        assertEquals(2, report.stuckEntries[0].endCount)
        assertEquals(1000L, report.stuckEntries[0].durationDeltaMs)
        assertEquals(Severity.INFO, report.severity)
    }

    // ── T-4.2: StuckWakelockDetector heuristic ────────────────────────────────

    @Test
    fun `detect finds stuck lock when count and duration both increase`() {
        val start = report(entry("MY_LOCK", count = 1, durationMs = 1000L))
        val end = report(entry("MY_LOCK", count = 2, durationMs = 2000L))
        val result = StuckWakelockDetector.detect(start, end)
        assertEquals(1, result.stuckEntries.size)
        val stuck = result.stuckEntries[0]
        assertEquals("MY_LOCK", stuck.name)
        assertEquals(1, stuck.startCount)
        assertEquals(2, stuck.endCount)
        assertEquals(1000L, stuck.durationDeltaMs)
    }

    @Test
    fun `detect returns empty when lock released between snapshots`() {
        val start = report(entry("MY_LOCK", count = 1, durationMs = 1000L))
        val end = report() // lock absent at end
        val result = StuckWakelockDetector.detect(start, end)
        assertTrue(result.stuckEntries.isEmpty(), "Lock released should not be stuck")
    }

    @Test
    fun `detect returns empty when both snapshots have no entries`() {
        val result = StuckWakelockDetector.detect(report(), report())
        assertTrue(result.stuckEntries.isEmpty())
    }

    @Test
    fun `detect returns empty when count is equal at both endpoints`() {
        // count NOT strictly increasing → not stuck
        val start = report(entry("MY_LOCK", count = 2, durationMs = 1000L))
        val end = report(entry("MY_LOCK", count = 2, durationMs = 2000L))
        val result = StuckWakelockDetector.detect(start, end)
        assertTrue(result.stuckEntries.isEmpty(), "Equal count should not be stuck")
    }

    @Test
    fun `detect returns empty when lock only exists at end`() {
        val start = report() // no locks at start
        val end = report(entry("NEW_LOCK", count = 1, durationMs = 500L))
        val result = StuckWakelockDetector.detect(start, end)
        assertTrue(result.stuckEntries.isEmpty(), "Lock only at end should not be stuck")
    }

    @Test
    fun `detect returns empty when start is null`() {
        val result = StuckWakelockDetector.detect(null, report(entry("LOCK", 1, 1000L)))
        assertTrue(result.stuckEntries.isEmpty())
    }

    @Test
    fun `detect returns empty when end is null`() {
        val result = StuckWakelockDetector.detect(report(entry("LOCK", 1, 1000L)), null)
        assertTrue(result.stuckEntries.isEmpty())
    }

    @Test
    fun `detect severity is always INFO`() {
        val result = StuckWakelockDetector.detect(report(), report())
        assertEquals(Severity.INFO, result.severity)
    }
}
