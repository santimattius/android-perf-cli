package dev.perf.analysis.exit

import dev.perf.analysis.model.ExitEvent
import dev.perf.analysis.model.ExitSignalReport
import dev.perf.analysis.model.ExitType
import dev.perf.analysis.model.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExitSignalParserTest {

    // ── TASK-01/02: ExitType compilation gate ─────────────────────────────────

    @Test
    fun `exitType enum has ANR CRASH LMK values`() {
        val anr = ExitType.ANR
        val crash = ExitType.CRASH
        val lmk = ExitType.LMK
        assertEquals(ExitType.ANR, anr)
        assertEquals(ExitType.CRASH, crash)
        assertEquals(ExitType.LMK, lmk)
    }

    // ── TASK-03/04: ExitEvent construction ────────────────────────────────────

    @Test
    fun `exitEvent defaults to CRITICAL severity and null timestampMs`() {
        val event = ExitEvent(
            exitType = ExitType.ANR,
            message = "x",
            packageName = "p",
            rawLine = "r",
        )
        assertEquals(Severity.CRITICAL, event.severity)
        assertNull(event.timestampMs)
    }

    // ── TASK-05/06: ExitSignalReport ─────────────────────────────────────────

    @Test
    fun `exitSignalReport_emptyEvents_severityIsInfo`() {
        val report = ExitSignalReport(events = emptyList())
        assertEquals(Severity.INFO, report.severity)
        assertFalse(report.hasExits)
    }

    @Test
    fun `exitSignalReport_nonEmptyEvents_severityIsCritical`() {
        val event = ExitEvent(
            exitType = ExitType.CRASH,
            message = "FATAL EXCEPTION in com.example.app",
            packageName = "com.example.app",
            rawLine = "raw",
        )
        val report = ExitSignalReport(events = listOf(event))
        assertEquals(Severity.CRITICAL, report.severity)
        assertTrue(report.hasExits)
    }

    // ── TASK-07/08/09: parseAmAnrLine ─────────────────────────────────────────

    @Test
    fun `parseAmAnrLine_validLine_matchingPackage_returnsAnrEvent`() {
        val line = "01-15 10:00:00.100  1234  1235 I am_anr : [1234,1000,com.example.app,952,Input dispatching timed out]"
        val result = ExitSignalParser.parseAmAnrLine(line, "com.example.app")
        assertNotNull(result)
        assertEquals(ExitType.ANR, result.exitType)
        assertEquals("com.example.app", result.packageName)
    }

    @Test
    fun `parseAmAnrLine_validLine_differentPackage_returnsNull`() {
        val line = "01-15 10:00:00.100  1234  1235 I am_anr : [1234,1000,com.other.app,952,Input dispatching timed out]"
        val result = ExitSignalParser.parseAmAnrLine(line, "com.example.app")
        assertNull(result)
    }

    // ── TASK-10/11/12/13: parseCrashLine ──────────────────────────────────────

    @Test
    fun `parseCrashLine_fatalExceptionFollowedByMatchingProcess_returnsCrashEvent`() {
        val previousLine = "01-15 10:00:01.000  5678  5679 E AndroidRuntime: FATAL EXCEPTION: main"
        val line = "01-15 10:00:01.001  5678  5679 E AndroidRuntime: Process: com.example.app, PID: 5678"
        val result = ExitSignalParser.parseCrashLine(line, previousLine, "com.example.app")
        assertNotNull(result)
        assertEquals(ExitType.CRASH, result.exitType)
        assertEquals("com.example.app", result.packageName)
    }

    @Test
    fun `parseCrashLine_fatalExceptionFollowedByDifferentProcess_returnsNull`() {
        val previousLine = "01-15 10:00:01.000  5678  5679 E AndroidRuntime: FATAL EXCEPTION: main"
        val line = "01-15 10:00:01.001  5678  5679 E AndroidRuntime: Process: com.other.app, PID: 5678"
        val result = ExitSignalParser.parseCrashLine(line, previousLine, "com.example.app")
        assertNull(result)
    }

    @Test
    fun `parseCrashLine_noFatalException_returnsNull`() {
        val previousLine = "01-15 10:00:01.000  5678  5679 I SomeTag: some log line"
        val line = "01-15 10:00:01.001  5678  5679 E AndroidRuntime: Process: com.example.app, PID: 5678"
        val result = ExitSignalParser.parseCrashLine(line, previousLine, "com.example.app")
        assertNull(result)
    }

    // ── TASK-14/15/16/17: parseLmkLine ────────────────────────────────────────

    @Test
    fun `parseLmkLine_killVariant_matchingPackage_returnsLmkEvent`() {
        val line = "01-15 10:00:02.000   300   300 I lmkd: Kill 'com.example.app' (pid 9999): heavy fg cached"
        val result = ExitSignalParser.parseLmkLine(line, "com.example.app")
        assertNotNull(result)
        assertEquals(ExitType.LMK, result.exitType)
        assertEquals("com.example.app", result.packageName)
    }

    @Test
    fun `parseLmkLine_killVariant_prefixPackage_returnsNull`() {
        val line = "01-15 10:00:02.000   300   300 I lmkd: Kill 'com.example' (pid 9999): heavy fg cached"
        val result = ExitSignalParser.parseLmkLine(line, "com.example.app")
        assertNull(result)
    }

    @Test
    fun `parseLmkLine_reclaimVariant_matchingPackage_returnsLmkEvent`() {
        val line = "01-15 10:00:02.100   300   300 I lmkd: Reclaim 'com.example.app' (pid 9999)"
        val result = ExitSignalParser.parseLmkLine(line, "com.example.app")
        assertNotNull(result)
        assertEquals(ExitType.LMK, result.exitType)
    }
}
