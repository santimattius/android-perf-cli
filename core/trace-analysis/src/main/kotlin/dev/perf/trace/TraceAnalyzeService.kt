package dev.perf.trace

import dev.perf.trace.model.AnalyzeOptions
import dev.perf.trace.model.AnalyzeResult
import dev.perf.trace.model.TraceAnalyzeException
import java.io.File
import kotlinx.serialization.json.JsonElement

class TraceAnalyzeService(
    private val resolver: TraceProcessorResolver = TraceProcessorResolver,
    private val runner: TraceProcessorRunner = TraceProcessorRunner(),
    private val packLoader: QueryPackLoader = QueryPackLoader,
) {

    fun analyze(trace: File, domain: String, options: AnalyzeOptions = AnalyzeOptions()): AnalyzeResult {
        require(trace.isFile) { "Trace file not found: ${trace.absolutePath}" }
        val resolved =
            resolver.resolve()
                ?: throw TraceAnalyzeException.TraceProcessorMissing(resolver.installHint())
        val manifest = packLoader.loadV1()
        val spec = packLoader.domainSpec(domain)
        val domainKey = domain.lowercase()

        val runResult =
            when (spec.mode) {
                "run_metrics" -> {
                    val metricsName =
                        spec.runMetrics
                            ?: throw TraceAnalyzeException.ParseFailed(domainKey, "run_metrics missing name")
                    runner.runMetrics(resolved.executable, trace, metricsName)
                }
                "sql" -> {
                    val resource =
                        spec.queryResource
                            ?: throw TraceAnalyzeException.ParseFailed(domainKey, "sql resource missing")
                    var sql = packLoader.loadSqlResource(resource)
                    options.packageName?.let { pkg ->
                        sql = "-- package filter: $pkg\n$sql"
                    }
                    val tmp = File.createTempFile("perf-analyze-", ".sql")
                    try {
                        tmp.writeText(sql)
                        runner.runQueryFile(resolved.executable, trace, tmp)
                    } finally {
                        tmp.delete()
                    }
                }
                else -> throw TraceAnalyzeException.ParseFailed(domainKey, "unknown mode ${spec.mode}")
            }

        if (runResult.exitCode != 0) {
            throw TraceAnalyzeException.QueryFailed(domainKey, runResult.exitCode, runResult.output)
        }

        val metrics: Map<String, JsonElement> =
            when (spec.mode) {
                "run_metrics" -> TraceMetricsParser.parseRunMetrics(runResult.output)
                "sql" -> {
                    val parsed = TraceMetricsParser.parseCsvMetrics(runResult.output, spec.metricKeys)
                    if (parsed.isEmpty() && spec.metricKeys.isNotEmpty()) {
                        throw TraceAnalyzeException.ParseFailed(
                            domainKey,
                            "no metrics parsed from output (keys=${spec.metricKeys})",
                        )
                    }
                    parsed
                }
                else -> emptyMap()
            }

        return AnalyzeResult(
            domain = domainKey,
            queryPackVersion = manifest.version,
            tracePath = trace.absolutePath,
            metrics = metrics,
            rawOutputPreview = runResult.output.take(500).ifBlank { null },
        )
    }
}
