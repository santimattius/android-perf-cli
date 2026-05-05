package dev.perf.analysis.perfetto

import dev.perf.adb.AdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Records a system trace with the on-device `perfetto` binary (when available).
 */
class PerfettoCapture(private val device: AdbClient) {

    /**
     * Blocks for [durationSeconds] while recording.
     */
    suspend fun recordToRemotePath(
        durationSeconds: Int,
        preset: PerfettoTracingPreset = PerfettoTracingPreset.DEFAULT,
        customCategories: List<String>? = null,
    ): PerfettoRecordResult =
        withContext(Dispatchers.IO) {
            if (PerfettoProbe.resolvePath(device).isEmpty()) {
                return@withContext PerfettoRecordResult.Failed(
                    "perfetto binary not found on device (try API 29+ system image or a physical device).",
                )
            }

            val remote = "/data/local/tmp/perf_cli_${System.currentTimeMillis()}.perfetto-trace"

            fun invoke(categories: Array<String>): PerfettoRecordResult {
                val args = arrayListOf<String>().apply {
                    add("perfetto")
                    add("--time")
                    add("${durationSeconds}s")
                    add("--buffer")
                    add("64mb")
                    addAll(categories.asList())
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
                    return PerfettoRecordResult.Failed(
                        "perfetto exited with code ${result.exitCode}$suffix",
                    )
                }
                return PerfettoRecordResult.Ok(remotePath = remote, degradedFromVitalsPreset = false)
            }

            val primary = categoriesForTracing(preset, customCategories)
            val outcome = invoke(primary)
            if (outcome is PerfettoRecordResult.Ok) return@withContext outcome
            if (preset != PerfettoTracingPreset.VITALS) return@withContext outcome

            val fallback = invoke(categoriesForTracing(PerfettoTracingPreset.DEFAULT, null))
            when (fallback) {
                is PerfettoRecordResult.Ok -> PerfettoRecordResult.Ok(
                    remotePath = fallback.remotePath,
                    degradedFromVitalsPreset = true,
                )
                is PerfettoRecordResult.Failed -> fallback
            }
        }
}
