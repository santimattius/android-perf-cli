package dev.perf.analysis.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T-1.1 RED: Verifies that SessionRateSummary holds all required fields and
 * that two instances with equal fields compare equal (data class equality).
 */
class SessionRateSummaryTest {

    @Test
    fun `holds all required fields with correct types`() {
        val summary = SessionRateSummary(
            totalRuns = 10,
            anrRate = 0.1,
            crashRate = 0.05,
            slowSessionRate = 0.25,
            windowSize = 30,
            hasExitSignalsCaveat = false,
        )

        assertEquals(10, summary.totalRuns)
        assertEquals(0.1, summary.anrRate)
        assertEquals(0.05, summary.crashRate)
        assertEquals(0.25, summary.slowSessionRate)
        assertEquals(30, summary.windowSize)
        assertFalse(summary.hasExitSignalsCaveat)
    }

    @Test
    fun `two instances with equal fields compare equal`() {
        val a = SessionRateSummary(
            totalRuns = 5,
            anrRate = 0.2,
            crashRate = 0.0,
            slowSessionRate = 0.5,
            windowSize = 30,
            hasExitSignalsCaveat = true,
        )
        val b = SessionRateSummary(
            totalRuns = 5,
            anrRate = 0.2,
            crashRate = 0.0,
            slowSessionRate = 0.5,
            windowSize = 30,
            hasExitSignalsCaveat = true,
        )

        assertEquals(a, b)
    }

    @Test
    fun `two instances with different fields are not equal`() {
        val a = SessionRateSummary(
            totalRuns = 5,
            anrRate = 0.2,
            crashRate = 0.0,
            slowSessionRate = 0.5,
            windowSize = 30,
            hasExitSignalsCaveat = false,
        )
        val b = a.copy(anrRate = 0.4)

        assertTrue(a != b)
    }
}
