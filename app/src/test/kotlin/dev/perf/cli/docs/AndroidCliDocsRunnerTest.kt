package dev.perf.cli.docs

import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StrictModeViolation
import dev.perf.analysis.model.StrictModeViolation.ViolationType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class AndroidCliDocsRunnerTest {

    @Test
    fun `buildQuery includes vitals and strict mode when violations present`() {
        val report = PerfReport(
            packageName = "com.example",
            strictModeViolations = listOf(
                StrictModeViolation(
                    violationType = ViolationType.DISK_READ,
                    threadName = "main",
                    message = "x",
                    stackTrace = "",
                ),
            ),
            renderingIncident = RenderingIncident(
                severity = Severity.INFO,
                message = "",
                jankyFrames = 0,
                frozenFrames = 0,
                totalFrames = 0,
                p90Ms = 0.0,
                p95Ms = 0.0,
                p99Ms = 0.0,
            ),
            memoryLeaks = emptyList(),
        )
        val q = AndroidCliDocsRunner().buildQuery(report)
        assertContains(q, "Vitals")
        assertContains(q, "StrictMode")
        assertFalse(q.length > 300)
    }
}
