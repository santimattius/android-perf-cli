package dev.perf.report

import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.ColdStartMeasurement
import dev.perf.analysis.model.ExitSignalReport
import dev.perf.analysis.model.startup.StartupReport
import dev.perf.analysis.model.startup.StartupStats
import dev.perf.analysis.model.MemoryLeak
import dev.perf.analysis.model.PotentialAnrHint
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StrictModeViolation
import dev.perf.analysis.model.ThresholdBreach
import dev.perf.analysis.model.WakelockReport
import dev.perf.report.source.SourceLinker
import dev.perf.report.source.StackFrameParser
import dev.perf.report.vitals.VitalsMetric
import dev.perf.report.vitals.VitalsPlaybook
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

    fun write(report: PerfReport, destination: File, extras: ReportRenderExtras = ReportRenderExtras()) {
        destination.writeText(render(report, extras))
    }

    fun render(report: PerfReport, extras: ReportRenderExtras = ReportRenderExtras()): String = buildString {
        appendHeader(report, extras)
        appendSidecarEvidence(report)

        if (!report.hasIssues) {
            appendLine("> No performance issues detected. Ship it.")
            report.startup?.let { appendStartupSummary(it) }
                ?: report.coldStart?.let { appendColdStartSummary(it) }
            report.wakelockReport?.let { appendWakelocksSection(it) }
            report.exitSignalReport?.takeIf { it.hasExits }?.let { appendExitSignalsSection(it) }
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
            appendAndroidCliSection(extras)
            return@buildString
        }

        if (report.thresholdBreaches.isNotEmpty()) appendThresholdBreaches(report.thresholdBreaches)
        report.exitSignalReport?.takeIf { it.hasExits }?.let { appendExitSignalsSection(it) }
        report.wakelockReport?.let { appendWakelocksSection(it) }
        report.startup?.let { appendStartupSection(it) }
            ?: report.coldStart?.let { appendColdStartSection(it) }
        if (report.memoryLeaks.isNotEmpty()) {
            appendLeaks(report.memoryLeaks)
            VitalsPlaybook.memoryLeaksSection().forEach { appendLine(it) }
        }
        if (report.strictModeViolations.isNotEmpty()) {
            appendStrictMode(report.strictModeViolations)
            VitalsPlaybook.strictModeSection(
                report.strictModeViolations.map { it.violationType }.toSet(),
            ).forEach { appendLine(it) }
            appendLine(VitalsPlaybook.sourceFooter(VitalsMetric.STRICT_MODE))
            appendLine()
        }
        if (report.potentialAnrHints.any { it.severity != Severity.INFO }) {
            val filtered = report.potentialAnrHints.filter { it.severity != Severity.INFO }
            appendPotentialAnr(filtered)
            VitalsPlaybook.potentialAnrSection(filtered.map { it.hintType }.toSet()).forEach { appendLine(it) }
            appendLine(VitalsPlaybook.sourceFooter(VitalsMetric.ANR))
            appendLine()
        }
        if (report.renderingIncident.severity != Severity.INFO) {
            appendRendering(report.renderingIncident)
            VitalsPlaybook.renderingSection().forEach { appendLine(it) }
        }
        appendPerfettoFooter(report)
        VitalsPlaybook.playConsoleLimitationsBanner().forEach { appendLine(it) }
        appendAndroidCliSection(extras)
    }

    /** Screenshots / mem snapshots when engines ran without top-level severity issues. */
    private fun StringBuilder.appendSidecarEvidence(report: PerfReport) {
        if (report.procSnapshot != null || report.screenshotArtifacts.isNotEmpty()) {
            appendEvidenceSections(report)
        }
    }

    private fun StringBuilder.appendStartupSummary(sr: StartupReport) {
        appendLine()
        appendLine("### Startup (measured)")
        appendLine()
        appendLine("| Kind | P50 TTID | P50 TTFD | Severity |")
        appendLine("|------|----------|----------|----------|")
        appendStartupStatsRow("Cold", sr.cold)
        appendStartupStatsRow("Warm", sr.warm)
        appendStartupStatsRow("Hot", sr.hot)
        appendLine()
        appendLine("**Parsed:** ${if (sr.parsedOk) "yes" else "no"}")
        appendLine()
        appendLine("_${sr.message}_")
        appendLine()
        VitalsPlaybook.startupSection().forEach { appendLine(it) }
        appendLine(VitalsPlaybook.sourceFooter(VitalsMetric.COLD_START))
        appendLine()
    }

    private fun StringBuilder.appendStartupSection(sr: StartupReport) {
        appendLine("## Startup")
        appendLine()
        appendLine("| Parsed OK | ${if (sr.parsedOk) "yes" else "no"} |")
        appendLine("| Overall | ${sr.overallSeverity.badge()} |")
        appendLine()
        appendLine(sr.message)
        appendLine()
        appendStartupKindBlock("Cold", sr.cold)
        appendStartupKindBlock("Warm", sr.warm)
        appendStartupKindBlock("Hot", sr.hot)
        VitalsPlaybook.startupSection().forEach { appendLine(it) }
        appendLine(VitalsPlaybook.sourceFooter(VitalsMetric.COLD_START))
        appendLine()
    }

    private fun StringBuilder.appendStartupStatsRow(label: String, stats: StartupStats?) {
        if (stats == null) return
        appendLine(
            "| $label | ${stats.p50Ms?.toString() ?: "—"} ms | " +
                "${stats.ttfdP50Ms?.toString() ?: "—"} ms | ${stats.severity.badge()} |",
        )
    }

    private fun StringBuilder.appendStartupKindBlock(label: String, stats: StartupStats?) {
        if (stats == null) return
        appendLine("### $label")
        appendLine()
        appendLine("| P50 TTID | P95 TTID | P50 TTFD | Severity |")
        appendLine("|----------|----------|----------|----------|")
        appendLine(
            "| ${stats.p50Ms?.toString() ?: "—"} ms | ${stats.p95Ms?.toString() ?: "—"} ms | " +
                "${stats.ttfdP50Ms?.toString() ?: "—"} ms | ${stats.severity.badge()} |",
        )
        appendLine()
        appendLine(stats.message)
        appendLine()
    }

    private fun StringBuilder.appendColdStartSummary(cs: ColdStartMeasurement) {
        appendLine()
        appendLine("### Cold start (measured)")
        appendLine()
        appendLine("| Field | Value |")
        appendLine("|-------|-------|")
        appendLine("| TotalTime | ${cs.totalTimeMs?.toString() ?: "—"} ms |")
        appendLine("| Status | ${cs.status ?: "—"} |")
        appendLine("| Parsed | ${if (cs.parsedOk) "yes" else "no"} |")
        appendLine()
        appendLine("_${cs.message}_")
        appendLine()
        VitalsPlaybook.coldStartSection().forEach { appendLine(it) }
        appendLine(VitalsPlaybook.sourceFooter(VitalsMetric.COLD_START))
        appendLine()
    }

    private fun StringBuilder.appendColdStartSection(cs: ColdStartMeasurement) {
        appendLine("## Cold start")
        appendLine()
        appendLine("| Field | Value |")
        appendLine("|-------|-------|")
        appendLine("| Severity | ${cs.severity.badge()} |")
        appendLine("| TotalTime | ${cs.totalTimeMs?.toString() ?: "—"} ms |")
        appendLine("| WaitTime | ${cs.waitTimeMs?.toString() ?: "—"} ms |")
        appendLine("| LaunchState | ${cs.launchState ?: "—"} |")
        appendLine("| Status | ${cs.status ?: "—"} |")
        appendLine()
        appendLine(cs.message)
        appendLine()
        VitalsPlaybook.coldStartSection().forEach { appendLine(it) }
        appendLine(VitalsPlaybook.sourceFooter(VitalsMetric.COLD_START))
        appendLine()
    }

    private fun StringBuilder.appendAndroidCliSection(extras: ReportRenderExtras) {
        val snippet = extras.androidCliDocsSnippet ?: return
        VitalsPlaybook.androidCliDocsIntro().forEach { appendLine(it) }
        appendLine("```text")
        appendLine(snippet)
        appendLine("```")
        appendLine()
    }

    private fun StringBuilder.appendExitSignalsSection(exitReport: ExitSignalReport) {
        appendLine("## Exit Signals")
        appendLine()
        appendLine("_Confirmed user-perceived exits during this session. Any entry is CRITICAL._")
        appendLine()
        appendLine("| Type | Package | Message | Raw line |")
        appendLine("|------|---------|---------|----------|")
        exitReport.events.forEach { event ->
            val truncatedRaw = event.rawLine.take(120).replace("|", "\\|")
            appendLine("| ${event.exitType.name} | ${event.packageName} | ${event.message} | $truncatedRaw |")
        }
        appendLine()
        VitalsPlaybook.exitSignalSection().forEach { appendLine(it) }
        appendLine(VitalsPlaybook.sourceFooter(VitalsMetric.EXIT_INFO))
        appendLine()
        appendLine(
            "_Note: Android Vitals bad-behaviour thresholds are >0.47% ANR rate and >1.09% crash rate " +
                "per daily sessions. Rate computation requires fleet aggregation — a single run cannot " +
                "determine whether these thresholds are breached._",
        )
        appendLine()
    }

    private fun StringBuilder.appendWakelocksSection(w: WakelockReport) {
        appendLine("## Wake Locks")
        appendLine()
        appendLine("| Name | Count | Duration (ms) |")
        appendLine("|------|-------|-----------------|")
        if (w.entries.isEmpty()) {
            appendLine("| — | 0 | 0 |")
        } else {
            w.entries.forEach { e ->
                val safeName = e.name.replace("|", "\\|")
                appendLine("| `$safeName` | ${e.count} | ${e.totalDurationMs} |")
            }
        }
        appendLine()
        appendLine("_Total ${w.totalDurationMs} ms — ${w.severity.badge()}_")
        appendLine()
        appendLine("> Play Console bad-behaviour threshold: >5% of daily sessions with a partial wake lock held >1 h.")
        appendLine()
        appendLine(VitalsPlaybook.sourceFooter(VitalsMetric.EXCESSIVE_WAKELOCKS))
        appendLine()
    }

    private fun StringBuilder.appendThresholdBreaches(breaches: List<ThresholdBreach>) {
        appendLine("## Threshold breaches")
        appendLine()
        appendLine("_Scenario SLOs exceeded — this run is a failure (exit code `1`)._")
        appendLine()
        breaches.forEach { b ->
            appendLine("- **${b.severity.badge()}** ${b.message}")
        }
        appendLine()
    }

    private fun StringBuilder.appendPerfettoFooter(report: PerfReport) {
        report.perfettoTraceFile?.let {
            appendLine("## Perfetto trace")
            appendLine()
            appendLine(
                if (report.perfettoDegradedFromVitalsPreset) {
                    "_Note:_ `vitals` preset categories failed on this device — trace captured with baseline categories.\n"
                } else {
                    ""
                },
            )
            appendLine(
                "Open `${it.absolutePath}` in [Perfetto UI](https://ui.perfetto.dev). " +
                    "Review **sched / gfx / binder** timelines against [slow rendering](https://developer.android.com/topic/performance/vitals/render) and [startup](https://developer.android.com/topic/performance/vitals/launch-time) Vitals docs.",
            )
            appendLine()
            VitalsPlaybook.perfettoAnalysisHints().forEach { appendLine(it) }
        }
        report.perfettoMessage?.let { msg ->
            appendLine("## Perfetto")
            appendLine()
            appendLine("> $msg")
            appendLine()
        }
    }

    private fun StringBuilder.appendEvidenceSections(report: PerfReport) {
        report.procSnapshot?.let { p ->
            appendLine("## Process snapshot (`meminfo` / threads)")
            appendLine()
            p.threadCount?.let {
                appendLine("**Threads (from /proc/.../status):** `$it`")
                appendLine()
            }
            VitalsPlaybook.procSnapshotGuidance().forEach { appendLine(it) }
            appendLine("```text")
            appendLine(p.meminfoSnippet.lines().take(80).joinToString("\n").trimEnd())
            appendLine("```")
            appendLine()
            VitalsPlaybook.memoryStressSection().forEach { appendLine(it) }
        }

        if (report.screenshotArtifacts.isNotEmpty()) {
            appendLine("## Screenshots")
            appendLine()
            report.screenshotArtifacts.forEach { rel ->
                appendLine("- `./$rel`")
            }
            appendLine()
            appendLine(VitalsPlaybook.screenshotEvidenceGuidance())
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

    private fun StringBuilder.appendHeader(report: PerfReport, extras: ReportRenderExtras) {
        val timestamp = Instant.now()
            .atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z"))

        appendLine("# Perf Report — `${report.packageName}`")
        appendLine()
        appendLine("| Field | Value |")
        appendLine("|-------|-------|")
        appendLine("| Package | `${report.packageName}` |")
        val genIso =
            extras.sessionMeta?.generatedAtIso
                ?: java.time.format.DateTimeFormatter.ISO_INSTANT.format(Instant.now())
        appendLine("| Report generated (UTC / ISO‑8601) | `$genIso` |")
        extras.sessionMeta?.let { m ->
            appendLine("| Monitoring window | `${m.monitorWindowStartIso}` → `${m.monitorWindowEndIso}` |")
            appendLine("| Scenario | ${m.scenarioLabel ?: "—"} |")
            appendLine("| Full mode | ${if (m.fullMode) "yes" else "no"} |")
        }
        if (extras.sessionMeta == null) {
            appendLine("| Report generated (local clock) | $timestamp |")
        }
        appendLine("| Overall Severity | ${report.overallSeverity.badge()} |")
        report.startup?.let { su ->
            su.cold?.p50Ms?.let { appendLine("| Cold start P50 (TTID) | $it ms |") }
            su.warm?.p50Ms?.let { appendLine("| Warm start P50 (TTID) | $it ms |") }
            su.hot?.p50Ms?.let { appendLine("| Hot start P50 (TTID) | $it ms |") }
        }
            ?: report.coldStart?.let { cs ->
                val t = cs.totalTimeMs?.let { "$it ms" } ?: "—"
                appendLine("| Cold start (TotalTime) | $t |")
            }
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
        appendLine(VitalsPlaybook.sourceFooter(VitalsMetric.SLOW_RENDERING))
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
