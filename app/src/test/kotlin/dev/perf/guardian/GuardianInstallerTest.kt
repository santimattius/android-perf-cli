package dev.perf.guardian

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.FileTime
import java.security.MessageDigest
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.getLastModifiedTime
import kotlin.io.path.readBytes
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardianInstallerTest {
    private lateinit var repository: Path

    @BeforeTest
    fun setUp() {
        repository = Files.createTempDirectory("guardian-installer-test")
    }

    @AfterTest
    fun tearDown() {
        repository.toFile().deleteRecursively()
    }

    @Test
    fun `install publishes the selected skill and persona at Claude standard destinations`() {
        val result = installer().install(repository, "claude", catalog())

        assertEquals(
            listOf(
                ".claude/skills/android-performance/SKILL.md",
                ".claude/agents/android-performance-guardian.md",
            ),
            result.installedPaths,
        )
        assertEquals("skill content", repository.resolve(".claude/skills/android-performance/SKILL.md").readText())
        assertEquals("persona content", repository.resolve(".claude/agents/android-performance-guardian.md").readText())
        assertTrue(repository.resolve(".guardian-manifest.json").exists())
    }

    @Test
    fun `install writes a receipt with catalog target item versions normalized paths and hashes`() {
        installer().install(repository, "claude", catalog())

        val receipt = GuardianInstallReceipt.read(repository.resolve(".guardian-manifest.json"))

        assertEquals("v-test", receipt.guardianVersion)
        assertEquals(listOf("claude"), receipt.targets)
        assertEquals(
            listOf(
                GuardianInstalledItem(
                    kind = "skill",
                    target = "claude",
                    id = "android-performance",
                    version = "1.2.3",
                    path = ".claude/skills/android-performance/SKILL.md",
                    sha256 = sha256("skill content".encodeToByteArray()),
                ),
                GuardianInstalledItem(
                    kind = "persona",
                    target = "claude",
                    id = "android-performance-guardian",
                    version = "4.5.6",
                    path = ".claude/agents/android-performance-guardian.md",
                    sha256 = sha256("persona content".encodeToByteArray()),
                ),
            ),
            receipt.items,
        )
    }

    @Test
    fun `unowned and modified managed destinations reject without changing files or receipt`() {
        val unownedRepository = Files.createTempDirectory("guardian-unowned-test")
        try {
            val unownedPath = unownedRepository.resolve(".claude/agents/android-performance-guardian.md")
            unownedPath.parent.createDirectories()
            unownedPath.writeText("user-owned")
            val beforeUnowned = snapshot(unownedRepository)

            assertFailsWith<GuardianInstallException> {
                installer().install(unownedRepository, "claude", catalog())
            }

            assertEquals(beforeUnowned, snapshot(unownedRepository))
            assertFalse(unownedRepository.resolve(".guardian-manifest.json").exists())
        } finally {
            unownedRepository.toFile().deleteRecursively()
        }

        installer().install(repository, "claude", catalog())
        val managed = repository.resolve(".claude/skills/android-performance/SKILL.md")
        managed.writeText("locally modified")
        val beforeModified = snapshot(repository)

        assertFailsWith<GuardianInstallException> {
            installer().install(repository, "claude", catalog())
        }

        assertEquals(beforeModified, snapshot(repository))
        assertEquals("locally modified", managed.readText())
    }

    @Test
    fun `invalid plans and symlink escapes reject before changing the repository`() {
        val invalidCatalogs =
            listOf(
                catalog(skillResource = "/absolute/SKILL.md"),
                catalog(skillResource = "../traversal/SKILL.md"),
                catalog(skillId = "escape/skill"),
                catalog(
                    skills =
                        listOf(
                            SkillEntry("android-performance", "1.2.3", "skills/one/SKILL.md"),
                            SkillEntry("android-performance", "1.2.4", "skills/two/SKILL.md"),
                        ),
                ),
                catalog(personaRequiresSkills = listOf("missing-skill")),
            )

        invalidCatalogs.forEachIndexed { index, invalidCatalog ->
            val invalidRepository = Files.createTempDirectory("guardian-invalid-$index")
            try {
                invalidRepository.resolve("keep.txt").writeText("unchanged")
                val before = snapshot(invalidRepository)

                assertFailsWith<GuardianInstallException> {
                    installer().install(invalidRepository, "claude", invalidCatalog)
                }

                assertEquals(before, snapshot(invalidRepository))
            } finally {
                invalidRepository.toFile().deleteRecursively()
            }
        }

        val outside = Files.createTempDirectory("guardian-outside")
        try {
            outside.resolve("keep.txt").writeText("outside")
            Files.createSymbolicLink(repository.resolve(".claude"), outside)
            val beforeRepository = snapshot(repository)
            val beforeOutside = snapshot(outside)

            assertFailsWith<GuardianInstallException> {
                installer().install(repository, "claude", catalog())
            }

            assertEquals(beforeRepository, snapshot(repository))
            assertEquals(beforeOutside, snapshot(outside))
            assertFalse(outside.resolve("skills/android-performance/SKILL.md").exists())
        } finally {
            outside.toFile().deleteRecursively()
        }
    }

    @Test
    fun `payload staging failure leaves payloads and receipt unchanged`() {
        val reader: (String) -> ByteArray = { resource ->
            if (resource == "personas/claude/android-performance-guardian.md") {
                error("missing staged payload")
            }
            payloads.getValue(resource)
        }
        repository.resolve("keep.txt").writeText("unchanged")
        val before = snapshot(repository)

        assertFailsWith<GuardianInstallException> {
            GuardianInstaller(reader).install(repository, "claude", catalog())
        }

        assertEquals(before, snapshot(repository))
    }

    @Test
    fun `unchanged reinstall succeeds without rewriting payloads or receipt`() {
        val first = installer().install(repository, "claude", catalog())
        val paths = first.installedPaths.map(repository::resolve) + listOf(repository.resolve(".guardian-manifest.json"))
        val originalTime = FileTime.fromMillis(1_000)
        paths.forEach { Files.setLastModifiedTime(it, originalTime) }
        val before = paths.associateWith { it.readBytes() }

        val second = installer().install(repository, "claude", catalog())

        assertEquals(emptyList(), second.installedPaths)
        assertEquals(first.installedPaths, second.unchangedPaths)
        paths.forEach { path ->
            assertContentEquals(before.getValue(path), path.readBytes())
            assertEquals(originalTime, path.getLastModifiedTime())
        }
    }

    private fun installer() = GuardianInstaller { resource -> payloads.getValue(resource) }

    private fun catalog(
        skillId: String = "android-performance",
        skillResource: String = "skills/android-performance/SKILL.md",
        personaRequiresSkills: List<String> = listOf("android-performance"),
        skills: List<SkillEntry>? = null,
    ) = GuardianManifest(
        guardianVersion = "v-test",
        skills = skills ?: listOf(SkillEntry(skillId, "1.2.3", skillResource)),
        personas =
            listOf(
                PersonaEntry(
                    id = "android-performance-guardian",
                    version = "4.5.6",
                    domain = "performance",
                    requiresSkills = personaRequiresSkills,
                    artifacts = mapOf("claude" to "personas/claude/android-performance-guardian.md"),
                ),
            ),
    )

    private fun snapshot(root: Path): Map<String, String> =
        Files.walk(root).use { paths ->
            paths
                .iterator()
                .asSequence()
                .filter { Files.isRegularFile(it) }
                .sorted()
                .associate { root.relativize(it).toString() to sha256(it.readBytes()) }
        }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private companion object {
        val payloads =
            mapOf(
                "skills/android-performance/SKILL.md" to "skill content".encodeToByteArray(),
                "personas/claude/android-performance-guardian.md" to "persona content".encodeToByteArray(),
                "skills/one/SKILL.md" to "one".encodeToByteArray(),
                "skills/two/SKILL.md" to "two".encodeToByteArray(),
            )
    }
}
