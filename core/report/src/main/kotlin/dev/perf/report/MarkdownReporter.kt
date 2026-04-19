package dev.perf.report

import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.MemoryLeak
import dev.perf.analysis.model.PotentialAnrHint
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StrictModeViolation
import dev.perf.report.source.SourceLinker
import dev.perf.report.source.StackFrameParser
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * "Calm Tech" Markdown reporter: only emits sections when there are problems.
 *
 * Design decision: [sourceLinker] is optional (null = no source links). This
 * lets the reporter work in environments where the project root is not
 * available (CI artifact inspection, remote runs) without crashing or requiring
 * a dummy implementation.
 *
 * Design decision: Markdown is built with StringBuilder — no template engine.
 * Template engines load resources from the classpath with reflection, which
 * breaks GraalVM Native Image. buildString {} produces identical output with
 * zero runtime dependencies.
 */
class MarkdownReporter(private val sourceLinker: SourceLinker? = null) {

    fun write(report: PerfReport, destination: File) {
        destination.writeText(render(report))
    }

    fun render(report: PerfReport): String = buildString {
        appendHeader(report)

        if (!report.hasIssues) {
            appendLine("> No performance issues detected. Ship it.")
            report.perfettoTraceFile?.let {
                appendLine()
                appendLine("### Perfetto trace")
                appendLine()
                appendLine("A system trace was captured: `${it.absolutePath}` — open in [Perfetto UI](https://ui.perfetto.dev).")
            }
            report.perfettoMessage?.let { msg ->
                appendLine()
                appendLine("> Perfetto: $msg")
            }
            return@buildString
        }

        if (report.memoryLeaks.isNotEmpty()) appendLeaks(report.memoryLeaks)
        if (report.strictModeViolations.isNotEmpty()) appendStrictMode(report.strictModeViolations)
        if (report.potentialAnrHints.any { it.severity != Severity.INFO }) {
            appendPotentialAnr(report.potentialAnrHints.filter { it.severity != Severity.INFO })
        }
        if (report.renderingIncident.severity != Severity.INFO) appendRendering(report.renderingIncident)
        appendPerfettoFooter(report)
    }

    private fun StringBuilder.appendPerfettoFooter(report: PerfReport) {
        report.perfettoTraceFile?.let {
            appendLine("## Perfetto trace")
            appendLine()
            appendLine("Open `${it.absolutePath}` in [Perfetto UI](https://ui.perfetto.dev) for scheduling, frames, and binder detail.")
            appendLine()
        }
        report.perfettoMessage?.let { msg ->
            appendLine("## Perfetto")
            appendLine()
            appendLine("> $msg")
            appendLine()
        }
    }

    private fun StringBuilder.appendPotentialAnr(hints: List<PotentialAnrHint>) {
        appendLine("## Potential ANR risk (heuristics)")
        appendLine()
        appendLine("_Logcat signals such as skipped frames — not a confirmed ANR._")
        appendLine()
        hints.take(30).forEach { h ->
            appendLine("- **${h.severity.badge()}** [${h.hintType}] ${h.message}")
            appendLine()
            appendLine("  <details><summary>Raw log line</summary>")
            appendLine()
            appendLine("  ```")
            appendLine("  ${h.detail}")
            appendLine("  ```")
            appendLine()
            appendLine("  </details>")
            appendLine()
        }
        if (hints.size > 30) {
            appendLine("_${hints.size - 30} more signals omitted._")
            appendLine()
        }
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private fun StringBuilder.appendHeader(report: PerfReport) {
        val timestamp = Instant.now()
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))

