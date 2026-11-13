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
    fun `real catalog installs into a clean repository and reinstalls unchanged`() {
        val repository = Files.createTempDirectory("guardian-real-catalog")
        try {
            val first = GuardianInstaller().install(repository, "claude")
            assertEquals(2, first.installedPaths.size)
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
