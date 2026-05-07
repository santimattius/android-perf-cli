package dev.perf.analysis.exit

import dev.perf.adb.AdbShellResult
import dev.perf.analysis.fake.FakeAdbClient
import dev.perf.analysis.model.ExitType
import dev.perf.analysis.model.Severity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for [ExitSignalCapture].
 *
 * Stream launch order in the engine is fixed: events-buffer → crash-buffer → lmkd.
 * [FakeAdbClient.streamSessions] dequeues positionally, so test setup MUST follow this order.
 */
class ExitSignalCaptureTest {

    private val pkg = "com.example.app"

    // T-CAP-01
    @Test
    fun `capture_amAnrLine_reportsOneAnrEvent`() = runTest {
        val amAnrLine = "01-15 10:00:00.100  1234  1235 I am_anr : [1234,1000,com.example.app,952,Input dispatching timed out]"
        val fake = FakeAdbClient(
            streamSessions = listOf(
                listOf(0L to amAnrLine),  // events-buffer (stream 1)
                emptyList(),              // crash-buffer  (stream 2)
                emptyList(),              // lmkd          (stream 3)
            ),
        )
        val report = ExitSignalCapture(fake).capture(pkg, 100L)
        assertEquals(1, report.events.size)
        assertEquals(ExitType.ANR, report.events[0].exitType)
        assertTrue(report.hasExits)
        assertEquals(Severity.CRITICAL, report.severity)
    }

    // T-CAP-02
    @Test
    fun `capture_fatalExceptionTwoLiner_reportsOneCrashEvent`() = runTest {
        val line1 = "01-15 10:00:01.000  5678  5679 E AndroidRuntime: FATAL EXCEPTION: main"
        val line2 = "01-15 10:00:01.001  5678  5679 E AndroidRuntime: Process: com.example.app, PID: 5678"
        val fake = FakeAdbClient(
            streamSessions = listOf(
                emptyList(),                                   // events-buffer (stream 1)
                listOf(0L to line1, 0L to line2),             // crash-buffer  (stream 2)
                emptyList(),                                   // lmkd          (stream 3)
            ),
        )
        val report = ExitSignalCapture(fake).capture(pkg, 100L)
        assertEquals(1, report.events.size)
        assertEquals(ExitType.CRASH, report.events[0].exitType)
    }

    // T-CAP-03
    @Test
    fun `capture_lmkdKillLine_reportsOneLmkEvent`() = runTest {
        val lmkLine = "01-15 10:00:02.000   300   300 I lmkd: Kill 'com.example.app' (pid 9999): heavy fg cached"
        val fake = FakeAdbClient(
            streamSessions = listOf(
                emptyList(),              // events-buffer (stream 1)
                emptyList(),              // crash-buffer  (stream 2)
                listOf(0L to lmkLine),   // lmkd          (stream 3)
            ),
        )
        val report = ExitSignalCapture(fake).capture(pkg, 100L)
        assertEquals(1, report.events.size)
        assertEquals(ExitType.LMK, report.events[0].exitType)
    }

    // T-CAP-04
    @Test
    fun `capture_noMatchingLines_returnsEmptyReport`() = runTest {
        val fake = FakeAdbClient(
            streamSessions = listOf(
                listOf(0L to "01-15 10:00:00.000  1234 1234 I SomeTag: unrelated line"),
                listOf(0L to "01-15 10:00:00.001  1234 1234 I SomeTag: another unrelated line"),
                listOf(0L to "01-15 10:00:00.002  1234 1234 I SomeTag: yet another unrelated line"),
            ),
        )
        val report = ExitSignalCapture(fake).capture(pkg, 100L)
        assertTrue(report.events.isEmpty())
        assertFalse(report.hasExits)
        assertEquals(Severity.INFO, report.severity)
    }

    // T-CAP-05
    @Test
    fun `capture_exitInfoShellFails_returnsGracefully`() = runTest {
        val fake = FakeAdbClient(
            allowFailureHandler = { AdbShellResult(1, "", "not found") },
            streamSessions = listOf(
                emptyList(),
                emptyList(),
                emptyList(),
            ),
        )
        val report = ExitSignalCapture(fake).capture(pkg, 100L)
        assertTrue(report.events.isEmpty())
    }
}
