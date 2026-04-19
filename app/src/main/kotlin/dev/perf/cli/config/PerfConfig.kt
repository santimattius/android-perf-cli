package dev.perf.cli.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
private val configFile = File(System.getProperty("user.home"), ".config/perf-cli/config.json")

/**
 * Persisted CLI configuration written by `perf init`.
 *
 * Design decision: stored in ~/.config/perf-cli/ (XDG convention) rather than
 * a project-local file so the SDK path is set once globally — not per-project.
 */
@Serializable
data class PerfConfig(
    val androidSdkPath: String,
    val defaultAvd: String? = null,
)

fun loadConfig(): PerfConfig =
    json.decodeFromString(configFile.readText())

fun saveConfig(config: PerfConfig) {
    configFile.parentFile.mkdirs()
    configFile.writeText(json.encodeToString(PerfConfig.serializer(), config))
}

fun configExists(): Boolean = configFile.exists()
