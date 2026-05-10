package dev.perf.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import dev.perf.cli.history.HistoryStore
import dev.perf.cli.history.RateCalculator
import dev.perf.report.vitals.VitalsPlaybook
import java.io.File

/**
 * `perf history` — print rolling session rates for a package.
 *
 * Read-only: never calls ADB or modifies the history store.
 *
 * Rates are advisory only — exit code is always 0 regardless of whether
 * any rate is above the Play Console gate. Use `perf run` threshold breaches
 * for gate enforcement.
 */
class HistoryCommand : CliktCommand(
    name = "history",
    help = "Show rolling session rates for a package based on prior `perf run` invocations.",
) {
    private val packageName: String by option(
        "--package", "-p",
        help = "Application package name to look up history for.",
    ).required()

    private val window: Int by option(
        "--window",
        help = "Rolling window size (number of most-recent runs to consider). Default: 30.",
    ).int().default(30)

    private val historyDirOverride: File? by option(
        "--history-dir",
        help = "Override history directory (default: ~/.config/perf-cli/history). Use in CI or testing.",
    ).file(canBeFile = false)

    override fun run() {
        val baseDir = historyDirOverride ?: HistoryStore.defaultBaseDir()
        val store = HistoryStore(baseDir)
        val entries = store.loadWindow(packageName, window)
        val summary = RateCalculator.compute(entries, window)

        if (entries.isEmpty()) {
            echo("No session history found for $packageName.")
            echo("Run `perf run --package $packageName` first to record session data.")
            VitalsPlaybook.playConsoleLimitationsBanner().forEach { echo(it) }
            return
        }

        echo()
        echo("## Session Rates — $packageName (last ${summary.totalRuns} runs)")
        echo()
        echo("| Metric | Rate | Play Console Gate | Status |")
        echo("|--------|------|-------------------|--------|")
        echo("| ANR rate | ${"%.2f".format(summary.anrRate * 100)}% | >0.47% | ${rateStatus(summary.anrRate, ANR_GATE)} |")
        echo("| Crash rate | ${"%.2f".format(summary.crashRate * 100)}% | >1.09% | ${rateStatus(summary.crashRate, CRASH_GATE)} |")
        echo("| Slow session rate | ${"%.2f".format(summary.slowSessionRate * 100)}% | >25.00% | ${rateStatus(summary.slowSessionRate, SLOW_SESSION_GATE)} |")
        echo()
        echo("_Window: ${summary.totalRuns} of $window runs._")
        echo()

        VitalsPlaybook.playConsoleLimitationsBanner().forEach { echo(it) }

        if (summary.hasExitSignalsCaveat) {
            echo()
            echo(
                "> Some runs in this window had `--exit-signals` (exitSignals engine) disabled, " +
                    "so ANR and crash rates may underreport. Re-run with `--exit-signals` consistently for accurate results.",
            )
        }
    }
}

// ── Rate gate constants (mirrors MarkdownReporter constants) ─────────────────

private const val ANR_GATE = 0.0047          // 0.47% ANR rate
private const val CRASH_GATE = 0.0109        // 1.09% crash rate
private const val SLOW_SESSION_GATE = 0.25   // 25% slow session rate

private fun rateStatus(rate: Double, gate: Double): String = when {
    rate >= gate -> "OVER"
    rate >= gate * 0.5 -> "NEAR"
    else -> "PASS"
}
