package dev.perf.cli.history

import kotlinx.serialization.json.Json
import java.io.File

private val historyJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

/**
 * Filesystem-backed history store. One JSON file per `perf run` invocation.
 *
 * Layout: <baseDir>/<packageName>/<runId>.json
 *
 * Mirrors the [dev.perf.cli.config.PerfConfig] pattern:
 * - [Json] with prettyPrint + ignoreUnknownKeys for schema evolution.
 * - Explicit [SessionHistoryEntry.serializer()] calls (GraalVM AOT-safe).
 *
 * File naming uses the run's ISO-8601 timestamp as prefix so that
 * lexicographic file sort equals chronological order.
 */
class HistoryStore(private val baseDir: File) {

    /**
     * Appends [entry] to disk, then prunes oldest files if count exceeds [maxEntries].
     *
     * Silent on success. Caller wraps in [runCatching] for best-effort semantics.
     */
    fun append(entry: SessionHistoryEntry, maxEntries: Int = 100) {
        val dir = historyDir(entry.packageName)
        dir.mkdirs()

        val file = File(dir, "${entry.runId}.json")
        file.writeText(historyJson.encodeToString(SessionHistoryEntry.serializer(), entry))

        pruneOldest(dir, maxEntries)
    }

    /**
     * Returns up to [windowSize] most-recent entries for [packageName], sorted
     * oldest-first (lexicographic by filename = chronological ascending).
     *
     * Corrupt or missing files are silently skipped via [runCatching].
     */
    fun loadWindow(packageName: String, windowSize: Int = 30): List<SessionHistoryEntry> {
        val dir = historyDir(packageName)
        if (!dir.exists()) return emptyList()

        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?: return emptyList()

        return files
            .takeLast(windowSize)
            .mapNotNull { file ->
                runCatching {
                    historyJson.decodeFromString(SessionHistoryEntry.serializer(), file.readText())
                }.getOrNull()
            }
    }

    /**
     * Returns the storage directory for [packageName]; creates it on demand via [mkdirs].
     */
    fun historyDir(packageName: String): File = File(baseDir, packageName)

    /**
     * Deletes oldest files (by name sort) that exceed [maxEntries].
     */
    private fun pruneOldest(dir: File, maxEntries: Int) {
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?: return

        if (files.size > maxEntries) {
            val toDelete = files.take(files.size - maxEntries)
            toDelete.forEach { it.delete() }
        }
    }

    companion object {
        /**
         * Default base: ~/.config/perf-cli/history (XDG convention, matches PerfConfig).
         */
        fun defaultBaseDir(): File =
            File(System.getProperty("user.home"), ".config/perf-cli/history")
    }
}
