package dev.perf.analysis

import dev.perf.analysis.model.BackgroundNetworkReport
import dev.perf.analysis.model.ExitEvent
import dev.perf.analysis.model.ExitSignalReport
import dev.perf.analysis.model.ExitType
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StuckWakelockEntry
import dev.perf.analysis.model.StuckWakelockReport
import dev.perf.analysis.model.WifiScanReport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerfReportTest {

    private fun baseReport() = PerfReport(
        packageName = "com.example.app",
        strictModeViolations = emptyList(),
        renderingIncident = RenderingIncident(
            severity = Severity.INFO,
            message = "no issues",
            jankyFrames = 0,
            frozenFrames = 0,
            totalFrames = 100,
            p90Ms = 10.0,
            p95Ms = 12.0,
            p99Ms = 15.0,
        ),
        memoryLeaks = emptyList(),
    )

    // TASK-30 test 1: null exitSignalReport does not affect hasIssues
    @Test
    fun `perfReport_exitSignalReportNull_hasIssuesUnchanged`() {
        val report = baseReport().copy(exitSignalReport = null)
        assertFalse(report.hasIssues)
    }

    // TASK-30 test 2: exitSignalReport with ANR event → hasIssues == true
    @Test
    fun `perfReport_exitSignalReportWithEvents_hasIssuesTrue`() {
        val exitReport = ExitSignalReport(
            events = listOf(
                ExitEvent(
                    exitType = ExitType.ANR,
                    message = "ANR: Input dispatching timed out",
                    packageName = "com.example.app",
                    rawLine = "raw",
                ),
            ),
        )
        val report = baseReport().copy(exitSignalReport = exitReport)
        assertTrue(report.hasIssues)
    }

    // T-5.3 test 1: all three background fields null → hasIssues and overallSeverity unchanged
    @Test
    fun `perfReport_backgroundFieldsAllNull_hasIssuesUnchanged`() {
        val report = baseReport().copy(
            backgroundNetwork = null,
            backgroundWifi = null,
            stuckWakelocks = null,
        )
        assertFalse(report.hasIssues)
        assertEquals(Severity.INFO, report.overallSeverity)
    }

    // T-5.3 test 2: non-null INFO background fields → hasIssues and overallSeverity unchanged
    @Test
    fun `perfReport_backgroundFieldsNonNull_hasIssuesUnchanged`() {
        val report = baseReport().copy(
            backgroundNetwork = BackgroundNetworkReport(rxBytesDelta = 1024L, txBytesDelta = 512L, durationMs = 60000L),
            backgroundWifi = WifiScanReport(scanCountDelta = 7, durationMs = 60000L),
            stuckWakelocks = StuckWakelockReport(
                stuckEntries = listOf(
                    StuckWakelockEntry("MY_LOCK", startCount = 1, endCount = 2, durationDeltaMs = 1000L)
                )
            ),
        )
        // INFO signals do NOT affect hasIssues
        assertFalse(report.hasIssues)
        // INFO signals do NOT affect overallSeverity (stays INFO from base)
        assertEquals(Severity.INFO, report.overallSeverity)
    }

    // TASK-30 test 3: exitSignalReport with ANR event → overallSeverity == CRITICAL
    @Test
    fun `perfReport_exitSignalReportWithEvents_overallSeverityCritical`() {
        val exitReport = ExitSignalReport(
            events = listOf(
                ExitEvent(
                    exitType = ExitType.ANR,
                    message = "ANR: Input dispatching timed out",
                    packageName = "com.example.app",
                    rawLine = "raw",
                ),
            ),
        )
        val report = baseReport().copy(exitSignalReport = exitReport)
        assertEquals(Severity.CRITICAL, report.overallSeverity)
    }
}
