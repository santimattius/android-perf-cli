package dev.perf.cli.command

import com.github.ajalt.clikt.testing.test
import dev.perf.cli.history.HistoryStore
import dev.perf.cli.history.SessionHistoryEntry
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [HistoryCommand] using Clikt's `CliktCommand.test()` harness.
 *
 * Each test populates a temp dir via [HistoryStore.append] before invoking the command.
 * The command always exits with code 0 — rates are advisory only (not breach exits).
 *
 * Written as the RED step of the TDD cycle — HistoryCommand does not exist yet.
 */
class HistoryCommandTest {

    private lateinit var tempDir: File

    @BeforeTest
    fun setUp() {
        tempDir = Files.createTempDirectory("history-cmd-test").toFile()
    }

    @AfterTest
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private val pkg = "com.example.testapp"

    private fun storeEntry(
        store: HistoryStore,
        anrCount: Int = 0,
        crashCount: Int = 0,
        totalFrames: Int = 1000,
        slowFrames: Int = 0,
        index: Int = 0,
    ) {
        store.append(
            SessionHistoryEntry(
                packageName = pkg,
                runId = "2026-05-10T12:0${index}:00Z-run$index",
                timestampIso = "2026-05-10T12:0${index}:00Z",
                anrCount = anrCount,
                crashCount = crashCount,
                totalFrames = totalFrames,
                slowFrames = slowFrames,
                frozenFrames = 0,
                exitSignalsEnabled = true,
            ),
        )
    }

    private fun runHistoryCommand(
        packageName: String = pkg,
        historyDir: File = tempDir,
    ) = HistoryCommand().test(
        "--package $packageName --history-dir ${historyDir.absolutePath}",
    )

    // ── T-7.1.1: ≥2 entries → stdout contains table header ───────────────────

    @Test
    fun `with two or more entries stdout contains rate table`() {
        val store = HistoryStore(tempDir)
        storeEntry(store, index = 0)
        storeEntry(store, index = 1)

        val result = runHistoryCommand()

        assertTrue(result.output.contains("| Metric | Rate |"), "Table header must appear in output")
    }

    // ── T-7.1.2: --history-dir override reads from temp dir ───────────────────

    @Test
    fun `history-dir override reads from custom directory`() {
        val store = HistoryStore(tempDir)
        storeEntry(store, index = 0)
        storeEntry(store, index = 1)

        val result = runHistoryCommand(historyDir = tempDir)

        // Command must have run and produced a table (proving it read from tempDir, not default)
        assertTrue(result.output.contains("| Metric | Rate |"))
    }

    // ── T-7.1.3: rates below threshold → PASS in status column ───────────────

    @Test
    fun `all rates below threshold produces PASS status`() {
        val store = HistoryStore(tempDir)
        // 5 entries with zero ANR, zero crash, zero slow frames → all rates 0.0 → PASS
        repeat(5) { i -> storeEntry(store, index = i) }

        val result = runHistoryCommand()

        assertTrue(result.output.contains("PASS"), "All rates at 0.0 must produce PASS status")
    }

    // ── T-7.1.4: all entries with anrCount ≥ 1 → OVER in status column ───────

    @Test
    fun `all entries with anrCount at least 1 produces OVER status`() {
        val store = HistoryStore(tempDir)
        // 5 entries all with anrCount=1 → anrRate=100% >> 0.47% gate → OVER
        repeat(5) { i -> storeEntry(store, anrCount = 1, index = i) }

        val result = runHistoryCommand()

        assertTrue(result.output.contains("OVER"), "ANR rate at 100% must produce OVER status")
    }

    // ── T-7.1.5: Play Console limitations banner appears ─────────────────────

    @Test
    fun `output contains Play Console limitations banner text`() {
        val store = HistoryStore(tempDir)
        storeEntry(store, index = 0)
        storeEntry(store, index = 1)

        val result = runHistoryCommand()

        assertTrue(
            result.output.contains("Android Vitals"),
            "Play Console limitations banner must appear in output",
        )
    }

    // ── T-7.1.6: exit code is 0 even when rates are above threshold ───────────

    @Test
    fun `exit code is 0 even when ANR rate is above threshold`() {
        val store = HistoryStore(tempDir)
        // All entries with high ANR count → above threshold, but history is advisory only
        repeat(5) { i -> storeEntry(store, anrCount = 1, index = i) }

        val result = runHistoryCommand()

        assertEquals(0, result.statusCode, "HistoryCommand must always exit with code 0 (advisory only)")
    }

    // ── T-7.1.7: empty history produces graceful output ───────────────────────

    @Test
    fun `empty history produces informational output without crashing`() {
        // No entries — should not throw, should produce a usable message
        val result = runHistoryCommand()

        // Must exit cleanly
        assertEquals(0, result.statusCode, "Empty history must exit 0")
        // Must not be blank output
        assertFalse(result.output.isBlank(), "Output must not be blank for empty history")
    }
}
