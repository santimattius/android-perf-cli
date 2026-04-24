package dev.perf.analysis.perfetto

import dev.perf.adb.AdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Records a system trace with the on-device `perfetto` binary (when available).
 *
 * Invocation follows Android’s documented **light mode** shape (data sources
 * as event specifiers, `--out` trace file):
 * [developer.android.com/tools/perfetto](https://developer.android.com/tools/perfetto)
 *
 * Open traces in [Perfetto UI](https://ui.perfetto.dev). This module does not
 * parse Perfetto’s protobuf format — it only records and pulls the file.
 * For full configs (normal mode, `heapprofd`, etc.), use a config file and
 * `adb shell perfetto --config …` as described on that page.
 */
class PerfettoCapture(private val device: AdbClient) {

    /**
     * Blocks for [durationSeconds] while recording.
     *
     * On failure, [PerfettoRecordResult.Failed] includes stderr from `perfetto`
     * when the device returns it (truncated for safe logging).
     */
    suspend fun recordToRemotePath(durationSeconds: Int): PerfettoRecordResult =
        withContext(Dispatchers.IO) {
            if (PerfettoProbe.resolvePath(device).isEmpty()) {
                return@withContext PerfettoRecordResult.Failed(
                    "perfetto binary not found on device (try API 29+ system image or a physical device).",
                )
            }
            val remote = "/data/local/tmp/perf_cli_${System.currentTimeMillis()}.perfetto-trace"
            // Light-mode style: https://developer.android.com/tools/perfetto#syntax
            val args = arrayListOf<String>().apply {
                add("perfetto")
                add("--time")
                add("${durationSeconds}s")
                add("--buffer")
                add("64mb")
                addAll(PerfettoLightModeCategories.DEFAULT.toList())
                add("--out")
                add(remote)
            }.toTypedArray()

            val result = device.shellAllowFailure(*args)
            if (!result.ok) {
                val detail = (result.stderr.ifBlank { result.stdout }).trim()
                    .lineSequence()
                    .take(4)
                    .joinToString(" ")
                    .take(400)
                val suffix = if (detail.isNotEmpty()) ": $detail" else ""
                return@withContext PerfettoRecordResult.Failed(
                    "perfetto exited with code ${result.exitCode}$suffix",
                )
            }
            PerfettoRecordResult.Ok(remote)
        }
}
