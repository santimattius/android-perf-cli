package dev.perf.cli.command

import com.github.ajalt.clikt.testing.test
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Routing tests for [AnalyzeCommand].
 *
 * The parent command uses `invokeWithoutSubcommand = true` so that it can print
 * a custom usage hint when called with no domain. The fix ensures the parent's
 * run() is a no-op when a domain subcommand is present — only the no-subcommand
 * path should exit 2.
 */
class AnalyzeCommandRoutingTest {

    @Test
    fun `analyze without subcommand exits 2 with usage hint`() {
        val result = AnalyzeCommand().test("")

        assertEquals(2, result.statusCode)
        assertTrue(
            result.output.contains("Usage: perf analyze"),
            "Expected usage hint in output, got: '${result.output}'",
        )
    }

    @Test
    fun `analyze with domain subcommand does not exit 2 from parent routing`() {
        // The parent's run() must be a no-op when a domain is given.
        // Without a trace file the domain itself exits with a missing-argument
        // error (statusCode != 2 and a different error message than the parent's).
        val result = AnalyzeCommand().test("rendering")

        assertFalse(
            result.statusCode == 2 && result.output.contains("Usage: perf analyze <startup|rendering|memory|cpu>"),
            "Parent routing message must not appear when a domain subcommand is present",
        )
    }

    @Test
    fun `analyze startup subcommand does not exit 2 from parent routing`() {
        val result = AnalyzeCommand().test("startup")

        assertFalse(
            result.statusCode == 2 && result.output.contains("Usage: perf analyze <startup|rendering|memory|cpu>"),
            "Parent routing message must not appear for 'startup' domain",
        )
    }
}
