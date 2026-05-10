package dev.perf.cli.history

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T-4.1 RED: Tests for RateCalculator covering all 9 spec scenarios.
 *
 * Pure function — no I/O, no mocks needed.
 */
class RateCalculatorTest {

    private fun entry(
        anrCount: Int = 0,
        crashCount: Int = 0,
        slowFrames: Int = 0,
        totalFrames: Int = 100,
        exitSignalsEnabled: Boolean = true,
        index: Int = 0,
    ) = SessionHistoryEntry(
        packageName = "com.example.app",
        runId = "2026-05-10T12:0${index}:00Z-$index",
        timestampIso = "2026-05-10T12:0${index}:00Z",
        anrCount = anrCount,
        crashCount = crashCount,
        totalFrames = totalFrames,
        slowFrames = slowFrames,
        frozenFrames = 0,
        exitSignalsEnabled = exitSignalsEnabled,
    )

    // Scenario 1: Empty list → zero summary
    @Test
    fun `empty list returns zero summary`() {
        val result = RateCalculator.compute(emptyList())

        assertEquals(0, result.totalRuns)
        assertEquals(0.0, result.anrRate)
        assertEquals(0.0, result.crashRate)
        assertEquals(0.0, result.slowSessionRate)
        assertFalse(result.hasExitSignalsCaveat)
    }

    // Scenario 2: Single entry, anrCount=0 → anrRate=0.0, totalRuns=1
    @Test
    fun `single entry with anrCount zero has zero anrRate and totalRuns one`() {
        val result = RateCalculator.compute(listOf(entry(anrCount = 0)))

        assertEquals(1, result.totalRuns)
        assertEquals(0.0, result.anrRate)
    }

    // Scenario 3: All entries have anrCount >= 1 → anrRate = 1.0
    @Test
    fun `all entries with anrCount one or more produce anrRate of one`() {
        val entries = List(5) { idx -> entry(anrCount = 1, index = idx) }
        val result = RateCalculator.compute(entries)

        assertEquals(1.0, result.anrRate)
    }

    // Scenario 4: Mixed — 2 of 6 with anrCount >= 1 → anrRate ≈ 0.333
    @Test
    fun `two of six entries with anrCount produces anrRate of one-third`() {
        val entries = listOf(
            entry(anrCount = 1, index = 0),
            entry(anrCount = 1, index = 1),
            entry(anrCount = 0, index = 2),
            entry(anrCount = 0, index = 3),
            entry(anrCount = 0, index = 4),
            entry(anrCount = 0, index = 5),
        )
        val result = RateCalculator.compute(entries)

        assertEquals(2.0 / 6.0, result.anrRate, absoluteTolerance = 0.0001)
        assertEquals(6, result.totalRuns)
    }

    // Scenario 5: slowFrames=25, totalFrames=100 → slowSessionRate=1.0 (threshold exactly 0.25)
    @Test
    fun `slow session threshold at exactly 0_25 counts as slow`() {
        val result = RateCalculator.compute(listOf(entry(slowFrames = 25, totalFrames = 100)))

        assertEquals(1.0, result.slowSessionRate)
    }

    // Scenario 6: slowFrames=0, totalFrames=0 → slowSessionRate=0.0 (guard: totalFrames > 0)
    @Test
    fun `zero totalFrames produces zero slowSessionRate without dividing by zero`() {
        val result = RateCalculator.compute(listOf(entry(slowFrames = 0, totalFrames = 0)))

        assertEquals(0.0, result.slowSessionRate)
    }

    // Scenario 7: Window truncation — 50 entries, windowSize=30 → totalRuns=30
    @Test
    fun `window truncation uses last 30 of 50 entries`() {
        val entries = List(50) { idx -> entry(anrCount = if (idx >= 20) 0 else 1, index = idx) }
        val result = RateCalculator.compute(entries, windowSize = 30)

        assertEquals(30, result.totalRuns)
        // Last 30 entries (indices 20-49) all have anrCount=0
        assertEquals(0.0, result.anrRate)
    }

    // Scenario 8: hasExitSignalsCaveat true when any entry has exitSignalsEnabled=false
    @Test
    fun `hasExitSignalsCaveat is true when any entry has exitSignalsEnabled false`() {
        val entries = listOf(
            entry(exitSignalsEnabled = true, index = 0),
            entry(exitSignalsEnabled = false, index = 1),
            entry(exitSignalsEnabled = true, index = 2),
        )
        val result = RateCalculator.compute(entries)

        assertTrue(result.hasExitSignalsCaveat)
    }

    // Scenario 9: hasExitSignalsCaveat false when all entries have exitSignalsEnabled=true
    @Test
    fun `hasExitSignalsCaveat is false when all entries have exitSignalsEnabled true`() {
        val entries = List(3) { idx -> entry(exitSignalsEnabled = true, index = idx) }
        val result = RateCalculator.compute(entries)

        assertFalse(result.hasExitSignalsCaveat)
    }

    // Triangulation: windowSize is propagated to the summary
    @Test
    fun `windowSize field in result matches the windowSize parameter`() {
        val entries = List(5) { idx -> entry(index = idx) }
        val result = RateCalculator.compute(entries, windowSize = 15)

        assertEquals(15, result.windowSize)
    }
}

private fun assertEquals(expected: Double, actual: Double, absoluteTolerance: Double) {
    assertTrue(
        kotlin.math.abs(expected - actual) <= absoluteTolerance,
        "Expected $expected ± $absoluteTolerance but was $actual",
    )
}
