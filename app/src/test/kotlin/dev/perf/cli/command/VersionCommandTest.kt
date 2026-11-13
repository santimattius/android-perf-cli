package dev.perf.cli.command

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import dev.perf.cli.BuildConfig
import dev.perf.cli.PerfCli
import dev.perf.guardian.GuardianManifest
import dev.perf.trace.QueryPackManifest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * Tests for [VersionCommand] using Clikt's `CliktCommand.test()` harness.
 *
 * [BuildConfig.VERSION] is a `const val` resolved at compile time, so no
 * mocks, fakes, or test doubles are needed.
 *
 * Covers spec requirements:
 *   T-version-1: perf version prints the version string to stdout, exit 0
 *   T-version-2: output is exactly BuildConfig.VERSION (no extra whitespace on its own line)
 *   T-version-3: exit code is 0
 */
class VersionCommandTest {

    // ── T-version-1: output contains the version string ──────────────────────

    @Test
    fun `version command prints BuildConfig VERSION to stdout`() {
        val result = VersionCommand().test("")
        assertTrue(
            result.output.contains(BuildConfig.VERSION),
            "stdout must contain '${BuildConfig.VERSION}', got: '${result.output}'",
        )
    }

    // ── T-version-2: output equals VERSION (trimmed) ──────────────────────────

    @Test
    fun `version command output trimmed equals BuildConfig VERSION`() {
        val result = VersionCommand().test("")
        assertEquals(
            BuildConfig.VERSION,
            result.output.trim(),
            "stdout trimmed must equal '${BuildConfig.VERSION}'",
        )
    }

    // ── T-version-3: exit code is 0 ───────────────────────────────────────────

    @Test
    fun `version command exits with code 0`() {
        val result = VersionCommand().test("")
        assertEquals(0, result.statusCode, "VersionCommand must exit with code 0")
    }

    // ── T-version-4: root --version flag ─────────────────────────────────────

