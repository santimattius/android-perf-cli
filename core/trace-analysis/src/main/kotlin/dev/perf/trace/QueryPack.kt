package dev.perf.trace

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class QueryPackManifest(
    val version: String,
    val domains: Map<String, DomainSpec>,
)

@Serializable
data class DomainSpec(
    val mode: String,
    @SerialName("runMetrics") val runMetrics: String? = null,
    @SerialName("queryResource") val queryResource: String? = null,
    @SerialName("metricKeys") val metricKeys: List<String> = emptyList(),
)

object QueryPackLoader {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Source of truth for the query-pack manifest's classpath path — reused by
     * `dev.perf.cli.command.VersionCommand` (`app` module) instead of a duplicated string
     * literal (PR1 second review pass, finding #3). Must be `public`, not `internal`: the
     * `app` module depends on `core:trace-analysis` as a separate Gradle compilation unit,
     * and Kotlin's `internal` visibility does not cross module boundaries.
     */
    const val MANIFEST_PATH = "/query-packs/v1/manifest.json"

    fun loadV1(): QueryPackManifest {
        val text =
            QueryPackLoader::class.java.getResourceAsStream(MANIFEST_PATH)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: error("Missing query pack manifest: $MANIFEST_PATH")
        return json.decodeFromString<QueryPackManifest>(text)
    }

    fun loadSqlResource(resourceName: String): String {
        val path = "/query-packs/v1/$resourceName"
        return QueryPackLoader::class.java.getResourceAsStream(path)?.use { stream ->
            stream.bufferedReader().readText()
        } ?: error("Missing query pack SQL: $path")
    }

    fun supportedDomains(): Set<String> = loadV1().domains.keys

    fun domainSpec(domain: String): DomainSpec =
        loadV1().domains[domain.lowercase()]
            ?: error("Unknown analyze domain '$domain'. Supported: ${supportedDomains().sorted().joinToString()}")
}
