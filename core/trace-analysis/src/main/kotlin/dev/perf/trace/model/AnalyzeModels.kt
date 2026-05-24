package dev.perf.trace.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AnalyzeOptions(
    val packageName: String? = null,
    val windowMs: Long? = null,
    val fromTs: Long? = null,
)

@Serializable
data class AnalyzeResult(
    val domain: String,
    val queryPackVersion: String,
    val tracePath: String,
    val metrics: Map<String, JsonElement>,
    val rawOutputPreview: String? = null,
)

@Serializable
data class TraceAnalysisSummary(
    val queryPackVersion: String,
    val domains: Map<String, AnalyzeResult>,
    val skipReason: String? = null,
)

sealed class TraceAnalyzeException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    class TraceProcessorMissing(val hint: String) : TraceAnalyzeException(hint)
    class QueryFailed(val domain: String, val exitCode: Int, val output: String) :
        TraceAnalyzeException("trace_processor failed for $domain (exit $exitCode): $output")
    class ParseFailed(val domain: String, val detail: String) :
        TraceAnalyzeException("Failed to parse $domain metrics: $detail")
}
