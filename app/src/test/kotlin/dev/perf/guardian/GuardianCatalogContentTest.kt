package dev.perf.guardian

import java.nio.file.Files
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Lints the real embedded PR2 payloads and proves they install end to end. */
class GuardianCatalogContentTest {
    private val catalog = GuardianAssets.loadCatalog()

    private fun frontmatter(text: String): Map<String, String> {
        val lines = text.lines()
        assertEquals("---", lines.first(), "payload must start with YAML frontmatter")
        val end = lines.drop(1).indexOf("---")
        assertTrue(end > 0, "frontmatter must be closed with ---")
        return lines.subList(1, end + 1)
            .filter { ':' in it && !it.startsWith(" ") }
            .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }
    }

    @Test
    fun `skill frontmatter name equals its id and description states what and when`() {
        catalog.skills.forEach { skill ->
            val text = GuardianAssets.readResource(skill.resource).decodeToString()
            val meta = frontmatter(text)
            assertEquals(skill.id, meta["name"])
            val description = meta["description"].orEmpty()
            assertTrue(description.length >= 40, "description must be informative: $description")
            assertTrue(description.contains("Use when", ignoreCase = true), "description must say when to use it")
            assertTrue(text.lines().size < 500, "skill body must stay under 500 lines")
        }
    }

    @Test
    fun `persona frontmatter name equals its id and its required skills exist`() {
        val skillIds = catalog.skills.map { it.id }.toSet()
        catalog.personas.forEach { persona ->
            val text = GuardianAssets.readResource(persona.artifacts.getValue("claude")).decodeToString()
            val meta = frontmatter(text)
            assertEquals(persona.id, meta["name"])
            assertTrue(meta["description"].orEmpty().isNotBlank())
            assertTrue(skillIds.containsAll(persona.requiresSkills))
        }
    }

    @Test
    fun `skill covers all four analyze domains and the exit code contract`() {
        val text = GuardianAssets.readResource("skills/android-performance/SKILL.md").decodeToString()
        listOf("startup", "rendering", "memory", "cpu").forEach { domain ->
            assertTrue(text.contains("perf analyze $domain"), "skill must document perf analyze $domain")
        }
        assertTrue(text.contains("exit 2") || text.contains("`2`"), "skill must document exit code 2")
    }

    @Test
    fun `every persona requires the general skill and its own specialized skill`() {
        val specialized = mapOf(
            "anr-auditor" to "android-anr-triage",
            "startup-auditor" to "android-startup-budget",
            "jank-auditor" to "android-jank-review",
            "leak-auditor" to "android-memory-leak-review",
        )
        specialized.forEach { (personaId, skillId) ->
            val persona = catalog.personas.single { it.id == personaId }
            assertEquals(listOf("android-performance", skillId), persona.requiresSkills, personaId)
        }
    }

    @Test
    fun `specialized skills only reference perf commands that exist`() {
        val analyzeDomains = setOf("startup", "rendering", "memory", "cpu")
        val invocation = Regex("perf analyze ([a-z]+)")
        catalog.skills.forEach { skill ->
            val text = GuardianAssets.readResource(skill.resource).decodeToString()
            invocation.findAll(text).forEach { match ->
                assertTrue(
                    match.groupValues[1] in analyzeDomains,
                    "${skill.id} references unknown analyze domain: ${match.value}",
                )
            }
        }
    }

    @Test
    fun `each specialized skill points back to the general skill and states how to report`() {
        catalog.skills.filter { it.id != "android-performance" }.forEach { skill ->
            val text = GuardianAssets.readResource(skill.resource).decodeToString()
            assertTrue(text.contains("android-performance"), "${skill.id} must reference the general skill")
            assertTrue(text.contains("## How to report"), "${skill.id} must define a reporting format")
        }
    }

    @Test
    fun `every persona ships a Codex TOML with the required fields`() {
        catalog.personas.forEach { persona ->
            val text = GuardianAssets.readResource(persona.artifacts.getValue("codex")).decodeToString()
            assertTrue(Regex("(?m)^name = \"${persona.id}\"$").containsMatchIn(text), "${persona.id}: name")
            assertTrue(Regex("(?m)^description = \".{20,}\"$").containsMatchIn(text), "${persona.id}: description")
            assertTrue(Regex("(?m)^developer_instructions = \"\"\"$").containsMatchIn(text), "${persona.id}: developer_instructions")
            assertEquals(0, Regex("\"\"\"").findAll(text).count() % 2, "${persona.id}: unbalanced triple quotes")
            assertTrue(text.trimEnd().endsWith("\"\"\""), "${persona.id}: developer_instructions must close the file")
            assertTrue(text.contains(".agents/skills/android-performance/SKILL.md"), "${persona.id}: must point at the general skill path")
        }
    }

    @Test
    fun `codex AGENTS md snippet is delimited by its catalog markers and names every codex persona`() {
        val snippet = catalog.docSnippets.single()
        val text = GuardianAssets.readResource(snippet.resource).decodeToString().trim()
        assertTrue(text.startsWith(snippet.markerBegin))
        assertTrue(text.endsWith(snippet.markerEnd))
        assertEquals(1, Regex(Regex.escape(snippet.markerBegin)).findAll(text).count())
        catalog.personas.forEach { persona -> assertTrue(text.contains("`${persona.id}`"), "snippet must name ${persona.id}") }
        assertTrue(text.toByteArray().size < 4_096, "snippet must stay small against Codex's AGENTS.md size cap")
    }

    @Test
    fun `real catalog installs for codex and coexists with claude`() {
        val repository = Files.createTempDirectory("guardian-real-codex")
        try {
            GuardianInstaller().install(repository, "claude")
            val codex = GuardianInstaller().install(repository, "codex")
            assertEquals(11, codex.installedPaths.size)
            assertTrue(repository.resolve(".codex/agents/jank-auditor.toml").exists())
            assertTrue(repository.resolve("AGENTS.md").readText().contains("jank-auditor"))
            assertTrue(GuardianInstaller().install(repository, "claude").installedPaths.isEmpty())
            assertTrue(GuardianInstaller().install(repository, "codex").installedPaths.isEmpty())
        } finally {
            repository.toFile().deleteRecursively()
        }
    }

    @Test
    fun `real catalog installs into a clean repository and reinstalls unchanged`() {
        val repository = Files.createTempDirectory("guardian-real-catalog")
        try {
            val first = GuardianInstaller().install(repository, "claude")
            assertEquals(10, first.installedPaths.size)
            assertTrue(repository.resolve(".claude/skills/android-performance/SKILL.md").exists())
            assertTrue(repository.resolve(".claude/agents/android-performance-guardian.md").readText().isNotBlank())

            val second = GuardianInstaller().install(repository, "claude")
            assertTrue(second.installedPaths.isEmpty())
            assertFalse(second.unchangedPaths.isEmpty())
        } finally {
            repository.toFile().deleteRecursively()
        }
    }
}
