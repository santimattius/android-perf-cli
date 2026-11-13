package dev.perf.guardian

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [GuardianAssets] — the classpath loader for the embedded guardian catalog.
 *
 * PR1 (native-image resource reachability spike) ships an EMPTY roster: no skills,
 * personas, or doc snippets yet — those land in PR2/PR3/PR4. This test proves the
 * catalog itself loads correctly via classpath resource access, exactly like
 * [dev.perf.trace.QueryPackLoader], with zero reflection (native-image safe).
 *
 * Covers spec/design requirements:
 *   GI-7 (PR1): embedded guardian resources MUST be reachable via classpath loading
 */
class GuardianAssetsTest {

    // ── Catalog loads and parses the empty-roster manifest ───────────────────

    @Test
    fun `loadCatalog reads guardianVersion from the embedded manifest`() {
        val catalog = GuardianAssets.loadCatalog()
        assertEquals("v1", catalog.guardianVersion, "guardianVersion must come from the embedded JSON")
    }

    @Test
    fun `loadCatalog ships the general skill plus four specialized skills with matching personas`() {
        val catalog = GuardianAssets.loadCatalog()
        assertEquals(
            listOf(
                "android-performance",
                "android-anr-triage",
                "android-startup-budget",
                "android-jank-review",
                "android-memory-leak-review",
            ),
            catalog.skills.map { it.id },
        )
        assertEquals(
            listOf(
                "android-performance-guardian",
                "anr-auditor",
                "startup-auditor",
                "jank-auditor",
                "leak-auditor",
            ),
            catalog.personas.map { it.id },
        )
        assertEquals(emptyList(), catalog.docSnippets, "doc snippets arrive with the Codex target")
    }

    // ── catalogResourcePaths() derives paths from catalog entries ────────────

    @Test
    fun `catalogResourcePaths lists the manifest then every skill and persona payload`() {
        assertEquals(
            listOf(
                "/guardian/v1/manifest.json",
                "/guardian/v1/skills/android-performance/SKILL.md",
                "/guardian/v1/skills/android-anr-triage/SKILL.md",
                "/guardian/v1/skills/android-startup-budget/SKILL.md",
                "/guardian/v1/skills/android-jank-review/SKILL.md",
                "/guardian/v1/skills/android-memory-leak-review/SKILL.md",
                "/guardian/v1/personas/claude/android-performance-guardian.md",
                "/guardian/v1/personas/claude/anr-auditor.md",
                "/guardian/v1/personas/claude/startup-auditor.md",
                "/guardian/v1/personas/claude/jank-auditor.md",
                "/guardian/v1/personas/claude/leak-auditor.md",
            ),
            GuardianAssets.catalogResourcePaths(),
        )
    }

    @Test
    fun `every catalog resource path is reachable on the classpath`() {
        GuardianAssets.catalogResourcePaths().forEach { path ->
            assertTrue(GuardianAssets.resourceExists(path), "missing embedded resource: $path")
        }
    }

    // ── resourceExists() proves classpath reachability without directory walking ─

    @Test
    fun `resourceExists returns true for the embedded manifest path`() {
        assertTrue(
            GuardianAssets.resourceExists("/guardian/v1/manifest.json"),
            "the manifest resource must be reachable via getResourceAsStream",
        )
    }

    @Test
    fun `resourceExists returns false for a path that was never shipped`() {
        assertTrue(
            !GuardianAssets.resourceExists("/guardian/v1/does-not-exist.md"),
            "a nonexistent classpath resource must report as unreachable, not throw",
        )
    }

    // ── resourcePathsFor() traversal branches (PR1 review finding #5) ────────
    //
    // `catalogResourcePaths()` only ever exercises these branches against the real
    // embedded PR1 manifest, which ships an EMPTY roster — so none of the three
    // `forEach` branches (skills+extraResources, personas.artifacts, docSnippets) ever
    // actually ran under test. `resourcePathsFor` is the pure derivation extracted from
    // `catalogResourcePaths`, exercised here against hand-built `GuardianManifest`
    // fixtures instead of the real classpath resource.

