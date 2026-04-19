package dev.perf.report.source

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for [SourceLinker].
 *
 * We create a temporary file tree that mimics an Android project layout so
 * tests are self-contained and don't depend on the repo on disk.
 */
class SourceLinkerTest {

    // ── File resolution ───────────────────────────────────────────────────────

    @Test
    fun `resolves Kotlin class to file under app-src-main-kotlin`() {
        val root = fakeProject {
            kotlinFile("com/example/app/MainActivity.kt")
        }
        val linker = SourceLinker(root)

        val file = linker.resolveFile("com.example.app.MainActivity")

        assertNotNull(file)
        assertTrue(file.exists())
    }

    @Test
    fun `resolves Java class to file under app-src-main-java`() {
        val root = fakeProject {
            javaFile("com/example/app/LegacyHelper.java")
        }
        val linker = SourceLinker(root)

        val file = linker.resolveFile("com.example.app.LegacyHelper")

        assertNotNull(file)
        assertTrue(file.exists())
    }

    @Test
    fun `returns null when class does not exist in project`() {
        val root = fakeProject {}
        val linker = SourceLinker(root)

        val file = linker.resolveFile("com.example.app.NonExistent")

        assertNull(file)
    }

    @Test
    fun `strips inner class suffix before resolving`() {
        val root = fakeProject {
            kotlinFile("com/example/app/Outer.kt")
        }
        val linker = SourceLinker(root)

        // com.example.app.Outer$Inner → look for com/example/app/Outer.kt
        val file = linker.resolveFile("com.example.app.Outer\$Inner")

        assertNotNull(file)
    }

    // ── IDE link generation ───────────────────────────────────────────────────

    @Test
    fun `linkFor returns idea URL with correct file path and line`() {
        val root = fakeProject {
            kotlinFile("com/example/app/MainActivity.kt")
        }
        val linker = SourceLinker(root)
        val frame = StackFrameParser.parse(
            "\tat com.example.app.MainActivity.onCreate(MainActivity.kt:42)"
        )!!

        val url = linker.linkFor(frame)

        assertNotNull(url)
        assertTrue(url.startsWith("idea://open?file="))
        assertTrue(url.contains("MainActivity.kt"))
        assertTrue(url.endsWith("&line=42"))
    }

    @Test
    fun `linkFor returns null when file does not resolve`() {
        val root = fakeProject {}
        val linker = SourceLinker(root)
        val frame = StackFrameParser.parse(
            "\tat com.example.Missing.method(Missing.kt:1)"
        )!!

        assertNull(linker.linkFor(frame))
    }

    // ── Root detection ────────────────────────────────────────────────────────

    @Test
    fun `detectProjectRoot finds root by build-gradle-kts marker`() {
        val root = Files.createTempDirectory("perf-test-root").toFile()
        File(root, "build.gradle.kts").createNewFile()
        val nested = File(root, "app/src/main/kotlin").also { it.mkdirs() }

        val detected = SourceLinker.detectProjectRoot(from = nested)

        assertTrue(detected.canonicalPath == root.canonicalPath)
        root.deleteRecursively()
    }

    @Test
    fun `detectProjectRoot finds root by git marker`() {
        val root = Files.createTempDirectory("perf-test-root").toFile()
        File(root, ".git").mkdir()
        val nested = File(root, "module/src").also { it.mkdirs() }

        val detected = SourceLinker.detectProjectRoot(from = nested)

        assertTrue(detected.canonicalPath == root.canonicalPath)
        root.deleteRecursively()
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun fakeProject(block: ProjectBuilder.() -> Unit): File {
        val root = Files.createTempDirectory("perf-test-project").toFile()
        File(root, "build.gradle.kts").createNewFile()
        ProjectBuilder(root).block()
        return root
    }

    private class ProjectBuilder(private val root: File) {
        fun kotlinFile(relativePath: String) {
            val file = File(root, "app/src/main/kotlin/$relativePath")
            file.parentFile.mkdirs()
            file.createNewFile()
        }

        fun javaFile(relativePath: String) {
            val file = File(root, "app/src/main/java/$relativePath")
            file.parentFile.mkdirs()
            file.createNewFile()
        }
    }
}
