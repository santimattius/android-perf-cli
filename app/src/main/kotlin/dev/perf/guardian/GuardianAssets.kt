package dev.perf.guardian

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Loads the embedded guardian catalog from the classpath — mirrors
 * [dev.perf.trace.QueryPackLoader]'s pattern exactly: `Json { ignoreUnknownKeys = true }` +
 * `Class.getResourceAsStream`, zero reflection.
 *
 * Load-bearing constraint (design decision, `sdd/android-agent-guardian/design`):
 * GraalVM native-image CANNOT enumerate a resource directory
 * (`getResourceAsStream` on a directory yields nothing under `--no-fallback`).
 * This object therefore NEVER walks `guardian/v1/`; every path it can report comes
 * from an explicit catalog entry ([GuardianManifest]).
 */
object GuardianAssets {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Source of truth for the guardian catalog's classpath manifest path — reused by
     * [dev.perf.cli.command.VersionCommand] instead of a duplicated string literal
     * (PR1 second review pass, finding #3). `internal` is sufficient: both files live in
     * the `app` Gradle module.
     */
    internal const val MANIFEST_PATH = "/guardian/v1/manifest.json"

    fun loadCatalog(): GuardianManifest {
        val text =
            GuardianAssets::class.java.getResourceAsStream(MANIFEST_PATH)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: error("Missing guardian catalog manifest: $MANIFEST_PATH")
        return json.decodeFromString<GuardianManifest>(text)
    }

    /**
     * Every classpath resource path the catalog references, including the manifest itself:
     * skill `SKILL.md` files (+ their extra resources), persona artifacts (all CLI targets),
     * and doc snippets. Used by `perf version --verify-resources` to prove reachability inside
     * a `--no-fallback` native image without ever walking a resource directory.
     */
    fun catalogResourcePaths(): List<String> = resourcePathsFor(loadCatalog())

    /**
     * Pure derivation of [catalogResourcePaths], extracted so its three traversal branches
     * (skills+extraResources, personas.artifacts, docSnippets) can be exercised directly
     * against a hand-built [GuardianManifest] fixture in tests — the real embedded PR1
     * manifest ships an empty roster, so none of these branches ever ran under test
     * before this was extracted (PR1 review finding #5).
     *
     * The manifest path is always the first entry of the result — callers that already
     * proved it reachable via a successful [loadCatalog] parse can rely on that ordering
     * to avoid re-opening it (see [dev.perf.cli.command.VersionCommand], finding #6).
     */
    internal fun resourcePathsFor(catalog: GuardianManifest): List<String> {
        val paths = mutableListOf(MANIFEST_PATH)
        catalog.skills.forEach { skill ->
            paths += "/guardian/v1/${skill.resource}"
            skill.extraResources.forEach { extra -> paths += "/guardian/v1/$extra" }
        }
        catalog.personas.forEach { persona ->
            persona.artifacts.values.forEach { resource -> paths += "/guardian/v1/$resource" }
        }
        catalog.docSnippets.forEach { snippet -> paths += "/guardian/v1/${snippet.resource}" }
        return paths
    }

    /** Reads a catalog-relative payload after the installer has validated its safe relative path. */
    fun readResource(resource: String): ByteArray {
        val path = "/guardian/v1/$resource"
        return GuardianAssets::class.java.getResourceAsStream(path)?.use { it.readBytes() }
            ?: error("Missing guardian payload resource: $path")
    }

    /** Opens [path] as a classpath resource stream; returns whether it is reachable. */
    fun resourceExists(path: String): Boolean = GuardianAssets::class.java.getResourceAsStream(path) != null
}
