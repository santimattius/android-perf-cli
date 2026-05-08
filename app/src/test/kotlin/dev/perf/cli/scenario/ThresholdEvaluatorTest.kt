package dev.perf.cli.scenario

import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.ColdStartMeasurement
import dev.perf.analysis.model.MemoryLeak
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StrictModeViolation
import dev.perf.analysis.model.WakelockEntry
import dev.perf.analysis.model.WakelockReport
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

    @Test
    fun `reports breach when cold start exceeds maxColdStartMs`() {
        val report = reportWith(
            coldStart = ColdStartMeasurement(
                totalTimeMs = 3000,
                waitTimeMs = 3000,
                launchState = "COLD",
                status = "ok",
                parsedOk = true,
                message = "ok",
                severity = Severity.WARNING,
            ),
        )
        val breaches = ThresholdEvaluator.evaluate(
            report,
            ThresholdConfig(maxColdStartMs = 2500),
        )
        assertEquals(1, breaches.size)
        assertTrue(breaches.first().message.contains("3000"))
        assertTrue(breaches.first().message.contains("2500"))
    }

    @Test
    fun `reports breach when wake lock total duration exceeds threshold`() {
        val wl =
            WakelockReport(
                entries = listOf(WakelockEntry("a", 1, 4000)),
                totalDurationMs = 4000,
            )
        val report = reportWith().copy(wakelockReport = wl)
        val breaches = ThresholdEvaluator.evaluate(report, ThresholdConfig(maxWakelockTimeMs = 3000))
        assertEquals(1, breaches.size)
        assertEquals(true, breaches.first().message.contains("4000"))
    }

    @Test
    fun `reports breach when wake lock entry count exceeds threshold`() {
        val wl =
            WakelockReport(
                entries =
                    listOf(
                        WakelockEntry("a", 1, 1),
                        WakelockEntry("b", 1, 1),
                        WakelockEntry("c", 1, 1),
                    ),
                totalDurationMs = 3,
            )
        val report = reportWith().copy(wakelockReport = wl)
        val breaches = ThresholdEvaluator.evaluate(report, ThresholdConfig(maxWakelockCount = 2))
        assertEquals(1, breaches.size)
        assertEquals(true, breaches.first().message.contains("3"))
    }

    @Test
    fun `applyToReport sets wakelock severity to WARNING when entries exist and no breach`() {
        val wl =
            WakelockReport(
                entries = listOf(WakelockEntry("a", 1, 100)),
                totalDurationMs = 100,
            )
        val out =
            ThresholdEvaluator.applyToReport(
                reportWith().copy(wakelockReport = wl),
                ThresholdConfig(maxWakelockTimeMs = null, maxWakelockCount = null),
            )
        assertEquals(Severity.WARNING, out.wakelockReport?.severity)
    }

    @Test
    fun `applyToReport sets wakelock severity to CRITICAL when duration threshold breached`() {
        val wl =
            WakelockReport(
                entries = listOf(WakelockEntry("a", 1, 5000)),
                totalDurationMs = 5000,
            )
        val out =
            ThresholdEvaluator.applyToReport(
                reportWith().copy(wakelockReport = wl),
                ThresholdConfig(maxWakelockTimeMs = 3000),
            )
        assertEquals(Severity.CRITICAL, out.wakelockReport?.severity)
        assertEquals(true, out.thresholdBreaches.any { it.message.startsWith("Wake lock total duration") })
    }

    @Test
    fun `does not breach cold start when threshold is null`() {
        val report = reportWith(
            coldStart = ColdStartMeasurement(
                totalTimeMs = 99999,
                waitTimeMs = null,
                launchState = null,
                status = null,
                parsedOk = true,
                message = "x",
                severity = Severity.CRITICAL,
            ),
        )
        val breaches = ThresholdEvaluator.evaluate(report, ThresholdConfig(maxColdStartMs = null))
        assertTrue(breaches.none { it.message.contains("Cold start") })
    }

    // ── Wake-Lock Session-Percentage Evaluation ───────────────────────────────

    @Test
    fun `wakelock entry exceeding maxWakelockLongHeldMs emits WARNING breach mentioning session and wake lock`() {
        val wl = WakelockReport(
            entries = listOf(WakelockEntry("alarm-wakelock", 1, 4_000_000L)),
            totalDurationMs = 4_000_000L,
        )
        val thresholds = ThresholdConfig(
            maxWakelockSessionPercent = 5.0,
            maxWakelockLongHeldMs = 3_600_000L,
        )
        val report = reportWith().copy(wakelockReport = wl)
        val breaches = ThresholdEvaluator.evaluate(report, thresholds)

        val sessionBreach = breaches.find { it.message.contains("session", ignoreCase = true) }
        assertTrue(sessionBreach != null, "Expected a session-percent breach but found none. Breaches: $breaches")
        assertTrue(sessionBreach.message.contains("wake lock", ignoreCase = true), "Breach message should mention 'wake lock': ${sessionBreach.message}")
        assertEquals(Severity.WARNING, sessionBreach.severity, "Session-percent breach should be WARNING, not CRITICAL")
    }

    @Test
    fun `all wakelock entries below maxWakelockLongHeldMs produce no session-percent breach`() {
        val wl = WakelockReport(
            entries = listOf(
                WakelockEntry("short-lock", 5, 1_000_000L),
                WakelockEntry("medium-lock", 3, 3_599_999L),
            ),
            totalDurationMs = 4_599_999L,
        )
        val thresholds = ThresholdConfig(
            maxWakelockSessionPercent = 5.0,
            maxWakelockLongHeldMs = 3_600_000L,
        )
        val report = reportWith().copy(wakelockReport = wl)
        val breaches = ThresholdEvaluator.evaluate(report, thresholds)

        val sessionBreach = breaches.find { it.message.contains("session", ignoreCase = true) }
        assertTrue(sessionBreach == null, "Expected no session-percent breach but found: $sessionBreach")
    }

    @Test
    fun `maxWakelockSessionPercent null means no session-percent breach even with 2h entry`() {
        val wl = WakelockReport(
            entries = listOf(WakelockEntry("long-lock", 1, 7_200_000L)),
            totalDurationMs = 7_200_000L,
        )
        val thresholds = ThresholdConfig(
            maxWakelockSessionPercent = null,
            maxWakelockLongHeldMs = 3_600_000L,
        )
        val report = reportWith().copy(wakelockReport = wl)
        val breaches = ThresholdEvaluator.evaluate(report, thresholds)

        val sessionBreach = breaches.find { it.message.contains("session", ignoreCase = true) }
        assertTrue(sessionBreach == null, "Expected no session-percent breach when maxWakelockSessionPercent is null, found: $sessionBreach")
    }

    // ── Task 3.4: applyToReport severity stays WARNING for session-percent breach ──

    @Test
    fun `applyToReport with only session-percent breach keeps wakelock severity as WARNING not CRITICAL`() {
        val wl = WakelockReport(
            entries = listOf(WakelockEntry("long-alarm", 1, 4_000_000L)),
            totalDurationMs = 4_000_000L,
        )
        val thresholds = ThresholdConfig(
            maxWakelockTimeMs = null,
            maxWakelockCount = null,
            maxWakelockSessionPercent = 5.0,
            maxWakelockLongHeldMs = 3_600_000L,
        )
        val result = ThresholdEvaluator.applyToReport(reportWith().copy(wakelockReport = wl), thresholds)

        assertEquals(Severity.WARNING, result.wakelockReport?.severity,
            "Severity should remain WARNING when only a session-percent breach fires")
        assertTrue(result.thresholdBreaches.any { it.message.contains("session", ignoreCase = true) },
            "Expected a session-percent breach in thresholdBreaches")
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun reportWith(
        jankyFrames: Int = 0,
        frozenFrames: Int = 0,
        totalFrames: Int = 100,
        violations: Int = 0,
        coldStart: ColdStartMeasurement? = null,
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
        coldStart = coldStart,
    )
}