    @Test
    fun `resourcePathsFor derives skill resource and extraResources paths with guardian v1 prefix`() {
        val catalog = GuardianManifest(
            guardianVersion = "v1",
            skills = listOf(
                SkillEntry(
                    id = "android-anr-triage",
                    version = "1.0.0",
                    resource = "skills/android-anr-triage/SKILL.md",
                    extraResources = listOf("skills/android-anr-triage/checklist.md"),
                ),
            ),
        )

        val paths = GuardianAssets.resourcePathsFor(catalog)

        assertEquals(
            listOf(
                "/guardian/v1/manifest.json",
                "/guardian/v1/skills/android-anr-triage/SKILL.md",
                "/guardian/v1/skills/android-anr-triage/checklist.md",
            ),
            paths,
            "skill resource and its extraResources must both be prefixed with /guardian/v1/",
        )
    }

    @Test
    fun `resourcePathsFor derives persona artifact paths for every CLI target with guardian v1 prefix`() {
        val catalog = GuardianManifest(
            guardianVersion = "v1",
            personas = listOf(
                PersonaEntry(
                    id = "anr-auditor",
                    version = "1.0.0",
                    domain = "anr",
                    artifacts = mapOf(
                        "claude" to "personas/claude/anr-auditor.md",
                        "codex" to "personas/codex/anr-auditor.toml",
                    ),
                ),
            ),
        )

        val paths = GuardianAssets.resourcePathsFor(catalog)

        assertEquals(
            listOf(
                "/guardian/v1/manifest.json",
                "/guardian/v1/personas/claude/anr-auditor.md",
                "/guardian/v1/personas/codex/anr-auditor.toml",
            ),
            paths,
            "every persona artifact value (per CLI target) must be prefixed with /guardian/v1/",
        )
    }

    @Test
    fun `resourcePathsFor derives docSnippet resource paths with guardian v1 prefix`() {
        val catalog = GuardianManifest(
            guardianVersion = "v1",
            docSnippets = listOf(
                DocSnippetEntry(
                    id = "codex-agents-md",
                    target = "codex",
                    version = "1.0.0",
                    path = "AGENTS.md",
                    resource = "snippets/codex-agents-md.md",
                    markerBegin = "<!-- guardian:begin -->",
                    markerEnd = "<!-- guardian:end -->",
                ),
            ),
        )

        val paths = GuardianAssets.resourcePathsFor(catalog)

        assertEquals(
            listOf(
                "/guardian/v1/manifest.json",
                "/guardian/v1/snippets/codex-agents-md.md",
            ),
            paths,
            "docSnippet resource must be prefixed with /guardian/v1/",
        )
    }

    @Test
    fun `resourcePathsFor combines all three branches in catalog order`() {
        val catalog = GuardianManifest(
            guardianVersion = "v1",
            skills = listOf(
                SkillEntry(id = "s1", version = "1.0.0", resource = "skills/s1/SKILL.md"),
            ),
            personas = listOf(
                PersonaEntry(
                    id = "p1",
                    version = "1.0.0",
                    domain = "anr",
                    artifacts = mapOf("claude" to "personas/claude/p1.md"),
                ),
            ),
            docSnippets = listOf(
                DocSnippetEntry(
                    id = "d1",
                    target = "codex",
                    version = "1.0.0",
                    path = "AGENTS.md",
                    resource = "snippets/d1.md",
                    markerBegin = "<!-- begin -->",
                    markerEnd = "<!-- end -->",
                ),
            ),
        )

        val paths = GuardianAssets.resourcePathsFor(catalog)

        assertEquals(
            listOf(
                "/guardian/v1/manifest.json",
                "/guardian/v1/skills/s1/SKILL.md",
                "/guardian/v1/personas/claude/p1.md",
                "/guardian/v1/snippets/d1.md",
            ),
            paths,
            "skills, then personas, then docSnippets — matching catalog field declaration order",
        )
    }
}
