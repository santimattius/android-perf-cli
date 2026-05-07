package dev.perf.analysis

import dev.perf.analysis.fake.FakeAdbClient
import dev.perf.analysis.model.ExitType
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerfWatchdogTest {

    // TASK-26: exitSignals disabled → capture never called, report.exitSignalReport == null
    @Test
    fun `watchdog_exitSignalsDisabled_captureNeverCalled`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeAdbClient(
            streamSessions = emptyList(),
        )
        val watchdog = PerfWatchdog(
            device = fake,
            packageName = "com.example.app",
            monitor = MonitorConfig(strictMode = false, anrHints = false, rendering = false, exitSignals = false),
            scope = this,
        )
        watchdog.start()
        val report = watchdog.stop()
        assertNull(report.exitSignalReport)
    }

    // TASK-28: exitSignals enabled + ANR line → report.exitSignalReport non-null with events
    @Test
    fun `watchdog_exitSignalsEnabled_reportContainsEvents`() = runTest(UnconfinedTestDispatcher()) {
        val amAnrLine = "01-15 10:00:00.100  1234  1235 I am_anr : [1234,1000,com.example.app,952,Input dispatching timed out]"
        val fake = FakeAdbClient(
            streamSessions = listOf(
                listOf(0L to amAnrLine),  // events-buffer (stream 1)
                emptyList(),              // crash-buffer  (stream 2)
                emptyList(),              // lmkd          (stream 3)
            ),
        )
        val watchdog = PerfWatchdog(
            device = fake,
            packageName = "com.example.app",
            monitor = MonitorConfig(strictMode = false, anrHints = false, rendering = false, exitSignals = true),
            scope = this,
        )
        watchdog.start()
        val report = watchdog.stop()
        assertNotNull(report.exitSignalReport)
        assertTrue(report.exitSignalReport!!.hasExits)
        assertTrue(report.exitSignalReport!!.events.any { it.exitType == ExitType.ANR })
    }
}
