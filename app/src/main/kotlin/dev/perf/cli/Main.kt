package dev.perf.cli

import com.github.ajalt.clikt.core.subcommands
import dev.perf.cli.command.InitCommand
import dev.perf.cli.command.RunCommand
import dev.perf.cli.command.SdkCommand

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
    override fun run() = Unit
}

fun main(args: Array<String>) =
    PerfCli()
        .subcommands(InitCommand(), SdkCommand(), RunCommand())
        .main(args)
