package dev.perf.analysis.network

import dev.perf.adb.AdbClient
import dev.perf.analysis.model.BackgroundNetworkReport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Total UID network bytes. Returned at session start (baseline) and end. */
data class NetworkSnapshot(val rxBytes: Long, val txBytes: Long, val capturedAtMs: Long)

class BackgroundNetworkCapture(private val device: AdbClient) {

    suspend fun snapshot(packageName: String): NetworkSnapshot? = withContext(Dispatchers.IO) {
        val uid = resolveUid(packageName) ?: return@withContext null
        val res = device.shellAllowFailure("dumpsys", "netstats", "--uid", uid.toString())
        if (res.exitCode != 0 || res.stdout.isBlank()) return@withContext null
        NetstatsParser.parse(res.stdout, uid)?.copy(capturedAtMs = System.currentTimeMillis())
    }

    fun delta(start: NetworkSnapshot, end: NetworkSnapshot): BackgroundNetworkReport =
        BackgroundNetworkReport(
            rxBytesDelta = (end.rxBytes - start.rxBytes).coerceAtLeast(0L),
            txBytesDelta = (end.txBytes - start.txBytes).coerceAtLeast(0L),
            durationMs = (end.capturedAtMs - start.capturedAtMs).coerceAtLeast(0L),
        )

    internal fun resolveUid(packageName: String): Int? {
        val res = device.shellAllowFailure("pm", "list", "packages", "-U", packageName)
        if (res.exitCode != 0) return null
        return Regex("""package:\Q$packageName\E\s+uid:(\d+)""").find(res.stdout)
            ?.groupValues?.get(1)?.toIntOrNull()
    }
}
