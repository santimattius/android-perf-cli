package dev.perf.cli.command

import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.ExitEvent
import dev.perf.analysis.model.ExitSignalReport
import dev.perf.analysis.model.ExitType
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.startup.StartupReport
import dev.perf.analysis.model.startup.StartupStats
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [buildHistoryEntry] — the top-level internal function that maps
 * a [PerfReport] to a [dev.perf.cli.history.SessionHistoryEntry] without spinning
 * up a Clikt command invocation.
 *
 * These tests follow the Strict TDD contract: written BEFORE the implementation exists (RED).
 */
class RunCommandHistoryIntegrationTest {

    // ── Helpers ────────────────────────────────────────────────────────────────

    private fun minimalRendering(
        totalFrames: Int = 0,
        slowFrames: Int = 0,
        frozenFrames: Int = 0,
    ) = RenderingIncident(
        severity = Severity.INFO,
        message = "",
        jankyFrames = 0,
        frozenFrames = frozenFrames,
        totalFrames = totalFrames,
        p90Ms = 0.0,
        p95Ms = 0.0,
        p99Ms = 0.0,
        slowFrames = slowFrames,
    )

    private fun minimalReport(
        exitSignalReport: ExitSignalReport? = null,
        startup: StartupReport? = null,
        totalFrames: Int = 0,
        slowFrames: Int = 0,
        frozenFrames: Int = 0,
    ): PerfReport = PerfReport(
        packageName = "com.example.app",
        strictModeViolations = emptyList(),
        renderingIncident = minimalRendering(totalFrames, slowFrames, frozenFrames),
        memoryLeaks = emptyList(),
        exitSignalReport = exitSignalReport,
        startup = startup,
    )

    private fun anrEvent() = ExitEvent(
        exitType = ExitType.ANR,
        packageName = "com.example.app",
        rawLine = "ANR in com.example.app",
        message = "ANR detected",
    )

    private fun crashEvent() = ExitEvent(
        exitType = ExitType.CRASH,
        packageName = "com.example.app",
        rawLine = "FATAL EXCEPTION",
        message = "Crash detected",
    )

    private val timestampIso = "2026-05-10T12:00:00Z"

    // ── T-6.1.1: anrCount equals count of ANR events ───────────────────────────

    @Test
    fun `anrCount equals count of ANR events in exitSignalReport`() {
        val report = minimalReport(
            exitSignalReport = ExitSignalReport(
                events = listOf(anrEvent(), anrEvent(), crashEvent()),
            ),
        )

        val entry = buildHistoryEntry(
            report = report,
            packageName = "com.example.app",
            exitSignalsEnabled = true,
            scenarioLabel = null,
            timestampIso = timestampIso,
        )

        assertEquals(2, entry.anrCount, "anrCount must equal count of ANR events")
    }

    // ── T-6.1.2: crashCount equals count of CRASH events ──────────────────────

    @Test
    fun `crashCount equals count of CRASH events in exitSignalReport`() {
        val report = minimalReport(
            exitSignalReport = ExitSignalReport(
                events = listOf(anrEvent(), crashEvent(), crashEvent()),
            ),
        )

        val entry = buildHistoryEntry(
            report = report,
            packageName = "com.example.app",
            exitSignalsEnabled = true,
            scenarioLabel = null,
            timestampIso = timestampIso,
        )

        assertEquals(2, entry.crashCount, "crashCount must equal count of CRASH events")
    }

    // ── T-6.1.3: coldStartP50Ms maps from report.startup?.cold?.p50Ms ─────────

    @Test
    fun `coldStartP50Ms maps from startup cold p50Ms`() {
        val startupStats = StartupStats(
            samples = emptyList(),
            p50Ms = 750,
            severity = Severity.INFO,
            message = "Cold start ok",
        )
        val report = minimalReport(
            startup = StartupReport(cold = startupStats),
        )

        val entry = buildHistoryEntry(
            report = report,
            packageName = "com.example.app",
            exitSignalsEnabled = true,
            scenarioLabel = null,
            timestampIso = timestampIso,
        )

        assertEquals(750, entry.coldStartP50Ms, "coldStartP50Ms must map from startup.cold.p50Ms")
    }

    // ── T-6.1.4: null exitSignalReport → anrCount = 0, crashCount = 0 ─────────

    @Test
    fun `null exitSignalReport produces zero anrCount and crashCount`() {
        val report = minimalReport(exitSignalReport = null)

        val entry = buildHistoryEntry(
            report = report,
            packageName = "com.example.app",
            exitSignalsEnabled = false,
            scenarioLabel = null,
            timestampIso = timestampIso,
        )

        assertEquals(0, entry.anrCount, "anrCount must be 0 when exitSignalReport is null")
        assertEquals(0, entry.crashCount, "crashCount must be 0 when exitSignalReport is null")
    }

    // ── T-6.1.5: runId contains timestampIso as prefix ─────────────────────────

    @Test
    fun `runId contains timestampIso as prefix`() {
        val report = minimalReport()

        val entry = buildHistoryEntry(
            report = report,
            packageName = "com.example.app",
            exitSignalsEnabled = true,
            scenarioLabel = null,
            timestampIso = timestampIso,
        )

        assertTrue(
            entry.runId.startsWith(timestampIso),
            "runId must start with timestampIso, was: ${entry.runId}",
        )
    }

    // ── T-6.1.6: null startup → coldStartP50Ms is null ────────────────────────

    @Test
    fun `null startup produces null coldStartP50Ms`() {
        val report = minimalReport(startup = null)

        val entry = buildHistoryEntry(
            report = report,
            packageName = "com.example.app",
            exitSignalsEnabled = true,
            scenarioLabel = null,
            timestampIso = timestampIso,
        )

        assertNull(entry.coldStartP50Ms, "coldStartP50Ms must be null when startup is null")
    }

    // ── T-6.1.7: rendering fields map correctly ────────────────────────────────

    @Test
    fun `rendering fields totalFrames slowFrames frozenFrames map correctly`() {
        val report = minimalReport(totalFrames = 1000, slowFrames = 50, frozenFrames = 5)

        val entry = buildHistoryEntry(
            report = report,
            packageName = "com.example.app",
            exitSignalsEnabled = true,
            scenarioLabel = "smoke-test",
            timestampIso = timestampIso,
        )

        assertEquals(1000, entry.totalFrames)
        assertEquals(50, entry.slowFrames)
        assertEquals(5, entry.frozenFrames)
        assertEquals("smoke-test", entry.scenarioLabel)
    }
}
