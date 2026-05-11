package dev.perf.analysis.network

import dev.perf.adb.AdbShellResult
import dev.perf.analysis.fake.FakeAdbClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BackgroundNetworkCaptureTest {

    // Shared fixtures
    private val pkg = "com.example.app"
    private val uid = 10234

    private val pmListSuccess = "package:com.example.app uid:10234\n"
    private val netstatsModern = """
        st=0 uid=10234 set=DEFAULT tag=0x0 rx_bytes=12345 tx_bytes=678 rx_packets=10 tx_packets=8
        st=0 uid=10234 set=FOREGROUND tag=0x0 rx_bytes=200 tx_bytes=50 rx_packets=2 tx_packets=1
    """.trimIndent()

    // ── T-2.3: snapshot ────────────────────────────────────────────────────────

    @Test
    fun `snapshot returns non-null with correct bytes when both commands succeed`() = runTest {
        val fake = FakeAdbClient(
            allowFailureHandler = { args ->
                when {
                    args.toList() == listOf("pm", "list", "packages", "-U", pkg) ->
                        AdbShellResult(0, pmListSuccess, "")
                    args.contains("netstats") ->
                        AdbShellResult(0, netstatsModern, "")
                    else ->
                        AdbShellResult(1, "", "unexpected command")
                }
            },
        )
        val snap = BackgroundNetworkCapture(fake).snapshot(pkg)
        assertNotNull(snap)
        assertEquals(12545L, snap.rxBytes)
        assertEquals(728L, snap.txBytes)
    }

    @Test
    fun `snapshot returns null when pm list returns empty output`() = runTest {
        val fake = FakeAdbClient(
            allowFailureHandler = { AdbShellResult(0, "", "") },
        )
        assertNull(BackgroundNetworkCapture(fake).snapshot(pkg))
    }

    @Test
    fun `snapshot returns null when netstats output is blank`() = runTest {
        val fake = FakeAdbClient(
            allowFailureHandler = { args ->
                when {
                    args.toList() == listOf("pm", "list", "packages", "-U", pkg) ->
                        AdbShellResult(0, pmListSuccess, "")
                    args.contains("netstats") ->
                        AdbShellResult(0, "", "")
                    else ->
                        AdbShellResult(1, "", "unexpected command")
                }
            },
        )
        assertNull(BackgroundNetworkCapture(fake).snapshot(pkg))
    }

    @Test
    fun `snapshot sets capturedAtMs to current time after parsing`() = runTest {
        val before = System.currentTimeMillis()
        val fake = FakeAdbClient(
            allowFailureHandler = { args ->
                when {
                    args.toList() == listOf("pm", "list", "packages", "-U", pkg) ->
                        AdbShellResult(0, pmListSuccess, "")
                    args.contains("netstats") ->
                        AdbShellResult(0, netstatsModern, "")
                    else ->
                        AdbShellResult(1, "", "unexpected")
                }
            },
        )
        val snap = BackgroundNetworkCapture(fake).snapshot(pkg)
        val after = System.currentTimeMillis()
        assertNotNull(snap)
        assert(snap.capturedAtMs in before..after) {
            "capturedAtMs ${snap.capturedAtMs} not in [$before, $after]"
        }
    }

    // ── T-2.4: delta ─────────────────────────────────────────────────────────

    @Test
    fun `delta computes positive rx and tx deltas correctly`() {
        val capture = BackgroundNetworkCapture(FakeAdbClient())
        val start = NetworkSnapshot(rxBytes = 1000L, txBytes = 500L, capturedAtMs = 1000L)
        val end = NetworkSnapshot(rxBytes = 2000L, txBytes = 800L, capturedAtMs = 4000L)
        val report = capture.delta(start, end)
        assertEquals(1000L, report.rxBytesDelta)
        assertEquals(300L, report.txBytesDelta)
        assertEquals(3000L, report.durationMs)
    }

    @Test
    fun `delta clamps negative delta to zero on counter reset`() {
        val capture = BackgroundNetworkCapture(FakeAdbClient())
        val start = NetworkSnapshot(rxBytes = 5000L, txBytes = 3000L, capturedAtMs = 0L)
        val end = NetworkSnapshot(rxBytes = 100L, txBytes = 50L, capturedAtMs = 1000L)
        val report = capture.delta(start, end)
        assertEquals(0L, report.rxBytesDelta, "Negative rx delta should be clamped to 0")
        assertEquals(0L, report.txBytesDelta, "Negative tx delta should be clamped to 0")
    }

    @Test
    fun `delta severity is always INFO`() {
        val capture = BackgroundNetworkCapture(FakeAdbClient())
        val start = NetworkSnapshot(0L, 0L, 0L)
        val end = NetworkSnapshot(1000L, 500L, 60000L)
        val report = capture.delta(start, end)
        assertEquals(dev.perf.analysis.model.Severity.INFO, report.severity)
    }
}
