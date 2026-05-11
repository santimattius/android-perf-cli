package dev.perf.analysis.wifi

import dev.perf.adb.AdbShellResult
import dev.perf.analysis.fake.FakeAdbClient
import dev.perf.analysis.model.Severity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WifiScanCaptureTest {

    private val pkg = "com.example.app"

    private val batteryStatsWithScan = """
        Statistics since last unplugged:
          Uid 10123: com.example.app
            Wifi scan: 5 times
            Wake lock SyncManager: 3 times, 1m 30s 200ms realtime
    """.trimIndent()

    private val batteryStatsNoScan = """
        Statistics since last unplugged:
          Uid 10123: com.example.app
            Wake lock SyncManager: 3 times, 1m 30s 200ms realtime
    """.trimIndent()

    // ── T-3.2: snapshot ────────────────────────────────────────────────────────

    @Test
    fun `snapshot returns non-null with correct scan count`() = runTest {
        val fake = FakeAdbClient(
            allowFailureHandler = { AdbShellResult(0, batteryStatsWithScan, "") },
        )
        val snap = WifiScanCapture(fake).snapshot(pkg)
        assertNotNull(snap)
        assertEquals(5, snap.scanCount)
    }

    @Test
    fun `snapshot returns null when batterystats output is blank`() = runTest {
        val fake = FakeAdbClient(
            allowFailureHandler = { AdbShellResult(0, "", "") },
        )
        assertNull(WifiScanCapture(fake).snapshot(pkg))
    }

    @Test
    fun `snapshot returns null when package uid block not found in output`() = runTest {
        val rawNoPackage = """
            Statistics since last unplugged:
              Uid 10099: com.other.app
                Wifi scan: 7 times
        """.trimIndent()
        val fake = FakeAdbClient(
            allowFailureHandler = { AdbShellResult(0, rawNoPackage, "") },
        )
        // WifiScanParser.parse returns null when block not found → snapshot returns null
        assertNull(WifiScanCapture(fake).snapshot(pkg))
    }

    @Test
    fun `snapshot sets capturedAtMs from system clock`() = runTest {
        val before = System.currentTimeMillis()
        val fake = FakeAdbClient(
            allowFailureHandler = { AdbShellResult(0, batteryStatsWithScan, "") },
        )
        val snap = WifiScanCapture(fake).snapshot(pkg)
        val after = System.currentTimeMillis()
        assertNotNull(snap)
        assert(snap.capturedAtMs in before..after) {
            "capturedAtMs ${snap.capturedAtMs} not in [$before, $after]"
        }
    }

    // ── T-3.3: delta ─────────────────────────────────────────────────────────

    @Test
    fun `delta computes positive scan count delta`() {
        val capture = WifiScanCapture(FakeAdbClient())
        val start = WifiScanSnapshot(scanCount = 5, capturedAtMs = 1000L)
        val end = WifiScanSnapshot(scanCount = 12, capturedAtMs = 61000L)
        val report = capture.delta(start, end)
        assertEquals(7, report.scanCountDelta)
        assertEquals(60000L, report.durationMs)
    }

    @Test
    fun `delta clamps negative scan count delta to zero on counter reset`() {
        val capture = WifiScanCapture(FakeAdbClient())
        val start = WifiScanSnapshot(scanCount = 12, capturedAtMs = 0L)
        val end = WifiScanSnapshot(scanCount = 5, capturedAtMs = 60000L)
        val report = capture.delta(start, end)
        assertEquals(0, report.scanCountDelta, "Negative delta should be clamped to 0")
    }

    @Test
    fun `delta severity is always INFO`() {
        val capture = WifiScanCapture(FakeAdbClient())
        val start = WifiScanSnapshot(scanCount = 0, capturedAtMs = 0L)
        val end = WifiScanSnapshot(scanCount = 10, capturedAtMs = 60000L)
        val report = capture.delta(start, end)
        assertEquals(Severity.INFO, report.severity)
    }
}
