package dev.perf.cli.docs

import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StrictModeViolation
import dev.perf.analysis.model.StrictModeViolation.ViolationType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotEquals

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

    @Test
    fun `searchTimeoutMs default is 90 seconds`() {
        // Verifies Decision 1: default changed from 18_000L to 90_000L.
        // Reads the field directly — requires it to be internal (change in Task 1.2).
        val runner = AndroidCliDocsRunner()
        assertEquals(90_000L, runner.searchTimeoutMs)
    }

    @Test
    fun `searchForReport returns null not an error string when binary absent`() {
        // On test JVM the android binary is never present.
        // findAndroidBinary() returns null → searchForReport returns null.
        // This test currently passes (binary absent path already returns null).
        // It becomes a REGRESSION guard: once the timeout string was the *only*
        // non-null return from timeout, removing it must never reintroduce a string.
        val runner = AndroidCliDocsRunner(searchTimeoutMs = 1L)
        val report = minimalReport()
        val result = runner.searchForReport(report)
        assertNull(result)
        assertNotEquals("(timeout after 1ms)", result)
    }

    private fun minimalReport() = PerfReport(
        packageName = "com.example.test",
        strictModeViolations = emptyList(),
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
}
