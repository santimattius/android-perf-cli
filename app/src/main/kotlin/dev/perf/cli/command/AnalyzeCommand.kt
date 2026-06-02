package dev.perf.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.long
import dev.perf.trace.TraceAnalyzeService
import dev.perf.trace.model.AnalyzeOptions
import dev.perf.trace.model.TraceAnalyzeException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File

class AnalyzeCommand : CliktCommand(
    name = "analyze",
    help = "Offline Perfetto trace metrics via trace_processor (startup, rendering, memory, cpu).",
    invokeWithoutSubcommand = true,
) {
    override fun run() {
        if (currentContext.invokedSubcommand != null) return
        echo("Usage: perf analyze <startup|rendering|memory|cpu> <trace.perfetto-trace> [options]")
        echo("See: docs/perfetto-investigation.md")
        throw ProgramResult(2)
    }

    init {
        subcommands(
            DomainAnalyzeCommand("startup"),
            DomainAnalyzeCommand("rendering"),
            DomainAnalyzeCommand("memory"),
            DomainAnalyzeCommand("cpu"),
        )
    }
}

private class DomainAnalyzeCommand(
    private val domain: String,
) : CliktCommand(name = domain, help = "Analyze trace for $domain metrics.") {

    private val traceFile by argument(help = "Path to .perfetto-trace file").convert { name ->
        File(name).absoluteFile.also { f ->
            require(f.isFile) { "Trace file not found: ${f.absolutePath}" }
        }
    }

    private val packageName by option("--package", help = "Target app package (recorded in output; future SQL filter).")
    private val windowMs by option("--window-ms", help = "Analysis window length in ms.").long()
    private val fromTs by option("--from-ts", help = "Window start timestamp (ns).").long()
    private val outputFormat by option("--output", help = "Output format.").choice("json", "md").default("md")
    private val maxMainThreadBlockedMs by option(
        "--max-main-thread-blocked-ms",
        help = "Fail with exit 1 if main_thread_blocked_ms exceeds this.",
    ).long()

    override fun run() {
        val service = TraceAnalyzeService()
        val result =
            try {
                service.analyze(
                    traceFile,
                    domain,
                    AnalyzeOptions(packageName = packageName, windowMs = windowMs, fromTs = fromTs),
                )
            } catch (e: TraceAnalyzeException.TraceProcessorMissing) {
                echo(e.message ?: "trace_processor not found", err = true)
                throw ProgramResult(1)
            } catch (e: TraceAnalyzeException) {
                echo(e.message ?: e.toString(), err = true)
                throw ProgramResult(1)
            }

        when (outputFormat) {
            "json" -> echo(Json.encodeToString(dev.perf.trace.model.AnalyzeResult.serializer(), result))
            "md" -> echo(formatMarkdown(result))
        }

        maxMainThreadBlockedMs?.let { limit ->
            val blocked = result.metrics["main_thread_blocked_ms"]?.longMetric()
            if (blocked != null && blocked > limit) {
                echo("Threshold breach: main_thread_blocked_ms=$blocked > $limit", err = true)
                throw ProgramResult(1)
            }
        }
    }

    private fun formatMarkdown(result: dev.perf.trace.model.AnalyzeResult): String =
        buildString {
            appendLine("## Perfetto analyze: ${result.domain}")
            appendLine("- **Trace**: `${result.tracePath}`")
            appendLine("- **Query pack**: ${result.queryPackVersion}")
            appendLine()
            appendLine("### Metrics")
            result.metrics.forEach { (k, v) -> appendLine("- **$k**: $v") }
            result.rawOutputPreview?.let {
                appendLine()
                appendLine("<details><summary>Raw preview</summary>")
                appendLine()
                appendLine("```")
                appendLine(it)
                appendLine("```")
                appendLine("</details>")
            }
        }
}

private fun JsonElement.longMetric(): Long? =
    (this as? JsonPrimitive)?.longOrNull
