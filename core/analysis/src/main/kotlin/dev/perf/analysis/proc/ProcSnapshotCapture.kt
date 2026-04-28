package dev.perf.analysis.proc

import dev.perf.adb.AdbClient
import dev.perf.analysis.model.ProcSnapshotSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Parses `dumpsys meminfo` and `/proc/[pid]/status` for coarse Vitals‑style context.
 */
class ProcSnapshotCapture(private val device: AdbClient) {

    suspend fun capture(packageName: String): ProcSnapshotSummary = withContext(Dispatchers.IO) {
        val pidLine = device.shellAllowFailure("pidof", packageName).stdout.trim().split(Regex("\\s+")).firstOrNull()
        val threadCount = pidLine?.let { pid ->
            threadsRegex.find(device.shellAllowFailure("cat", "/proc/$pid/status").stdout)?.groupValues?.get(1)?.toIntOrNull()
        }

        val memFull = device.shellAllowFailure("dumpsys", "meminfo", packageName).stdout
        ProcSnapshotSummary(
            meminfoSnippet = memFull.lineSequence().take(48).joinToString("\n").trim(),
            threadCount = threadCount,
        )
    }

    companion object {
        private val threadsRegex = Regex("""(?i)^Threads:\s+(\d+)\s*$""", RegexOption.MULTILINE)
    }
}
