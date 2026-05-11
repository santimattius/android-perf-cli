package dev.perf.cli.history

import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * T-3.1 RED: Tests for HistoryStore using a real temp directory.
 *
 * Real filesystem (no fakes) — mirrors PerfConfigTest convention.
 * All tests use a temp directory cleaned up in @AfterTest.
 */
class HistoryStoreTest {

    private lateinit var baseDir: java.io.File

    @BeforeTest
    fun setUp() {
        baseDir = Files.createTempDirectory("history-store-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        baseDir.deleteRecursively()
    }

    private fun entry(
        pkg: String = "com.example.app",
        runId: String = "2026-05-10T12:00:00Z-aaa",
        timestampIso: String = "2026-05-10T12:00:00Z",
        anrCount: Int = 0,
        crashCount: Int = 0,
        totalFrames: Int = 100,
        slowFrames: Int = 0,
        frozenFrames: Int = 0,
        exitSignalsEnabled: Boolean = true,
    ) = SessionHistoryEntry(
        packageName = pkg,
        runId = runId,
        timestampIso = timestampIso,
        anrCount = anrCount,
        crashCount = crashCount,
        totalFrames = totalFrames,
        slowFrames = slowFrames,
        frozenFrames = frozenFrames,
        exitSignalsEnabled = exitSignalsEnabled,
    )

    // Group 1: append creates directory and writes file
    @Test
    fun `append creates directory on first use and writes valid JSON file`() {
        val store = HistoryStore(baseDir)
        val e = entry(runId = "2026-05-10T12:00:00Z-aaa111")

        store.append(e)

        val dir = store.historyDir(e.packageName)
        assertTrue(dir.exists(), "historyDir should exist after append")
        val files = dir.listFiles() ?: emptyArray()
        assertEquals(1, files.size, "Expected exactly one file after one append")
        assertTrue(files[0].length() > 0, "Written file should not be empty")
    }

    // Group 2: files sorted chronologically
    @Test
    fun `loadWindow returns entries in chronological ascending order`() {
        val store = HistoryStore(baseDir)

        // Write two entries with different ISO timestamps (lexicographic sort = chronological)
        val e1 = entry(runId = "2026-05-10T11:00:00Z-bbb111", timestampIso = "2026-05-10T11:00:00Z", anrCount = 1)
        val e2 = entry(runId = "2026-05-10T12:00:00Z-bbb222", timestampIso = "2026-05-10T12:00:00Z", anrCount = 2)

        store.append(e1)
        store.append(e2)

        val loaded = store.loadWindow("com.example.app", windowSize = 30)
        assertEquals(2, loaded.size)
        assertEquals(1, loaded[0].anrCount, "First entry should be the earlier one")
        assertEquals(2, loaded[1].anrCount, "Second entry should be the later one")
    }

    // Group 3: auto-prune removes oldest files beyond maxEntries
    @Test
    fun `auto-prune keeps exactly maxEntries files after overflow`() {
        val store = HistoryStore(baseDir)

        // Write 4 entries with maxEntries=3
        store.append(entry(runId = "2026-05-10T09:00:00Z-ccc111", anrCount = 1), maxEntries = 3)
        store.append(entry(runId = "2026-05-10T10:00:00Z-ccc222", anrCount = 2), maxEntries = 3)
        store.append(entry(runId = "2026-05-10T11:00:00Z-ccc333", anrCount = 3), maxEntries = 3)
        store.append(entry(runId = "2026-05-10T12:00:00Z-ccc444", anrCount = 4), maxEntries = 3)

        val dir = store.historyDir("com.example.app")
        val files = dir.listFiles()?.sortedBy { it.name } ?: emptyList()
        assertEquals(3, files.size, "Expected exactly 3 files after prune with maxEntries=3")

        // Oldest (first written = lexicographically smallest name) should be gone
        assertFalse(
            files.any { it.name.startsWith("2026-05-10T09:00:00Z") },
            "Oldest file should have been pruned",
        )
    }

    // Group 4: loadWindow skips corrupt files silently
    @Test
    fun `loadWindow skips corrupt files silently without throwing`() {
        val store = HistoryStore(baseDir)

        // Write a valid entry
        store.append(entry(runId = "2026-05-10T12:00:00Z-ddd111", anrCount = 5))

        // Manually write a corrupt (non-JSON) file
        val dir = store.historyDir("com.example.app")
        dir.mkdirs()
        java.io.File(dir, "2026-05-10T10:00:00Z-corrupt.json").writeText("THIS IS NOT JSON {{{")

        // Should not throw; corrupt file excluded from results
        val loaded = store.loadWindow("com.example.app", windowSize = 30)
        assertEquals(1, loaded.size, "Only valid entry should be returned")
        assertEquals(5, loaded[0].anrCount)
    }

    // Triangulation: defaultBaseDir returns expected path
    @Test
    fun `defaultBaseDir returns dot config perf-cli history path`() {
        val dir = HistoryStore.defaultBaseDir()
        val home = System.getProperty("user.home")
        assertTrue(dir.path.endsWith(".config/perf-cli/history"), "Expected XDG config path, got: ${dir.path}")
        assertTrue(dir.path.startsWith(home), "Expected path under home dir")
    }
}
