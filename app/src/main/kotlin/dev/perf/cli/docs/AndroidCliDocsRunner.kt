package dev.perf.cli.docs

import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.Severity
import java.util.concurrent.TimeUnit

/**
 * Runs `android docs search` on the **host** (not the device) to attach
 * authoritative snippets to the Markdown report.
 *
 * Design decision: never throws — failures return null so `perf run` always completes.
 */
class AndroidCliDocsRunner(
    private val resolveTimeoutMs: Long = 3_000L,
    private val searchTimeoutMs: Long = 18_000L,
) {

    fun searchForReport(report: PerfReport): String? {
        val android = findAndroidBinary() ?: return null
        val query = buildQuery(report)
        return runSearch(android, query)
    }

    internal fun buildQuery(report: PerfReport): String {
        val parts = mutableListOf<String>()
        parts += "Android Vitals performance"
        if (report.strictModeViolations.isNotEmpty()) {
            parts += "StrictMode disk network main thread"
        }
        if (report.potentialAnrHints.any { it.severity != Severity.INFO }) {
            parts += "ANR responsiveness main thread"
        }
        if (report.renderingIncident.severity != Severity.INFO) {
            parts += "slow rendering jank frames"
        }
        if (report.memoryLeaks.isNotEmpty()) {
            parts += "memory leaks heap"
        }
        if (report.startup != null || report.coldStart != null) {
            parts += "app startup time cold start"
        }
        return parts.joinToString(" ").take(280)
    }

    private fun findAndroidBinary(): String? = runCatching {
        val win = System.getProperty("os.name")?.lowercase()?.contains("windows") == true
        val pb = if (win) {
            ProcessBuilder("cmd.exe", "/c", "where android")
        } else {
            ProcessBuilder("/bin/sh", "-c", "command -v android")
        }.redirectErrorStream(true)
        val p = pb.start()
        if (!p.waitFor(resolveTimeoutMs, TimeUnit.MILLISECONDS)) {
            p.destroyForcibly()
            return@runCatching null
        }
        if (p.exitValue() != 0) return@runCatching null
        p.inputStream.bufferedReader().readText().trim().lines().firstOrNull { it.isNotEmpty() }
    }.getOrNull()

    private fun runSearch(androidPath: String, query: String): String? = runCatching {
        val pb = ProcessBuilder(androidPath, "docs", "search", query)
            .redirectErrorStream(true)
        val p = pb.start()
        if (!p.waitFor(searchTimeoutMs, TimeUnit.MILLISECONDS)) {
            p.destroyForcibly()
            return@runCatching "(timeout after ${searchTimeoutMs}ms)"
        }
        val text = p.inputStream.bufferedReader().readText().trim()
        if (text.isEmpty()) return@runCatching null
        text.lines().take(45).joinToString("\n")
    }.getOrNull()
}
