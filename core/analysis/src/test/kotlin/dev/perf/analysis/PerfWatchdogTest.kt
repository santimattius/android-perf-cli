package dev.perf.analysis

import dev.perf.adb.AdbShellResult
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

    // T-5.4 test (a): backgroundNetwork=true → report.backgroundNetwork non-null
    @Test
    fun `watchdog_backgroundNetworkEnabled_reportBackgroundNetworkNonNull`() = runTest(UnconfinedTestDispatcher()) {
        val pmListOutput = "package:com.example.app uid:10234\n"
        val netstatsOutput = "uid=10234 set=DEFAULT tag=0x0 rx=1000 tx=200\n"
        val fake = FakeAdbClient(
            allowFailureHandler = { args ->
                when {
                    args.toList() == listOf("pm", "list", "packages", "-U", "com.example.app") ->
                        AdbShellResult(0, pmListOutput, "")
                    args.contains("netstats") ->
                        AdbShellResult(0, netstatsOutput, "")
                    else ->
                        AdbShellResult(0, "", "")
                }
            },
        )
        val watchdog = PerfWatchdog(
            device = fake,
            packageName = "com.example.app",
            monitor = MonitorConfig(strictMode = false, anrHints = false, rendering = false, backgroundNetwork = true),
            scope = this,
        )
        watchdog.start()
        val report = watchdog.stop()
        assertNotNull(report.backgroundNetwork)
    }

    // T-5.4 test (b): backgroundWifi=true → report.backgroundWifi non-null
    @Test
    fun `watchdog_backgroundWifiEnabled_reportBackgroundWifiNonNull`() = runTest(UnconfinedTestDispatcher()) {
        val batteryStatsOutput = """
            Statistics since last unplugged:
              Uid 10123: com.example.app
                Wifi scan: 5 times
        """.trimIndent()
        val fake = FakeAdbClient(
            allowFailureHandler = { args ->
                when {
                    args.contains("batterystats") && !args.contains("--reset") ->
                        AdbShellResult(0, batteryStatsOutput, "")
                    else ->
                        AdbShellResult(0, "", "")
                }
            },
        )
        val watchdog = PerfWatchdog(
            device = fake,
            packageName = "com.example.app",
            monitor = MonitorConfig(strictMode = false, anrHints = false, rendering = false, backgroundWifi = true),
            scope = this,
        )
        watchdog.start()
        val report = watchdog.stop()
        assertNotNull(report.backgroundWifi)
    }

    // T-5.4 test (c): stuckWakelocks=true + wakelocks=true → report.stuckWakelocks non-null
    @Test
    fun `watchdog_stuckWakelocksEnabled_reportStuckWakelocksNonNull`() = runTest(UnconfinedTestDispatcher()) {
        val batteryStatsOutput = """
            Statistics since last unplugged:
              Uid 10123: com.example.app
                Wake lock *alarm*: 1 times, 2s realtime
        """.trimIndent()
        val fake = FakeAdbClient(
            allowFailureHandler = { args ->
                when {
                    args.contains("batterystats") && !args.contains("--reset") ->
                        AdbShellResult(0, batteryStatsOutput, "")
                    else ->
                        AdbShellResult(0, "", "")
                }
            },
        )
        val watchdog = PerfWatchdog(
            device = fake,
            packageName = "com.example.app",
            monitor = MonitorConfig(
                strictMode = false, anrHints = false, rendering = false,
                wakelocks = true, stuckWakelocks = true,
            ),
            scope = this,
        )
        watchdog.start()
        val report = watchdog.stop()
        assertNotNull(report.stuckWakelocks)
    }

    // T-5.4 test (d): all three flags false → all three fields remain null
    @Test
    fun `watchdog_allBackgroundFlagsFalse_allBackgroundFieldsNull`() = runTest(UnconfinedTestDispatcher()) {
        val fake = FakeAdbClient(
            streamSessions = emptyList(),
        )
        val watchdog = PerfWatchdog(
            device = fake,
            packageName = "com.example.app",
            monitor = MonitorConfig(strictMode = false, anrHints = false, rendering = false),
            scope = this,
        )
        watchdog.start()
        val report = watchdog.stop()
        assertNull(report.backgroundNetwork)
        assertNull(report.backgroundWifi)
        assertNull(report.stuckWakelocks)
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
