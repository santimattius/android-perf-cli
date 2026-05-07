package dev.perf.analysis

import dev.perf.analysis.model.ExitEvent
import dev.perf.analysis.model.ExitSignalReport
import dev.perf.analysis.model.ExitType
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
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
