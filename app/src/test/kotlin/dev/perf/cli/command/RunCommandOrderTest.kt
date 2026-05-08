package dev.perf.cli.command

import dev.perf.adb.AdbClient
import dev.perf.adb.AdbShellResult
import dev.perf.analysis.MonitorConfig
import dev.perf.analysis.PerfWatchdog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Verifies the strict ordering of ADB commands inside [PerfWatchdog].
 *
 * Required order:
 *   1. `batterystats --reset`  (wakelock reset)
 *   2. `batterystats <pkg>`    (wifi baseline snapshot — AFTER reset)
 *   3. `pm list packages -U`   (network UID resolution — baseline)
 *   4. `dumpsys netstats ...`  (network baseline bytes)
 *   ... monitoring window ...
 *   5. `pm list packages -U`   (network UID resolution — end)
 *   6. `dumpsys netstats ...`  (network end bytes)
 *   7. `batterystats <pkg>`    (wifi end snapshot)
 *   8. `batterystats <pkg>`    (wakelock capture — also used by StuckWakelockDetector)
 *
 * Note: steps 2-4 run in a coroutine launched by start() (captureBaselines), so they complete
 * before stop() calls computeDeltas. This test verifies that the reset always precedes the
 * wifi/network baselines and that wakelock capture is the last batterystats call.
 */
class RunCommandOrderTest {

    private val pkg = "com.example.test"

    /**
     * Recording fake ADB client that appends every shell command to [log].
     * Uses canonical fixture responses so the capture chain produces non-null results.
     */
    private class RecordingFakeAdbClient(
        val log: MutableList<List<String>> = mutableListOf(),
        private val pkg: String,
    ) : AdbClient {

        override val serial: String = "emulator-5554"

        private val pmListOutput = "package:$pkg uid:10234\n"
        private val netstatsOutput = "uid=10234 set=DEFAULT tag=0x0 rx=1000 tx=200\n"
        private val batteryStatsOutput = """
            Statistics since last unplugged:
              Uid 10234: $pkg
                Wifi scan: 5 times
                Wake lock *alarm*: 1 times, 2s realtime
        """.trimIndent()

        override fun shell(vararg args: String): String {
            log.add(args.toList())
            return ""
        }

        override fun shellAllowFailure(vararg args: String): AdbShellResult {
            log.add(args.toList())
            val argList = args.toList()
            return when {
                argList == listOf("pm", "list", "packages", "-U", pkg) ->
                    AdbShellResult(0, pmListOutput, "")
                argList.contains("netstats") ->
                    AdbShellResult(0, netstatsOutput, "")
                argList.contains("batterystats") && !argList.contains("--reset") ->
                    AdbShellResult(0, batteryStatsOutput, "")
                else ->
                    AdbShellResult(0, "", "")
            }
        }

        override suspend fun stream(vararg args: String, onLine: (String) -> Boolean) = Unit
        override fun pull(devicePath: String, destination: File): File = destination
        override fun push(source: File, devicePath: String) = Unit
        override fun install(apk: File, replace: Boolean) = Unit
        override fun execOut(vararg args: String): ByteArray = byteArrayOf()
    }

    /**
     * T-6.3: When all background signal flags are enabled, the ADB command order must be:
     * reset → wifi baseline → network baseline → [window] → network end → wifi end → wakelock capture.
     *
     * Specifically: the batterystats --reset command must precede the first batterystats capture
     * (wifi baseline), and wakelock capture must happen AFTER the network/wifi end snapshots.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `batterystats reset precedes wifi and network baseline captures`() = runTest(UnconfinedTestDispatcher()) {
        val recording = RecordingFakeAdbClient(pkg = pkg)
        val watchdog = PerfWatchdog(
            device = recording,
            packageName = pkg,
            monitor = MonitorConfig(
                strictMode = false,
                anrHints = false,
                rendering = false,
                wakelocks = true,
                backgroundWifi = true,
                backgroundNetwork = true,
                stuckWakelocks = true,
            ),
            scope = this,
        )

        watchdog.start()
        watchdog.stop()

        val commands = recording.log

        // Find indices of key ADB calls
        val resetIndex = commands.indexOfFirst { it.contains("--reset") }
        val wifiBaselineIndex = commands.indexOfFirst {
            it.contains("batterystats") && !it.contains("--reset")
        }
        val pmListIndex = commands.indexOfFirst {
            it == listOf("pm", "list", "packages", "-U", pkg)
        }

        assertTrue(resetIndex >= 0, "batterystats --reset must be called (index=$resetIndex)")
        assertTrue(wifiBaselineIndex >= 0, "batterystats wifi baseline must be called (index=$wifiBaselineIndex)")
        assertTrue(pmListIndex >= 0, "pm list packages (network uid) must be called (index=$pmListIndex)")

        assertTrue(
            resetIndex < wifiBaselineIndex,
            "batterystats --reset (index=$resetIndex) must happen BEFORE wifi baseline (index=$wifiBaselineIndex)",
        )
        assertTrue(
            resetIndex < pmListIndex,
            "batterystats --reset (index=$resetIndex) must happen BEFORE network pm list (index=$pmListIndex)",
        )
    }

    /**
     * T-6.3 secondary: wakelock capture (the final batterystats non-reset call) must happen
     * after all network snapshot calls.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `wakelock end capture happens after network end snapshot`() = runTest(UnconfinedTestDispatcher()) {
        val recording = RecordingFakeAdbClient(pkg = pkg)
        val watchdog = PerfWatchdog(
            device = recording,
            packageName = pkg,
            monitor = MonitorConfig(
                strictMode = false,
                anrHints = false,
                rendering = false,
                wakelocks = true,
                backgroundWifi = true,
                backgroundNetwork = true,
                stuckWakelocks = true,
            ),
            scope = this,
        )

        watchdog.start()
        watchdog.stop()

        val commands = recording.log

        // pm list appears twice: once for baseline, once for end snapshot
        val pmListIndices = commands.indices.filter {
            commands[it] == listOf("pm", "list", "packages", "-U", pkg)
        }
        // The last pm list call is the network end snapshot
        val lastPmListIndex = pmListIndices.lastOrNull() ?: -1

        // The last batterystats non-reset call is the wakelock end capture
        val wakelockCaptureIndex = commands.indices.filter {
            commands[it].contains("batterystats") && !commands[it].contains("--reset")
        }.lastOrNull() ?: -1

        assertTrue(pmListIndices.size >= 2, "pm list must be called at least twice (baseline + end), got ${pmListIndices.size}")
        assertTrue(lastPmListIndex >= 0, "last pm list index must be valid")
        assertTrue(wakelockCaptureIndex >= 0, "wakelock capture must be called")

        assertTrue(
            lastPmListIndex < wakelockCaptureIndex,
            "network end pm list (index=$lastPmListIndex) must precede final wakelock capture (index=$wakelockCaptureIndex)",
        )
    }
}
