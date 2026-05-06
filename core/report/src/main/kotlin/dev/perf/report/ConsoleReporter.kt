package dev.perf.report

import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.table.table
import com.github.ajalt.mordant.terminal.Terminal
import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.MemoryLeak
import dev.perf.analysis.model.PotentialAnrHint
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StrictModeViolation
import dev.perf.analysis.model.ColdStartMeasurement
import dev.perf.analysis.model.startup.StartupReport
import dev.perf.analysis.model.ThresholdBreach
import dev.perf.analysis.model.WakelockReport
import dev.perf.report.source.SourceLinker
import dev.perf.report.vitals.VitalsPlaybook
import dev.perf.report.source.StackFrameParser

/**
 * Renders a styled summary to stdout using Mordant.
 *
 * Design decision: [sourceLinker] is optional — when present, resolved frames
 * in the StrictMode detail block are rendered as OSC 8 terminal hyperlinks
 * (clickable in iTerm2, kitty, Ghostty, WezTerm). When absent or when the
 * terminal doesn't support hyperlinks, the frame is printed as plain text.
 * Mordant handles the capability detection transparently.
 *
 * Design decision: we print nothing when there are no issues ("calm tech").
 * The caller decides when to invoke this — the reporter does not check
 * whether it "should" print; it always prints what it receives.
 */
