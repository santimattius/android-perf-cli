package dev.perf.cli.scenario

import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.MemoryLeak
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StrictModeViolation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Behavioral tests for [ThresholdEvaluator].
 *
 * ThresholdEvaluator is a pure function — no fakes or coroutines needed.
 * Each test describes the exact policy being enforced.
 */
class ThresholdEvaluatorTest {

    // ── No breaches ───────────────────────────────────────────────────────────

    @Test
    fun `returns no breaches when all metrics are within thresholds`() {
        val report = reportWith(jankyFrames = 1, totalFrames = 100) // 1% < 5% default
        val breaches = ThresholdEvaluator.evaluate(report, ThresholdConfig())

        assertTrue(breaches.isEmpty())
    }

    @Test
    fun `returns no breaches when report has no frames`() {
        val report = reportWith(jankyFrames = 0, totalFrames = 0)
        val breaches = ThresholdEvaluator.evaluate(report, ThresholdConfig())

        assertTrue(breaches.isEmpty())
    }

    // ── Janky frame threshold ─────────────────────────────────────────────────

    @Test
    fun `reports breach when janky rate exceeds maxJankyPercent`() {
        // 6 janky out of 100 = 6% > 5% default
        val report = reportWith(jankyFrames = 6, totalFrames = 100)
        val breaches = ThresholdEvaluator.evaluate(report, ThresholdConfig(maxJankyPercent = 5.0))

        assertEquals(1, breaches.size)
        assertTrue(breaches.first().message.contains("6.0%"))
        assertTrue(breaches.first().message.contains("5.0%"))
    }

    @Test
    fun `does not breach when janky rate equals maxJankyPercent exactly`() {
        // 5 janky out of 100 = exactly 5% — boundary is exclusive
        val report = reportWith(jankyFrames = 5, totalFrames = 100)
        val breaches = ThresholdEvaluator.evaluate(report, ThresholdConfig(maxJankyPercent = 5.0))

        assertTrue(breaches.isEmpty())
    }

    @Test
    fun `respects custom janky threshold per scenario`() {
        // A strict scenario: 0% janky tolerated
        val report = reportWith(jankyFrames = 1, totalFrames = 100)
        val breaches = ThresholdEvaluator.evaluate(report, ThresholdConfig(maxJankyPercent = 0.0))

        assertEquals(1, breaches.size)
    }

    // ── Frozen frame threshold ────────────────────────────────────────────────

    @Test
    fun `reports breach when frozen frames exceed maxFrozenFrames`() {
        val report = reportWith(frozenFrames = 1)
        val breaches = ThresholdEvaluator.evaluate(report, ThresholdConfig(maxFrozenFrames = 0))

        assertEquals(1, breaches.size)
        assertTrue(breaches.first().message.contains("1"))
    }

    @Test
    fun `does not breach when frozen frames equal maxFrozenFrames`() {
        val report = reportWith(frozenFrames = 2)
        val breaches = ThresholdEvaluator.evaluate(report, ThresholdConfig(maxFrozenFrames = 2))

        assertTrue(breaches.isEmpty())
    }

    // ── StrictMode threshold ──────────────────────────────────────────────────

    @Test
    fun `reports breach when strictmode violations exceed threshold`() {
        val report = reportWith(violations = 3)
        val breaches = ThresholdEvaluator.evaluate(report, ThresholdConfig(maxStrictModeViolations = 0))

        assertEquals(1, breaches.size)
        assertTrue(breaches.first().message.contains("3"))
    }

    @Test
    fun `does not breach when violations equal threshold`() {
        val report = reportWith(violations = 2)
        val breaches = ThresholdEvaluator.evaluate(report, ThresholdConfig(maxStrictModeViolations = 2))

        assertTrue(breaches.isEmpty())
    }

    // ── Multiple breaches ─────────────────────────────────────────────────────

    @Test
    fun `reports all independent breaches in a single evaluation`() {
        val report = reportWith(
            jankyFrames = 10,
            totalFrames = 100,
            frozenFrames = 1,
            violations = 2,
        )
        val thresholds = ThresholdConfig(
            maxJankyPercent = 5.0,
            maxFrozenFrames = 0,
            maxStrictModeViolations = 0,
        )

        val breaches = ThresholdEvaluator.evaluate(report, thresholds)

        assertEquals(3, breaches.size)
    }

    // ── Severity ──────────────────────────────────────────────────────────────

    @Test
    fun `breaches always carry CRITICAL severity`() {
        val report = reportWith(frozenFrames = 1)
        val breaches = ThresholdEvaluator.evaluate(report, ThresholdConfig(maxFrozenFrames = 0))

        assertTrue(breaches.all { it.severity == Severity.CRITICAL })
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun reportWith(
        jankyFrames: Int = 0,
        frozenFrames: Int = 0,
        totalFrames: Int = 100,
        violations: Int = 0,
    ) = PerfReport(
        packageName = "com.example.app",
        strictModeViolations = List(violations) {
            StrictModeViolation(
                violationType = StrictModeViolation.ViolationType.DISK_READ,
                threadName = "main",
                message = "StrictModeDiskReadViolation",
                stackTrace = "",
            )
        },
        renderingIncident = RenderingIncident(
            severity = if (frozenFrames > 0 || jankyFrames > 0) Severity.WARNING else Severity.INFO,
            message = "test",
            jankyFrames = jankyFrames,
            frozenFrames = frozenFrames,
            totalFrames = totalFrames,
            p90Ms = 10.0,
            p95Ms = 12.0,
            p99Ms = 15.0,
        ),
        memoryLeaks = emptyList(),
    )
}
