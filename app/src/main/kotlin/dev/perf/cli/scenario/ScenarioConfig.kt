package dev.perf.cli.scenario

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

/**
 * Declarative configuration for a monitoring scenario.
 *
 * Design decision: the scenario is a plain JSON file (not flags) so that CI
 * pipelines can version-control per-screen profiles alongside the test suite.
 * A flag-based API would require rebuilding command strings; a JSON file is
 * committable, diffable, and shareable across team members.
 *
 * Design decision: most engine flags default to true. **heap**, **perfetto**,
 * and **startup** default to false (heavy / disruptive). Opt-out remains the
 * default for strictMode, rendering, and anrHints.
 *
 * Example scenario file:
 * ```json
 * {
 *   "name": "checkout-flow",
 *   "durationSeconds": 60,
 *   "engines": {
 *     "strictMode": true,
 *     "rendering": true,
 *     "heap": false,
 *     "anrHints": true,
 *     "perfetto": false,
 *     "startup": false
 *   },
 *   "thresholds": {
 *     "maxJankyPercent": 2.0,
 *     "maxFrozenFrames": 0,
 *     "maxStrictModeViolations": 0,
 *     "maxPotentialAnrHints": 20,
 *     "maxColdStartMs": 3000
 *   }
 * }
 * ```
 */
@Serializable
data class ScenarioConfig(
    val name: String,
    val durationSeconds: Int = 30,
    val engines: EngineConfig = EngineConfig(),
    val thresholds: ThresholdConfig = ThresholdConfig(),
)

@Serializable
data class EngineConfig(
    val strictMode: Boolean = true,
    val rendering: Boolean = true,
    val heap: Boolean = false,
    val anrHints: Boolean = true,
    val perfetto: Boolean = false,
    /**
     * When true, measures cold start (`am force-stop` + `am start -W`) once at
     * the beginning of the run (before the timed monitoring session).
     */
    val startup: Boolean = false,
)

/**
 * Thresholds that escalate report severity to CRITICAL regardless of the
 * engine's own classification logic.
 *
 * Design decision: thresholds live in the scenario, not hardcoded in the
 * engines. Different screens have different SLOs — a checkout flow tolerates
 * zero janky frames while a feed scroll might allow 2%.
 */
@Serializable
data class ThresholdConfig(
    val maxJankyPercent: Double = 5.0,
    val maxFrozenFrames: Int = 0,
    val maxStrictModeViolations: Int = 0,
    /** Counts heuristic ANR hints with severity WARNING or higher. */
    val maxPotentialAnrHints: Int = 50,
    /**
     * When non-null and [EngineConfig.startup] produced timings, fails the run
     * if cold-start `TotalTime` exceeds this value (milliseconds).
     */
    val maxColdStartMs: Int? = null,
)

fun loadScenario(file: File): ScenarioConfig =
    json.decodeFromString(file.readText())