class ConsoleReporter(
    private val terminal: Terminal = Terminal(),
    private val sourceLinker: SourceLinker? = null,
) {

    fun print(report: PerfReport, extras: ReportRenderExtras = ReportRenderExtras()) {
        if (!report.hasIssues) {
            terminal.println(green("✔ No performance issues detected."))
            report.startup?.let { printStartup(it) }
                ?: report.coldStart?.let { printColdStart(it) }
            report.wakelockReport?.let { printWakelocks(it) }
            printPerfetto(report)
            printAndroidCliHint(extras)
            return
        }

        terminal.println()
        terminal.println(bold("Perf Report — ${report.packageName}"))
        terminal.println(severityLine(report.overallSeverity))
        terminal.println()

        if (report.thresholdBreaches.isNotEmpty()) printThresholdBreaches(report.thresholdBreaches)
        report.startup?.let { printStartup(it) }
            ?: report.coldStart?.let { printColdStart(it) }
        report.wakelockReport?.let { printWakelocks(it) }
        if (report.memoryLeaks.isNotEmpty()) {
            printLeaks(report.memoryLeaks)
            terminal.println(dim("  Vitals: ${VitalsPlaybook.VITALS_HUB}"))
        }
        if (report.strictModeViolations.isNotEmpty()) {
            printStrictMode(report.strictModeViolations)
            terminal.println(dim("  StrictMode docs: https://developer.android.com/reference/android/os/StrictMode"))
        }
        val anrRisk = report.potentialAnrHints.filter { it.severity != Severity.INFO }
        if (anrRisk.isNotEmpty()) {
            printPotentialAnr(anrRisk)
            terminal.println(dim("  ANRs: https://developer.android.com/topic/performance/vitals/anr"))
        }
        if (report.renderingIncident.severity != Severity.INFO) {
            printRendering(report.renderingIncident)
            terminal.println(dim("  Render vitals: https://developer.android.com/topic/performance/vitals/render"))
        }
        printPerfetto(report)
        printAndroidCliHint(extras)
    }

    private fun printStartup(sr: StartupReport) {
        terminal.println(bold("  Startup (${sr.overallSeverity.name})"))
        terminal.println(dim("    ${sr.message}"))
        terminal.println(
            dim("    Launch: https://developer.android.com/topic/performance/vitals/launch-time"),
        )
        terminal.println()
    }

    private fun printWakelocks(w: WakelockReport) {
        terminal.println()
        terminal.println(
            bold(
                "  Wake locks (${w.entries.size} locks, %.1f s total, %s)"
                    .format(w.totalDurationMs / 1000.0, w.severity.name),
            ),
        )
        if (w.entries.isNotEmpty()) {
            terminal.println(
                terminal.render(
                    table {
                        header { row("Name", "Count", "Duration (ms)") }
                        body {
                            w.entries.take(5).forEach { e ->
                                row(e.name.take(48), e.count.toString(), e.totalDurationMs.toString())
                            }
                        }
                    },
                ),
            )
            if (w.entries.size > 5) {
                terminal.println(dim("    … ${w.entries.size - 5} more"))
            }
        }
        terminal.println()
    }

    private fun printColdStart(cs: ColdStartMeasurement) {
        terminal.println(bold("  Cold start"))
        terminal.println(
            dim(
                "    ${cs.message}  (${cs.severity.name})  " +
                    "Launch: https://developer.android.com/topic/performance/vitals/launch-time",
            ),
        )
        terminal.println()
    }

    private fun printAndroidCliHint(extras: ReportRenderExtras) {
        val s = extras.androidCliDocsSnippet ?: return
        val firstUrl = Regex("https?://\\S+").find(s)?.value
        terminal.println()
        terminal.println(dim("  Android CLI docs search (truncated):"))
        terminal.println(dim("    ${firstUrl ?: s.take(120)}${if (s.length > 120) "…" else ""}"))
    }

    private fun printPerfetto(report: PerfReport) {
        report.perfettoTraceFile?.let { f ->
            terminal.println()
            terminal.println(dim("  Perfetto trace: ${f.absolutePath}  (open in https://ui.perfetto.dev)"))
        }
        report.perfettoMessage?.let { msg ->
            terminal.println()
            terminal.println(yellow("  Perfetto: $msg"))
        }
    }

    private fun printThresholdBreaches(breaches: List<ThresholdBreach>) {
        terminal.println(bold(red("  Threshold breaches")))
        breaches.forEach { b ->
            terminal.println("    ${red("✗")} ${b.message}")
        }
        terminal.println()
    }

    private fun printPotentialAnr(hints: List<PotentialAnrHint>) {
        terminal.println(bold(yellow("  Potential ANR risk (heuristics)")))
        terminal.println(
            terminal.render(
                table {
                    header { row("Severity", "Type", "Message") }
                    body {
                        hints.take(15).forEach { h ->
                            row(h.severity.name, h.hintType.name, h.message.take(80))
                        }
                    }
                }
            )
        )
    }

    // ── Sections ──────────────────────────────────────────────────────────────

    private fun printLeaks(leaks: List<MemoryLeak>) {
        terminal.println(bold(red("  Memory Leaks")))
        terminal.println(
            terminal.render(
                table {
                    header { row("Object", "Retained (KB)", "Shortest Path") }
                    body {
                        leaks.forEach { leak ->
                            row(
                                red(leak.leakingObject),
                                red("~${leak.retainedBytes / 1024}"),
                                leak.shortestPath.take(60).let { if (it.length == 60) "$it…" else it },
                            )
                        }
                    }
                }
            )
        )
    }

    private fun printStrictMode(violations: List<StrictModeViolation>) {
        terminal.println(bold(yellow("  StrictMode Violations")))
        terminal.println(
            terminal.render(
                table {
                    header { row("Type", "Count", "Example Thread") }
                    body {
                        violations.groupBy { it.violationType }.forEach { (type, group) ->
                            row(yellow(type.name), group.size.toString(), group.first().threadName)
                        }
                    }
                }
            )
        )

        // Detail block: top frame of each unique violation with an IDE link when available
        if (sourceLinker != null) {
            terminal.println(dim("  Top frames:"))
            violations.distinctBy { it.stackTrace.lines().firstOrNull() }.take(5).forEach { v ->
                printTopFrame(v)
            }
            terminal.println()
        }
    }

    /**
     * Prints the first resolvable frame of a violation as an OSC 8 hyperlink.
     *
     * Design decision: we print only the top resolvable frame (not the full
     * trace) to keep the console output scannable. The full trace is in the
     * Markdown report. If no frame resolves, we fall back to printing the raw
     * first line — never silent.
     */
    private fun printTopFrame(violation: StrictModeViolation) {
        val linker = sourceLinker ?: return
        val frames = violation.stackTrace.lines()
            .mapNotNull { StackFrameParser.parse(it) }

        val (frame, url) = frames
            .firstNotNullOfOrNull { f -> linker.linkFor(f)?.let { f to it } }
            ?: run {
                val raw = violation.stackTrace.lines().firstOrNull { it.isNotBlank() } ?: return
                terminal.println("    ${dim(raw.trim())}")
                return
            }

        // OSC 8 hyperlink — Mordant detects capability and falls back to plain text
        val linked = terminal.colors.blue("${frame.className}.${frame.method}(${frame.file}:${frame.line})")
        terminal.println("    $linked  ${dim(url)}")
    }

    private fun printRendering(incident: RenderingIncident) {
        terminal.println(bold(incident.severity.color()("  Rendering")))
        terminal.println(
            terminal.render(
                table {
                    header { row("Metric", "Value") }
                    body {
                        row("Total frames", incident.totalFrames.toString())
                        row("Janky (>16ms)", incident.jankyFrames.toString())
                        row("Frozen (>700ms)", incident.frozenFrames.toString())
                        row("p90", "%.2f ms".format(incident.p90Ms))
                        row("p95", "%.2f ms".format(incident.p95Ms))
                        row("p99", "%.2f ms".format(incident.p99Ms))
                    }
                }
            )
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun severityLine(severity: Severity) =
        severity.color()("  ● Overall: ${severity.name}")

    private fun Severity.color() = when (this) {
        Severity.CRITICAL -> red
        Severity.WARNING -> yellow
        Severity.INFO -> green
    }
}
