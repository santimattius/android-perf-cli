package dev.perf.analysis.wifi

import dev.perf.adb.AdbClient
import dev.perf.analysis.model.WifiScanReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Wi-Fi scan count at a single point in time. */
data class WifiScanSnapshot(val scanCount: Int, val capturedAtMs: Long)

/**
 * Captures the Wi-Fi scan count from `dumpsys batterystats` for a given package.
 *
 * Must be called AFTER [dev.perf.analysis.wakelock.WakelockCapture.reset] — taking
 * the baseline before the reset causes the scan counter to be wiped, producing a
 * zero delta regardless of actual activity.
 *
 * TODO: WifiScanCapture duplicates the `dumpsys batterystats` ADB call from
 * WakelockCapture. See design §5 for consolidation rationale (deferred).
 */
class WifiScanCapture(private val device: AdbClient) {

    suspend fun snapshot(packageName: String): WifiScanSnapshot? = withContext(Dispatchers.IO) {
        val res = device.shellAllowFailure("dumpsys", "batterystats", packageName)
        if (res.exitCode != 0 || res.stdout.isBlank()) return@withContext null
        WifiScanParser.parse(res.stdout, packageName)?.let { count ->
            WifiScanSnapshot(count, System.currentTimeMillis())
        }
    }

    fun delta(start: WifiScanSnapshot, end: WifiScanSnapshot): WifiScanReport =
        WifiScanReport(
            scanCountDelta = (end.scanCount - start.scanCount).coerceAtLeast(0),
            durationMs = (end.capturedAtMs - start.capturedAtMs).coerceAtLeast(0L),
        )
}
