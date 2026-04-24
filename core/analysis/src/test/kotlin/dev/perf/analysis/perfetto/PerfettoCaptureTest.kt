package dev.perf.analysis.perfetto

import dev.perf.adb.AdbShellResult
import dev.perf.analysis.fake.FakeAdbClient
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PerfettoCaptureTest {

    @Test
    fun `record fails when perfetto binary is missing`() = runBlocking {
        val fake = FakeAdbClient(
            allowFailureHandler = { args ->
                when {
                    args.size >= 3 && args[0] == "sh" && args[1] == "-c" ->
                        AdbShellResult(0, "", "")
                    else -> AdbShellResult(0, "", "")
                }
            },
        )
        val result = PerfettoCapture(fake).recordToRemotePath(10)
        assertIs<PerfettoRecordResult.Failed>(result)
        assertContains(result.reason.lowercase(), "not found")
    }

    @Test
    fun `record succeeds when perfetto exits zero`() = runBlocking {
        val fake = FakeAdbClient(
            allowFailureHandler = { args ->
                when {
                    args.size >= 3 && args[0] == "sh" && args[1] == "-c" ->
                        AdbShellResult(0, "/system/bin/perfetto", "")
                    args.firstOrNull() == "perfetto" ->
                        AdbShellResult(0, "", "")
                    else -> AdbShellResult(0, "", "")
                }
            },
        )
        val result = PerfettoCapture(fake).recordToRemotePath(5)
        assertIs<PerfettoRecordResult.Ok>(result)
        assertTrue(result.remotePath.startsWith("/data/local/tmp/perf_cli_"))
    }

    @Test
    fun `record surfaces stderr when perfetto exits non-zero`() = runBlocking {
        val fake = FakeAdbClient(
            allowFailureHandler = { args ->
                when {
                    args.size >= 3 && args[0] == "sh" && args[1] == "-c" ->
                        AdbShellResult(0, "/system/bin/perfetto", "")
                    args.firstOrNull() == "perfetto" ->
                        AdbShellResult(1, "", "invalid category: xyz")
                    else -> AdbShellResult(0, "", "")
                }
            },
        )
        val result = PerfettoCapture(fake).recordToRemotePath(3)
        assertIs<PerfettoRecordResult.Failed>(result)
        assertContains(result.reason, "exit")
        assertContains(result.reason, "invalid category")
    }
}
