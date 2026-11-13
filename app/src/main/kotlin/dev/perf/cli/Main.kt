package dev.perf.cli

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.versionOption
import dev.perf.cli.command.AnalyzeCommand
import dev.perf.cli.command.GuardianCommand
import dev.perf.cli.command.HistoryCommand
import dev.perf.cli.command.InitCommand
import dev.perf.cli.command.RunCommand
import dev.perf.cli.command.SdkCommand
import dev.perf.cli.command.VersionCommand

/**
 * Entry point. PerfCli is the root command — it holds no logic itself.
 *
 * Design decision: we use Clikt's `subcommands()` extension rather than
 * registering commands imperatively. This keeps Main.kt minimal and makes
 * adding future commands (e.g. `perf report --replay`) a one-liner here.
 */
class PerfCli : com.github.ajalt.clikt.core.CliktCommand(
    name = "perf",
    help = "Android performance monitoring CLI — leaks, StrictMode, frames.",
    invokeWithoutSubcommand = false,
) {
    init { versionOption(BuildConfig.VERSION, names = setOf("--version", "-V")) }
    override fun run() = Unit
}

fun main(args: Array<String>) =
    PerfCli()
        .subcommands(
            InitCommand(),
            SdkCommand(),
            RunCommand(),
            AnalyzeCommand(),
            HistoryCommand(),
            GuardianCommand(),
            VersionCommand(),
        )
        .main(args)
