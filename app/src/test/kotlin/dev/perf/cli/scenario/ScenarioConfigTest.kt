package dev.perf.cli.scenario

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScenarioConfigTest {

    // ── Defaults ──────────────────────────────────────────────────────────────

    @Test
    fun `loads minimal scenario with defaults applied`() {
        val file = scenarioFile("""{ "name": "smoke" }""")
        val config = loadScenario(file)

        assertEquals("smoke", config.name)
        assertEquals(30, config.durationSeconds)
        assertTrue(config.engines.strictMode)
        assertTrue(config.engines.rendering)
        assertFalse(config.engines.heap)
        assertEquals(5.0, config.thresholds.maxJankyPercent)
        assertEquals(0, config.thresholds.maxFrozenFrames)
        assertEquals(0, config.thresholds.maxStrictModeViolations)
        assertEquals(50, config.thresholds.maxPotentialAnrHints)
        assertTrue(config.engines.anrHints)
        assertFalse(config.engines.perfetto)
        assertFalse(config.engines.startup)
        assertEquals(null, config.thresholds.maxColdStartMs)
    }

    // ── Full scenario ─────────────────────────────────────────────────────────

    @Test
    fun `loads all fields from a complete scenario file`() {
        val file = scenarioFile(
            """
            {
              "name": "checkout-flow",
              "durationSeconds": 60,
              "engines": {
                "strictMode": true,
                "rendering": true,
                "heap": true
              },
              "thresholds": {
                "maxJankyPercent": 2.0,
                "maxFrozenFrames": 0,
                "maxStrictModeViolations": 0
              }
            }
            """.trimIndent()
        )
        val config = loadScenario(file)

        assertEquals("checkout-flow", config.name)
        assertEquals(60, config.durationSeconds)
        assertTrue(config.engines.heap)
        assertEquals(2.0, config.thresholds.maxJankyPercent)
    }

    @Test
    fun `loads startup engine and cold start threshold`() {
        val file = scenarioFile(
            """
            {
              "name": "ci-startup",
              "engines": { "startup": true },
              "thresholds": { "maxColdStartMs": 2500 }
            }
            """.trimIndent(),
        )
        val config = loadScenario(file)
        assertTrue(config.engines.startup)
        assertEquals(2500, config.thresholds.maxColdStartMs)
    }

    @Test
    fun `ignores unknown keys without throwing`() {
        val file = scenarioFile(
            """{ "name": "test", "futureField": "ignored" }"""
        )
        // Should not throw — ignoreUnknownKeys = true in the Json instance
        val config = loadScenario(file)
        assertEquals("test", config.name)
    }

    // ── Engine opt-out ────────────────────────────────────────────────────────

    @Test
    fun `can disable individual engines`() {
        val file = scenarioFile(
            """
            {
              "name": "rendering-only",
              "engines": { "strictMode": false, "rendering": true, "heap": false }
            }
            """.trimIndent()
        )
        val config = loadScenario(file)

        assertFalse(config.engines.strictMode)
        assertTrue(config.engines.rendering)
        assertFalse(config.engines.heap)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun scenarioFile(content: String): File {
        val file = Files.createTempFile("scenario", ".json").toFile()
        file.writeText(content)
        file.deleteOnExit()
        return file
    }
}
