package dev.perf.cli.scenario

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests for [resolveRunParameters] — specifically for vitals profile merge behaviour.
 *
 * Design decision D1: `profile: "vitals"` causes VitalsDefaults to WIN over any user-supplied threshold.
 * This is the confirmed decision and contradicts the original spec statement of "user wins".
 */
class ScenarioResolutionTest {

    // ── Vitals profile: VitalsDefaults WIN ────────────────────────────────────

    @Test
    fun `profile vitals with explicit maxColdStartMs — vitals wins with 5000`() {
        val scenario = ScenarioConfig(
            name = "vitals-test",
            profile = "vitals",
            thresholds = ThresholdConfig(maxColdStartMs = 3000),
        )
        val resolved = resolveRunParameters(scenario, fullCli = false, cliDurationSeconds = 30)

        assertEquals(5000, resolved.thresholds.maxColdStartMs)
    }

    @Test
    fun `profile vitals sets all startup thresholds from VitalsDefaults`() {
        val scenario = ScenarioConfig(
            name = "vitals-test",
            profile = "vitals",
            thresholds = ThresholdConfig(),
        )
        val resolved = resolveRunParameters(scenario, fullCli = false, cliDurationSeconds = 30)

        assertEquals(5000, resolved.thresholds.maxColdStartMs)
        assertEquals(2000, resolved.thresholds.maxWarmStartMs)
        assertEquals(1500, resolved.thresholds.maxHotStartMs)
    }

    @Test
    fun `profile vitals sets wakelock session fields from VitalsDefaults`() {
        val scenario = ScenarioConfig(
            name = "vitals-test",
            profile = "vitals",
            thresholds = ThresholdConfig(),
        )
        val resolved = resolveRunParameters(scenario, fullCli = false, cliDurationSeconds = 30)

        assertEquals(5.0, resolved.thresholds.maxWakelockSessionPercent)
        assertEquals(3_600_000L, resolved.thresholds.maxWakelockLongHeldMs)
    }

    // ── No profile: user thresholds preserved verbatim ─────────────────────────

    @Test
    fun `no profile preserves user cold start threshold verbatim`() {
        val scenario = ScenarioConfig(
            name = "custom",
            profile = null,
            thresholds = ThresholdConfig(maxColdStartMs = 2500),
        )
        val resolved = resolveRunParameters(scenario, fullCli = false, cliDurationSeconds = 30)

        assertEquals(2500, resolved.thresholds.maxColdStartMs)
    }

    @Test
    fun `no profile preserves null thresholds as null`() {
        val scenario = ScenarioConfig(
            name = "custom",
            profile = null,
            thresholds = ThresholdConfig(maxColdStartMs = null),
        )
        val resolved = resolveRunParameters(scenario, fullCli = false, cliDurationSeconds = 30)

        assertNull(resolved.thresholds.maxColdStartMs)
    }

    // ── Full profile: thresholds unchanged ────────────────────────────────────

    @Test
    fun `profile full does not affect thresholds — only engines`() {
        val scenario = ScenarioConfig(
            name = "full-test",
            profile = "full",
            thresholds = ThresholdConfig(maxColdStartMs = 3000),
        )
        val resolved = resolveRunParameters(scenario, fullCli = false, cliDurationSeconds = 30)

        // Full profile should not touch thresholds
        assertEquals(3000, resolved.thresholds.maxColdStartMs)
    }

    // ── Task 2.3: resolved.thresholds carries merged values ──────────────────

    @Test
    fun `resolved thresholds field carries vitals-merged values when profile is vitals`() {
        val scenario = ScenarioConfig(
            name = "vitals",
            profile = "vitals",
            thresholds = ThresholdConfig(maxColdStartMs = 999),
        )
        val resolved = resolveRunParameters(scenario, fullCli = false, cliDurationSeconds = 30)

        // The resolved config must expose merged thresholds
        assertEquals(5000, resolved.thresholds.maxColdStartMs)
    }

    // ── Task 2.5: Regression — no profile produces identical ThresholdConfig ─

    @Test
    fun `scenario without profile field produces unchanged ThresholdConfig`() {
        val thresholds = ThresholdConfig(
            maxJankyPercent = 3.0,
            maxFrozenFrames = 2,
            maxStrictModeViolations = 1,
            maxColdStartMs = 2000,
            maxWakelockTimeMs = 30000L,
            maxWakelockCount = 3,
        )
        val scenario = ScenarioConfig(name = "legacy", thresholds = thresholds)
        val resolved = resolveRunParameters(scenario, fullCli = false, cliDurationSeconds = 30)

        assertEquals(3.0, resolved.thresholds.maxJankyPercent)
        assertEquals(2, resolved.thresholds.maxFrozenFrames)
        assertEquals(1, resolved.thresholds.maxStrictModeViolations)
        assertEquals(2000, resolved.thresholds.maxColdStartMs)
        assertEquals(30000L, resolved.thresholds.maxWakelockTimeMs)
        assertEquals(3, resolved.thresholds.maxWakelockCount)
        assertNull(resolved.thresholds.maxWakelockSessionPercent)
        assertNull(resolved.thresholds.maxWakelockLongHeldMs)
    }

    @Test
    fun `profile vitals case-insensitive — VITALS also applies defaults`() {
        val scenario = ScenarioConfig(
            name = "vitals-upper",
            profile = "VITALS",
            thresholds = ThresholdConfig(maxColdStartMs = 1000),
        )
        val resolved = resolveRunParameters(scenario, fullCli = false, cliDurationSeconds = 30)

        assertEquals(5000, resolved.thresholds.maxColdStartMs)
    }
}
