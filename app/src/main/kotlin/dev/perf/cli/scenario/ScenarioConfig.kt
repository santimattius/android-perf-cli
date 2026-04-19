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
 * Design decision: all engine flags default to true. Opt-out is more useful
 * than opt-in — a scenario file without explicit keys runs everything, which
 * is safe. This avoids CI jobs silently skipping engines because a flag was
 * forgotten.
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
 *     "perfetto": false
 *   },
 *   "thresholds": {
 *     "maxJankyPercent": 2.0,
 *     "maxFrozenFrames": 0,
 *     "maxStrictModeViolations": 0,
 *     "maxPotentialAnrHints": 20
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
)

fun loadScenario(file: File): ScenarioConfig =
    json.decodeFromString(file.readText())
