package dev.perf.cli.command

import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [resolvedOutputFile] — the pure helper that derives the effective
 * Markdown report output [File] from an [Instant] and an optional explicit path.
 *
 * Strict TDD: all tests written BEFORE [resolvedOutputFile] exists (RED phase).
 */
class ResolvedOutputFileTest {

    // ── Test 1 ─────────────────────────────────────────────────────────────────
    // When an explicit file is provided, it must be returned unchanged.

    @Test
    fun `returnsExplicitPathVerbatim - explicit file returned unchanged`() {
        val explicit = File("custom/path/my-report.md")
        val anyInstant = Instant.parse("2026-05-15T12:34:56Z")

        val result = resolvedOutputFile(anyInstant, explicit)

        assertEquals(explicit, result, "Expected explicit file to be returned unchanged")
    }

    // ── Test 2 ─────────────────────────────────────────────────────────────────
    // Default filename (explicit == null) must start with "report-".

    @Test
    fun `usesTimestampedDefaultWhenNull - default filename starts with report-`() {
        val instant = Instant.parse("2026-05-15T12:34:56Z")

        val result = resolvedOutputFile(instant, null)

        assertTrue(
            result.name.startsWith("report-"),
            "Expected filename to start with 'report-', was: ${result.name}",
        )
    }

    // ── Test 3 ─────────────────────────────────────────────────────────────────
    // Default filename must end with ".md".

    @Test
    fun `usesTimestampedDefaultWhenNull - default filename ends with dot md`() {
        val instant = Instant.parse("2026-05-15T12:34:56Z")

        val result = resolvedOutputFile(instant, null)

        assertTrue(
            result.name.endsWith(".md"),
            "Expected filename to end with '.md', was: ${result.name}",
        )
    }

    // ── Test 4 ─────────────────────────────────────────────────────────────────
    // The embedded timestamp must match the instant in yyyyMMdd-HHmmssX format (UTC).

    @Test
    fun `formatUsesUtcEvenForNonUtcClock - timestamp in filename is UTC formatted`() {
        val instant = Instant.parse("2026-05-15T12:34:56Z")
        val expected = "report-20260515-123456Z.md"

        val result = resolvedOutputFile(instant, null)

        assertEquals(expected, result.name, "Expected filename to embed the UTC timestamp correctly")
    }

    // ── Test 5 ─────────────────────────────────────────────────────────────────
    // Two instants one second apart must produce different filenames.

    @Test
    fun `twoInstantsOneSecondApart - produce different filenames`() {
        val instant1 = Instant.parse("2026-05-15T12:34:56Z")
        val instant2 = Instant.parse("2026-05-15T12:34:57Z")

        val result1 = resolvedOutputFile(instant1, null)
        val result2 = resolvedOutputFile(instant2, null)

        assertNotEquals(result1.name, result2.name, "Expected different filenames for instants 1 second apart")
    }

    // ── Test 6 ─────────────────────────────────────────────────────────────────
    // Midnight UTC produces a valid, correctly formatted filename.

    @Test
    fun `midnightUtcProducesValidFilename - edge case at midnight`() {
        val instant = Instant.parse("2026-01-01T00:00:00Z")
        val expected = "report-20260101-000000Z.md"

        val result = resolvedOutputFile(instant, null)

        assertEquals(expected, result.name, "Expected midnight UTC to produce a valid filename")
    }
}
