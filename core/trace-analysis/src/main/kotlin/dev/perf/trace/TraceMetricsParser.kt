package dev.perf.trace

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Parses trace_processor stdout (CSV-like or JSON lines) into a metric map for v1 packs.
 */
object TraceMetricsParser {

    fun parseCsvMetrics(output: String, keys: List<String>): Map<String, JsonElement> {
        val lines = output.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        if (lines.isEmpty()) return emptyMap()
        val headerLine = lines.first { it.contains(",") || !it.startsWith("{") }
        val dataLine = lines.last { it.contains(",") || (!it.startsWith("{") && lines.size > 1) }
        if (headerLine.startsWith("{")) return parseJsonLineMetrics(output, keys)
        val headers = headerLine.split(',').map { it.trim() }
        val values = dataLine.split(',').map { it.trim() }
        val row = headers.zip(values).toMap()
        return keys.associateWith { key ->
            row[key]?.let { JsonPrimitive(it) } ?: JsonNull
        }.filterValues { it != JsonNull }
    }

    fun parseJsonLineMetrics(output: String, keys: List<String>): Map<String, JsonElement> {
        val jsonLine = output.lines().map { it.trim() }.firstOrNull { it.startsWith("{") } ?: return emptyMap()
        return try {
            val obj = kotlinx.serialization.json.Json.parseToJsonElement(jsonLine)
            if (obj is JsonObject) {
                keys.associateWith { k -> obj[k] ?: JsonNull }.filterValues { it != JsonNull }
            } else {
                emptyMap()
            }
        } catch (_: Exception) {
            emptyMap()
        }
    }

    fun parseRunMetrics(output: String): Map<String, JsonElement> =
        buildJsonObject {
            put("raw_metrics_output", JsonPrimitive(output.take(4000)))
        }
}
