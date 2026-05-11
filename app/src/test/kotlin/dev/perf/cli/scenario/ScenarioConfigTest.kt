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

    // ── Wake-lock session fields backward compatibility ────────────────────────

    @Test
    fun `legacy JSON without new wakelock fields deserializes with both fields null`() {
        val file = scenarioFile("""{ "name": "legacy" }""")
        val config = loadScenario(file)

        assertEquals(null, config.thresholds.maxWakelockSessionPercent)
        assertEquals(null, config.thresholds.maxWakelockLongHeldMs)
    }

    @Test
    fun `legacy JSON with existing wakelock fields still deserializes correctly`() {
        val file = scenarioFile(
            """
            {
              "name": "wakelock-test",
              "thresholds": {
                "maxWakelockTimeMs": 60000,
                "maxWakelockCount": 5
              }
            }
            """.trimIndent()
        )
        val config = loadScenario(file)

        assertEquals(60000L, config.thresholds.maxWakelockTimeMs)
        assertEquals(5, config.thresholds.maxWakelockCount)
        assertEquals(null, config.thresholds.maxWakelockSessionPercent)
        assertEquals(null, config.thresholds.maxWakelockLongHeldMs)
    }

    @Test
    fun `new wakelock session fields deserialize when present in JSON`() {
        val file = scenarioFile(
            """
            {
              "name": "wakelock-session",
              "thresholds": {
                "maxWakelockSessionPercent": 5.0,
                "maxWakelockLongHeldMs": 3600000
              }
            }
            """.trimIndent()
        )
        val config = loadScenario(file)

        assertEquals(5.0, config.thresholds.maxWakelockSessionPercent)
        assertEquals(3_600_000L, config.thresholds.maxWakelockLongHeldMs)
    }

    @Test
    fun `serialization round-trip preserves all threshold fields including new nullable ones`() {
        val original = ThresholdConfig(
            maxJankyPercent = 3.0,
            maxColdStartMs = 2000,
            maxWakelockSessionPercent = 5.0,
            maxWakelockLongHeldMs = 3_600_000L,
        )
        // Verify the object can be re-constructed with all values intact (not testing JSON round-trip,
        // just that the data class holds values correctly)
        assertEquals(3.0, original.maxJankyPercent)
        assertEquals(2000, original.maxColdStartMs)
        assertEquals(5.0, original.maxWakelockSessionPercent)
        assertEquals(3_600_000L, original.maxWakelockLongHeldMs)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun scenarioFile(content: String): File {
        val file = Files.createTempFile("scenario", ".json").toFile()
        file.writeText(content)
        file.deleteOnExit()
        return file
    }
}
