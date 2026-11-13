package dev.perf.cli.command

import com.github.ajalt.clikt.testing.test
import dev.perf.guardian.GuardianInstaller
import dev.perf.guardian.GuardianManifest
import dev.perf.guardian.PersonaEntry
import dev.perf.guardian.SkillEntry
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/** Public-interface tests for `perf guardian install` (PR2 T2: S2, S4, S7). */
class GuardianInstallCommandTest {
    private lateinit var repository: Path

    @BeforeTest
    fun setUp() {
        repository = Files.createTempDirectory("guardian-command-test")
    }

    @AfterTest
    fun tearDown() {
        repository.toFile().deleteRecursively()
    }

    private fun command() =
        GuardianCommand(
            GuardianInstallCommand(
                installer = GuardianInstaller(payloadReader = { "payload:$it".encodeToByteArray() }),
                catalog = { catalog() },
            ),
        )

    private fun catalog() =
        GuardianManifest(
            guardianVersion = "v-test",
            skills = listOf(SkillEntry("android-performance", "1", "skills/android-performance/SKILL.md")),
            personas =
                listOf(
                    PersonaEntry(
                        id = "android-performance-guardian",
                        version = "1",
                        domain = "all",
                        requiresSkills = listOf("android-performance"),
                        artifacts = mapOf("claude" to "personas/claude/android-performance-guardian.md"),
                    ),
                ),
        )

    @Test
    fun `install with explicit directory reports installed paths on stdout and exits zero`() {
        val result = command().test("install --target claude --directory $repository")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains(".claude/skills/android-performance/SKILL.md"), result.stdout)
        assertTrue(result.stdout.contains(".claude/agents/android-performance-guardian.md"), result.stdout)
        assertTrue(repository.resolve(".claude/skills/android-performance/SKILL.md").exists())
        assertTrue(repository.resolve(".guardian-manifest.json").exists())
    }

    @Test
    fun `repeated install reports unchanged paths and exits zero`() {
        command().test("install --target claude --directory $repository")

        val result = command().test("install --target claude --directory $repository")

        assertEquals(0, result.statusCode)
        assertTrue(result.stdout.contains("Unchanged"), result.stdout)
        assertFalse(result.stdout.contains("Installed"), result.stdout)
    }

    @Test
    fun `missing target is a usage error and writes nothing`() {
        val result = command().test("install --directory $repository")

        assertNotEquals(0, result.statusCode)
        assertFalse(repository.resolve(".guardian-manifest.json").exists())
    }

    @Test
    fun `unsupported target is rejected and writes nothing`() {
        val result = command().test("install --target codex --directory $repository")

        assertNotEquals(0, result.statusCode)
        assertFalse(repository.resolve(".guardian-manifest.json").exists())
    }

    @Test
    fun `collision with an unowned file fails on stderr with non-zero exit and leaves it intact`() {
        val existing = repository.resolve(".claude/skills/android-performance/SKILL.md")
        Files.createDirectories(existing.parent)
        existing.writeText("user content")

        val result = command().test("install --target claude --directory $repository")

        assertNotEquals(0, result.statusCode)
        assertTrue(result.stderr.contains("Refusing to overwrite"), result.stderr)
        assertEquals("user content", existing.readText())
        assertFalse(repository.resolve(".guardian-manifest.json").exists())
    }

    @Test
    fun `missing directory fails on stderr with non-zero exit`() {
        val result = command().test("install --target claude --directory ${repository.resolve("absent")}")

        assertNotEquals(0, result.statusCode)
        assertTrue(result.stderr.isNotBlank())
    }
}
