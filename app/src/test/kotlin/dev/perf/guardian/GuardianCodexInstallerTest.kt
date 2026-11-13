package dev.perf.guardian

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Codex target: skills in .agents/skills, TOML personas in .codex/agents, marker block in AGENTS.md. */
class GuardianCodexInstallerTest {
    private lateinit var repository: Path
    private val agentsMd get() = repository.resolve("AGENTS.md")

    @BeforeTest
    fun setUp() {
        repository = Files.createTempDirectory("guardian-codex-test")
    }

    @AfterTest
    fun tearDown() {
        repository.toFile().deleteRecursively()
    }

    @Test
    fun `codex install writes skills personas and the AGENTS md block at Codex destinations`() {
        val result = installer().install(repository, "codex", catalog())

        assertEquals(
            listOf(".agents/skills/android-performance/SKILL.md", ".codex/agents/android-performance-guardian.toml", "AGENTS.md"),
            result.installedPaths,
        )
        assertEquals("skill content", repository.resolve(".agents/skills/android-performance/SKILL.md").readText())
        assertEquals("toml content", repository.resolve(".codex/agents/android-performance-guardian.toml").readText())
        assertEquals(block("v1") + "\n", agentsMd.readText())
        assertFalse(repository.resolve(".claude").exists(), "a codex install must not write Claude files")
    }

    @Test
    fun `receipt records the AGENTS md region hash and the codex target`() {
        installer().install(repository, "codex", catalog())

        val receipt = GuardianInstallReceipt.read(repository.resolve(".guardian-manifest.json"))
        val item = receipt.items.single { it.kind == "docSnippet" }

        assertEquals(listOf("codex"), receipt.targets)
        assertEquals("codex", item.target)
        assertEquals("AGENTS.md", item.path)
        assertEquals(sha256(block("v1").encodeToByteArray()), item.sha256)
    }

    @Test
    fun `existing AGENTS md content is preserved byte for byte before the appended block`() {
        val original = "# Team rules\n\nBe kind.\n"
        agentsMd.writeText(original)

        installer().install(repository, "codex", catalog())

        val text = agentsMd.readText()
        assertTrue(text.startsWith(original), text)
        assertTrue(text.contains(block("v1")), text)
    }

    @Test
    fun `AGENTS md without a trailing newline still keeps the original bytes as a prefix`() {
        agentsMd.writeText("no newline")

        installer().install(repository, "codex", catalog())

        assertTrue(agentsMd.readText().startsWith("no newline"))
    }

    @Test
    fun `reinstall is a no-op and a new block version replaces only the marked region`() {
        agentsMd.writeText("before\n")
        installer().install(repository, "codex", catalog())

        val unchanged = installer().install(repository, "codex", catalog())
        assertTrue(unchanged.installedPaths.isEmpty())

        val upgraded = GuardianInstaller { resource -> payloads(blockVersion = "v2").getValue(resource) }
            .install(repository, "codex", catalog())

        assertEquals(listOf("AGENTS.md"), upgraded.installedPaths)
        val text = agentsMd.readText()
        assertTrue(text.startsWith("before\n"))
        assertTrue(text.contains(block("v2")))
        assertFalse(text.contains(block("v1")))
    }

    @Test
    fun `text after the marked region survives an upgrade`() {
        installer().install(repository, "codex", catalog())
        agentsMd.writeText(agentsMd.readText() + "\nuser footer\n")
        // The region itself is untouched, so the receipt hash still matches.

        GuardianInstaller { resource -> payloads(blockVersion = "v2").getValue(resource) }
            .install(repository, "codex", catalog())

        assertTrue(agentsMd.readText().endsWith("\nuser footer\n"))
        assertTrue(agentsMd.readText().contains(block("v2")))
    }

    @Test
    fun `a user edited region is rejected without changing any file`() {
        installer().install(repository, "codex", catalog())
        agentsMd.writeText(agentsMd.readText().replace("body v1", "body edited by user"))
        val before = snapshot()

        assertFailsWith<GuardianInstallException> {
            GuardianInstaller { resource -> payloads(blockVersion = "v2").getValue(resource) }
                .install(repository, "codex", catalog())
        }

        assertEquals(before, snapshot())
    }

    @Test
    fun `a begin marker without an end marker is rejected and the file is untouched`() {
        agentsMd.writeText("intro\n<!-- guardian:begin -->\ndangling\n")
        val before = snapshot()

        assertFailsWith<GuardianInstallException> { installer().install(repository, "codex", catalog()) }

        assertEquals(before, snapshot())
        assertFalse(repository.resolve(".guardian-manifest.json").exists())
    }

    @Test
    fun `a marked region that Guardian did not install is rejected`() {
        agentsMd.writeText(block("v1") + "\n")

        assertFailsWith<GuardianInstallException> { installer().install(repository, "codex", catalog()) }
    }

    @Test
    fun `claude and codex installs coexist and keep ownership of each other's files`() {
        val claudeFirst = installer().install(repository, "claude", catalog())
        installer().install(repository, "codex", catalog())

        val receipt = GuardianInstallReceipt.read(repository.resolve(".guardian-manifest.json"))
        assertEquals(listOf("claude", "codex"), receipt.targets)
        assertEquals(claudeFirst.installedPaths.size + 3, receipt.items.size)

        val again = installer().install(repository, "claude", catalog())
        assertTrue(again.installedPaths.isEmpty(), "claude files stay owned after a codex install")
    }

    @Test
    fun `an unsupported target is still rejected`() {
        assertFailsWith<GuardianInstallException> { installer().install(repository, "gemini", catalog()) }
    }

    @Test
    fun `a persona without a codex artifact is skipped for codex`() {
        val claudeOnly = catalog().copy(
            personas = catalog().personas.map { it.copy(artifacts = mapOf("claude" to "personas/claude/x.md")) },
        )

        val result = installer().install(repository, "codex", claudeOnly)

        assertEquals(listOf(".agents/skills/android-performance/SKILL.md", "AGENTS.md"), result.installedPaths)
    }

    private fun installer() = GuardianInstaller { resource -> payloads().getValue(resource) }

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
                        artifacts =
                            mapOf(
                                "claude" to "personas/claude/android-performance-guardian.md",
                                "codex" to "personas/codex/android-performance-guardian.toml",
                            ),
                    ),
                ),
            docSnippets =
                listOf(
                    DocSnippetEntry(
                        id = "codex-agents-md",
                        target = "codex",
                        version = "1",
                        path = "AGENTS.md",
                        resource = "snippets/codex-agents-md.md",
                        markerBegin = BEGIN,
                        markerEnd = END,
                    ),
                ),
        )

    private fun block(version: String) = "$BEGIN\nbody $version\n$END"

    private fun payloads(blockVersion: String = "v1") =
        mapOf(
            "skills/android-performance/SKILL.md" to "skill content".encodeToByteArray(),
            "personas/claude/android-performance-guardian.md" to "persona content".encodeToByteArray(),
            "personas/claude/x.md" to "x".encodeToByteArray(),
            "personas/codex/android-performance-guardian.toml" to "toml content".encodeToByteArray(),
            "snippets/codex-agents-md.md" to (block(blockVersion) + "\n").encodeToByteArray(),
        )

    private fun snapshot(): Map<String, String> =
        Files.walk(repository).use { paths ->
            paths.iterator().asSequence().filter { Files.isRegularFile(it) }.sorted()
                .associate { repository.relativize(it).toString() to it.readText() }
        }

    private fun sha256(bytes: ByteArray) =
        java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        const val BEGIN = "<!-- guardian:begin -->"
        const val END = "<!-- guardian:end -->"
    }
}
