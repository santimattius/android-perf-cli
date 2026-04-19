package dev.perf.analysis.perfetto

import dev.perf.adb.AdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Records a system trace with the on-device `perfetto` binary (when available).
 *
 * Open traces in [Perfetto UI](https://ui.perfetto.dev). The JVM CLI does not
 * parse Perfetto’s protobuf format — it only records and pulls the file.
 */
class PerfettoCapture(private val device: AdbClient) {

    suspend fun isAvailable(): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val path = device.shell("sh", "-c", "command -v perfetto 2>/dev/null || true").trim()
            path.isNotEmpty() && path.startsWith("/")
        }.getOrDefault(false)
    }

    /**
     * Blocks for [durationSeconds] while recording. Returns remote path for [AdbClient.pull],
     * or null if perfetto is missing or the command failed.
     */
    suspend fun recordToRemotePath(durationSeconds: Int): String? = withContext(Dispatchers.IO) {
        if (!isAvailable()) return@withContext null
        val remote = "/data/local/tmp/perf_cli_${System.currentTimeMillis()}.perfetto-trace"
        val args = arrayOf(
            "perfetto",
            "-o", remote,
            "-t", "${durationSeconds}s",
            "-b", "64mb",
            "sched", "freq", "idle", "am", "wm", "gfx", "view", "binder_driver",
            "dalvik", "input", "res", "memory",
        )
        val ok = runCatching { device.shell(*args) }.isSuccess
        if (!ok) return@withContext null
        remote
    }
}
