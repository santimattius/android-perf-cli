package dev.perf.analysis.wakelock

import dev.perf.adb.AdbClient
import dev.perf.analysis.model.WakelockReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Captures partial wake-lock stats from `dumpsys batterystats`.
 *
 * [reset] clears system-wide batterystats — destructive; callers must gate on user opt-in.
 */
class WakelockCapture(private val device: AdbClient) {

    fun reset(@Suppress("UNUSED_PARAMETER") packageName: String) {
        device.shell("dumpsys", "batterystats", "--reset")
    }

    suspend fun capture(packageName: String): WakelockReport? = withContext(Dispatchers.IO) {
            val res = device.shellAllowFailure("dumpsys", "batterystats", packageName)
            if (res.exitCode != 0 || res.stdout.isBlank()) {
                return@withContext null
            }
            WakelockParser.parse(res.stdout, packageName)
        }
}