    @Test
    fun `perf --version prints version and exits 0`() {
        val result = PerfCli().subcommands(VersionCommand()).test("--version")
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains(BuildConfig.VERSION))
    }

    // ── T-version-5: root -V short flag ──────────────────────────────────────

    @Test
    fun `perf -V prints version and exits 0`() {
        val result = PerfCli().subcommands(VersionCommand()).test("-V")
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains(BuildConfig.VERSION))
    }

    // ── --verify-resources: pure hit/miss algorithm ───────────────────────────
    //
    // Covers GI-7 (PR1): the hidden flag opens every catalog+query-pack classpath
    // resource and exits 1 on miss. `verifyResourcePaths` is the pure core of that
    // logic — no classpath I/O — so miss-detection is triangulated with a fake
    // predicate instead of deleting real embedded resources.

    @Test
    fun `verifyResourcePaths reports found true when the predicate resolves the path`() {
        val results = verifyResourcePaths(listOf("/guardian/v1/manifest.json")) { path ->
            path == "/guardian/v1/manifest.json"
        }
        assertEquals(listOf(ResourceCheck("/guardian/v1/manifest.json", found = true)), results)
    }

    @Test
    fun `verifyResourcePaths reports found false for a path the predicate rejects`() {
        val results = verifyResourcePaths(listOf("/guardian/v1/does-not-exist.md")) { false }
        assertEquals(listOf(ResourceCheck("/guardian/v1/does-not-exist.md", found = false)), results)
    }

    @Test
    fun `verifyResourcePaths preserves order and mixes hits and misses`() {
        val results = verifyResourcePaths(
            listOf("/hit-1", "/miss-1", "/hit-2"),
        ) { path -> path.startsWith("/hit") }

        assertEquals(
            listOf(
                ResourceCheck("/hit-1", found = true),
                ResourceCheck("/miss-1", found = false),
                ResourceCheck("/hit-2", found = true),
            ),
            results,
        )
    }

    // ── --verify-resources: end-to-end wiring against real embedded resources ─

    @Test
    fun `perf version --verify-resources exits 0 when all embedded resources are present`() {
        val result = VersionCommand().test("--verify-resources")
        assertEquals(0, result.statusCode, "all PR1 embedded resources exist, so exit code must be 0")
    }

    @Test
    fun `perf version --verify-resources reports every checked path as OK`() {
        val result = VersionCommand().test("--verify-resources")
        assertTrue(
            result.output.contains("/guardian/v1/manifest.json"),
            "output must list the guardian manifest path, got: '${result.output}'",
        )
        assertTrue(
            result.output.contains("/query-packs/v1/manifest.json"),
            "output must list the query-pack manifest path, got: '${result.output}'",
        )
        assertTrue(
            !result.output.contains("MISS"),
            "no resource should be reported missing on an unmodified checkout, got: '${result.output}'",
        )
    }

    @Test
    fun `perf version --verify-resources does not print the plain version string`() {
        val result = VersionCommand().test("--verify-resources")
        assertTrue(
            !result.output.trim().equals(BuildConfig.VERSION),
            "verify-resources output must be the report, not the bare version string",
        )
    }

    // ── loadCatalogChecks: graceful MISS instead of an uncaught crash ─────────
    //
    // PR1 review finding #1: GuardianAssets.loadCatalog() / QueryPackLoader.loadV1() both
    // throw IllegalStateException (via `error(...)`) when their own manifest.json is
    // unreachable — e.g. a --no-fallback native image with a broken resource-config.json
    // pattern. Before this fix, that exception propagated uncaught out of
    // verifyEmbeddedResources() instead of being reported as a MISS + exit 1, defeating the
    // whole point of --verify-resources (GI-7's native-smoke job needs a clean diagnostic,
    // not a crash). loadCatalogChecks is the extracted, injectable core of that recovery —
    // no mocking library, just a hand-written throwing lambda standing in for an unreachable
    // manifest.

    @Test
    fun `loadCatalogChecks reports the manifest path as a graceful miss when loadPaths throws`() {
        val results = loadCatalogChecks(
            manifestPath = "/guardian/v1/manifest.json",
            exists = { true },
        ) {
            throw IllegalStateException("Missing guardian catalog manifest: /guardian/v1/manifest.json")
        }

        assertEquals(
            listOf(ResourceCheck("/guardian/v1/manifest.json", found = false)),
            results,
            "an unreachable manifest must be reported as a graceful MISS, not crash the command",
        )
    }

    @Test
    fun `loadCatalogChecks does not re-check the first proven path when loadPaths succeeds`() {
        val checkedPaths = mutableListOf<String>()

        val results = loadCatalogChecks(
            manifestPath = "/guardian/v1/manifest.json",
            exists = { path ->
                checkedPaths += path
                path == "/guardian/v1/skills/foo/SKILL.md"
            },
        ) {
            listOf("/guardian/v1/manifest.json", "/guardian/v1/skills/foo/SKILL.md")
        }

        assertEquals(
            listOf(
                ResourceCheck("/guardian/v1/manifest.json", found = true),
                ResourceCheck("/guardian/v1/skills/foo/SKILL.md", found = true),
            ),
            results,
        )
        assertEquals(
            listOf("/guardian/v1/skills/foo/SKILL.md"),
            checkedPaths,
            "the manifest path (first entry) must not be re-opened via exists() — a " +
                "successful loadPaths() call already proves it reachable (finding #6)",
        )
    }

    @Test
    fun `loadQueryPackChecks reports the manifest path as a graceful miss when loadPaths throws`() {
        val results = loadQueryPackChecks(
            manifestPath = "/query-packs/v1/manifest.json",
            exists = { true },
        ) {
            throw IllegalStateException("Missing query pack manifest: /query-packs/v1/manifest.json")
        }

        assertEquals(
            listOf(ResourceCheck("/query-packs/v1/manifest.json", found = false)),
            results,
            "an unreachable query-pack manifest must be reported as a graceful MISS, not crash the command",
        )
    }

    @Test
    fun `loadQueryPackChecks delegates to verifyResourcePaths when loadPaths succeeds`() {
        val results = loadQueryPackChecks(
            manifestPath = "/query-packs/v1/manifest.json",
            exists = { path -> path == "/query-packs/v1/manifest.json" },
        ) {
            listOf("/query-packs/v1/manifest.json", "/query-packs/v1/rendering.sql")
        }

        assertEquals(
            listOf(
                ResourceCheck("/query-packs/v1/manifest.json", found = true),
                ResourceCheck("/query-packs/v1/rendering.sql", found = false),
            ),
            results,
        )
    }

    // ── loadCatalogChecks / loadQueryPackChecks: malformed JSON is also a graceful MISS ──
    //
    // Second review pass, findings #1 and #2: the original fix (above) only caught
    // IllegalStateException — the exception GuardianAssets.loadCatalog() / QueryPackLoader.loadV1()
    // throw when their manifest.json is MISSING (`error(...)`). A malformed manifest (e.g. a
    // required field with the wrong JSON type) throws kotlinx.serialization.SerializationException
    // instead, which extends IllegalArgumentException — NOT IllegalStateException — so it still
    // propagated uncaught before this fix. These fixtures decode through the real
    // kotlinx.serialization pipeline (hand-built malformed JSON, no mocking library) so the
    // SerializationException is genuine, not a hand-thrown stand-in.

    @Test
    fun `loadCatalogChecks reports the manifest path as a graceful miss on malformed JSON`() {
        val json = Json { ignoreUnknownKeys = true }
        val malformedManifest = """{"guardianVersion": 42}""" // wrong type: Int, not String

        val results = loadCatalogChecks(
            manifestPath = "/guardian/v1/manifest.json",
            exists = { true },
        ) {
            json.decodeFromString(GuardianManifest.serializer(), malformedManifest)
            error("unreachable: decodeFromString above must throw SerializationException")
        }

        assertEquals(
            listOf(ResourceCheck("/guardian/v1/manifest.json", found = false)),
            results,
            "a malformed manifest (wrong JSON type for guardianVersion) must be reported as a " +
                "graceful MISS, not crash the command",
        )
    }

    @Test
    fun `loadQueryPackChecks reports the manifest path as a graceful miss on malformed JSON`() {
        val json = Json { ignoreUnknownKeys = true }
        val malformedManifest = """{"version": 1, "domains": {}}""" // wrong type: Int, not String

        val results = loadQueryPackChecks(
            manifestPath = "/query-packs/v1/manifest.json",
            exists = { true },
        ) {
            json.decodeFromString(QueryPackManifest.serializer(), malformedManifest)
            error("unreachable: decodeFromString above must throw SerializationException")
        }

        assertEquals(
            listOf(ResourceCheck("/query-packs/v1/manifest.json", found = false)),
            results,
            "a malformed query-pack manifest (wrong JSON type for version) must be reported as a " +
                "graceful MISS, not crash the command",
        )
    }

    // ── loadQueryPackChecks: proven-manifest optimization (finding #4, mirrors #6) ──

    @Test
    fun `loadQueryPackChecks does not re-check the first proven path when loadPaths succeeds`() {
        val checkedPaths = mutableListOf<String>()

        val results = loadQueryPackChecks(
            manifestPath = "/query-packs/v1/manifest.json",
            exists = { path ->
                checkedPaths += path
                path == "/query-packs/v1/rendering.sql"
            },
        ) {
            listOf("/query-packs/v1/manifest.json", "/query-packs/v1/rendering.sql")
        }

        assertEquals(
            listOf(
                ResourceCheck("/query-packs/v1/manifest.json", found = true),
                ResourceCheck("/query-packs/v1/rendering.sql", found = true),
            ),
            results,
        )
        assertEquals(
            listOf("/query-packs/v1/rendering.sql"),
            checkedPaths,
            "the manifest path (first entry) must not be re-opened via exists() — a successful " +
                "loadPaths() call already proves it reachable (finding #4, mirrors finding #6)",
        )
    }

    // ── End-to-end: --verify-resources still exits 1 with a report, never a raw crash ──
    // (real embedded resources are all present on an unmodified checkout, so this only
    // proves the wiring is exception-safe end to end, not the manifest-missing path itself
    // — that path is covered at the unit level above, per the design's existing pattern of
    // testing miss-detection without deleting real embedded resources.)

    @Test
    fun `perf version --verify-resources never throws an uncaught exception`() {
        val result = VersionCommand().test("--verify-resources")
        assertEquals(0, result.statusCode)
    }
}
