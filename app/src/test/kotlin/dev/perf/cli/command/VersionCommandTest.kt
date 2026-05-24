package dev.perf.cli.command

import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.testing.test
import dev.perf.cli.BuildConfig
import dev.perf.cli.PerfCli
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests for [VersionCommand] using Clikt's `CliktCommand.test()` harness.
 *
 * [BuildConfig.VERSION] is a `const val` resolved at compile time, so no
 * mocks, fakes, or test doubles are needed.
 *
 * Covers spec requirements:
 *   T-version-1: perf version prints the version string to stdout, exit 0
 *   T-version-2: output is exactly BuildConfig.VERSION (no extra whitespace on its own line)
 *   T-version-3: exit code is 0
 */
class VersionCommandTest {

    // ── T-version-1: output contains the version string ──────────────────────

    @Test
    fun `version command prints BuildConfig VERSION to stdout`() {
        val result = VersionCommand().test("")
        assertTrue(
            result.output.contains(BuildConfig.VERSION),
            "stdout must contain '${BuildConfig.VERSION}', got: '${result.output}'",
        )
    }

    // ── T-version-2: output equals VERSION (trimmed) ──────────────────────────

    @Test
    fun `version command output trimmed equals BuildConfig VERSION`() {
        val result = VersionCommand().test("")
        assertEquals(
            BuildConfig.VERSION,
            result.output.trim(),
            "stdout trimmed must equal '${BuildConfig.VERSION}'",
        )
    }

    // ── T-version-3: exit code is 0 ───────────────────────────────────────────

    @Test
    fun `version command exits with code 0`() {
        val result = VersionCommand().test("")
        assertEquals(0, result.statusCode, "VersionCommand must exit with code 0")
    }

    // ── T-version-4: root --version flag ─────────────────────────────────────

    @Test
    fun `perf --version prints version and exits 0`() {
        val result = PerfCli().subcommands(VersionCommand()).test("--version")
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains(BuildConfig.VERSION))
    }

    // ── T-version-5: root -V short flag ──────────────────────────────────────

    @Test
    fun `perf -V prints version and exits 0`() {
        val result = PerfCli().subcommands(VersionCommand()).test("-V")
        assertEquals(0, result.statusCode)
        assertTrue(result.output.contains(BuildConfig.VERSION))
    }
}