        appendLine("# Perf Report — `${report.packageName}`")
        appendLine()
        appendLine("| Field | Value |")
        appendLine("|-------|-------|")
        appendLine("| Package | `${report.packageName}` |")
        appendLine("| Date | $timestamp |")
        appendLine("| Overall Severity | ${report.overallSeverity.badge()} |")
        appendLine()
    }

    private fun StringBuilder.appendLeaks(leaks: List<MemoryLeak>) {
        appendLine("## Memory Leaks")
        appendLine()
        leaks.forEach { leak ->
            appendLine("### ${leak.severity.badge()} `${leak.leakingObject}`")
            appendLine()
            appendLine("- **Retained heap**: ~${leak.retainedBytes / 1024} KB")
            if (leak.shortestPath.isNotBlank()) {
                appendLine("- **Shortest path**: `${leak.shortestPath}`")
            }
            appendLine()
        }
    }

    private fun StringBuilder.appendStrictMode(violations: List<StrictModeViolation>) {
        appendLine("## StrictMode Violations")
        appendLine()

        violations.groupBy { it.violationType }.forEach { (type, group) ->
            appendLine("### ${type.label()} (${group.size} occurrence${if (group.size > 1) "s" else ""})")
            appendLine()
            group.take(3).forEach { v ->
                appendLine("<details>")
                appendLine("<summary><code>${v.threadName}</code> — ${v.message}</summary>")
                appendLine()
                appendStackTrace(v.stackTrace)
                appendLine()
                appendLine("</details>")
                appendLine()
            }
            if (group.size > 3) {
                appendLine("_${group.size - 3} more occurrences omitted._")
                appendLine()
            }
        }
    }

    /**
     * Renders the stack trace as a Markdown list so individual frames can
     * carry clickable IDE links. Falls back to a plain code block when
     * [sourceLinker] is null or no frames resolve to local files.
     *
     * Design decision: we use a Markdown list (not a fenced code block) for
     * resolved traces because code blocks don't render links in GitHub/GitLab.
     * If zero frames resolve, we keep the code block — it's more readable for
     * unresolved traces than a plain list of strings.
     */
    private fun StringBuilder.appendStackTrace(stackTrace: String) {
        val linker = sourceLinker
        if (linker == null) {
            appendLine("```")
            appendLine(stackTrace)
            appendLine("```")
            return
        }

        val lines = stackTrace.lines().filter { it.isNotBlank() }
        val rendered = lines.map { line ->
            val frame = StackFrameParser.parse(line)
            val url = frame?.let { linker.linkFor(it) }
            when {
                url != null -> "- `${line.trim()}` — [open in IDE]($url)"
                else -> "- `${line.trim()}`"
            }
        }

        // Only switch to list format if at least one frame resolved
        if (rendered.any { it.contains("open in IDE") }) {
            rendered.forEach { appendLine(it) }
        } else {
            appendLine("```")
            appendLine(stackTrace)
            appendLine("```")
        }
    }

    private fun StringBuilder.appendRendering(incident: RenderingIncident) {
        appendLine("## Rendering Performance")
        appendLine()
        appendLine("| Metric | Value |")
        appendLine("|--------|-------|")
        appendLine("| Total frames | ${incident.totalFrames} |")
        appendLine("| Janky frames (>16ms) | **${incident.jankyFrames}** |")
        appendLine("| Frozen frames (>700ms) | **${incident.frozenFrames}** |")
        appendLine("| p90 | ${incident.p90Ms.fmt()} ms |")
        appendLine("| p95 | ${incident.p95Ms.fmt()} ms |")
        appendLine("| p99 | ${incident.p99Ms.fmt()} ms |")
        appendLine()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun Severity.badge() = when (this) {
        Severity.CRITICAL -> "🔴 CRITICAL"
        Severity.WARNING -> "🟡 WARNING"
        Severity.INFO -> "🟢 INFO"
    }

    private fun StrictModeViolation.ViolationType.label() = when (this) {
        StrictModeViolation.ViolationType.DISK_READ -> "Disk Read on Main Thread"
        StrictModeViolation.ViolationType.DISK_WRITE -> "Disk Write on Main Thread"
        StrictModeViolation.ViolationType.NETWORK -> "Network on Main Thread"
        StrictModeViolation.ViolationType.UNKNOWN -> "Unknown Violation"
    }

    private fun Double.fmt() = "%.2f".format(this)
}
