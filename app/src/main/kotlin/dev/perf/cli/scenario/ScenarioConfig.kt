package dev.perf.cli.scenario

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private val json = Json {
    ignoreUnknownKeys = true
    prettyPrint = false
}

@Serializable
data class GradleBuildConfig(
    val enabled: Boolean = false,
    val variant: String = "debug",
    val module: String = ":app",
    val task: String? = null,
)

@Serializable
data class WorkflowStep(
    val type: String,
    val seconds: Double? = null,
    val uri: String? = null,
    /** `BACK`, `HOME`, or numeric keycode string. */
    val key: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val x2: Int? = null,
    val y2: Int? = null,
    val durationMs: Long? = null,
    val label: String? = null,
)

/**
 * Declarative configuration for a monitoring scenario.
 *
 * Use `"profile":"full"` for heavyweight engines (`heap`, `perfetto`, `startup`), presets, and tooling
 * presets (snapshots/screenshots)—see [`resolveRunParameters`] for exact merge behaviour.
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
    /** When `"full"` (same idea as `--full`), heavyweight engines flip on and defaults expand. */
    val profile: String? = null,
    val durationSeconds: Int = 30,
    val engines: EngineConfig = EngineConfig(),
    val thresholds: ThresholdConfig = ThresholdConfig(),
    /** `default`, `vitals`, or `custom` (requires `perfettoCategories`). */
    val perfettoPreset: String = "default",
    /** `session` records over the watchdog window; `startup` aligns with startup-only trace. */
    val perfettoCoverage: String = "session",
    /** Categories when preset is `custom` (`perfetto` light-mode names). */
    val perfettoCategories: List<String> = emptyList(),
    val collectProcSnapshot: Boolean = false,
    val screenshots: Boolean = false,
    val workflow: List<WorkflowStep> = emptyList(),
    val build: GradleBuildConfig = GradleBuildConfig(),
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
