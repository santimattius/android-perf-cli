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
 *     "startup": false,
 *     "wakelocks": false
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
    /**
     * When [startup] is true, which start kinds to measure (`cold`, `warm`, `hot`).
     * Empty means cold-only (backward compatible with older scenario JSON).
     */
    val startupTypes: List<String> = emptyList(),
    /** Samples per start type (1–10). Default 3. */
    val startupSamples: Int = 3,
    /** Partial wake locks via `dumpsys batterystats` (issues a destructive `--reset` at session start). */
    val wakelocks: Boolean = false,
    /** Confirmed ANR / Crash / LMK detection via logcat stream. Any confirmed exit is CRITICAL. */
    val exitSignals: Boolean = false,
    /**
     * Enable background network traffic measurement via `dumpsys netstats --uid N`.
     * Reports total session rx/tx bytes as INFO — no threshold gate in v1.
     * Reference: https://developer.android.com/topic/performance/vitals/bg-network-usage
     */
    val backgroundNetwork: Boolean = false,
    /**
     * Enable background Wi-Fi scan count measurement via `dumpsys batterystats`.
     * Must be used with [wakelocks] enabled (requires `--reset` to be effective).
     * Reports scan count delta as INFO — no threshold gate in v1.
     * Reference: https://developer.android.com/topic/performance/vitals/bg-wifi
     */
    val backgroundWifi: Boolean = false,
    /**
     * Enable stuck partial wake-lock detection by diffing start/end [WakelockReport]s.
     * Requires [wakelocks] = true (depends on the wakelock baseline after `--reset`).
     * Reports stuck locks as INFO — no threshold gate in v1.
     * Reference: https://developer.android.com/topic/performance/vitals/stuck-wakelock
     */
    val stuckWakelocks: Boolean = false,
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
     * When non-null and startup data exists, fails the run if cold-start **P50 (TTID)**
     * exceeds this value (milliseconds). Legacy reports without [dev.perf.analysis.PerfReport.startup]
     * fall back to [dev.perf.analysis.model.ColdStartMeasurement.totalTimeMs].
     */
    val maxColdStartMs: Int? = null,
    /** When set, compared against warm-start P50 from the startup engine. */
    val maxWarmStartMs: Int? = null,
    /** When set, compared against hot-start P50 from the startup engine. */
    val maxHotStartMs: Int? = null,
    /**
     * Absolute heuristic — does NOT represent the Play Console session-percentage definition.
     * Breach when summed partial wake-lock duration exceeds this (milliseconds).
     */
    val maxWakelockTimeMs: Long? = null,
    /**
     * Absolute heuristic — does NOT represent the Play Console session-percentage definition.
     * Breach when the number of distinct partial wake-lock rows exceeds this.
     */
    val maxWakelockCount: Int? = null,
    /**
     * When non-null, triggers a single-run approximation of the Play Console session-percentage
     * rule: if any wake lock entry exceeds [maxWakelockLongHeldMs] in this run, a WARNING breach
     * is emitted. Uses [VitalsDefaults.WAKELOCK_SESSION_PERCENT] as the reference threshold.
     */
    val maxWakelockSessionPercent: Double? = null,
    /**
     * The "long-held" boundary in milliseconds used with [maxWakelockSessionPercent].
     * A wake lock entry with totalDurationMs exceeding this is treated as a long-held lock.
     * Defaults to [VitalsDefaults.WAKELOCK_LONG_HELD_MS] (1 hour) when null.
     */
    val maxWakelockLongHeldMs: Long? = null,
)

/**
 * Official Android Vitals threshold constants. All values are `const val` (compile-time literals;
 * GraalVM AOT-safe; no reflection).
 *
 * These constants reflect the bad-behaviour thresholds published at
 * https://developer.android.com/topic/performance/vitals.
 *
 * Note: `maxJankyPercent` is intentionally excluded — the 5.0 default is a project-level
 * aggregate SLO, not a direct Play Console numeric gate.
 */
object VitalsDefaults {
    /** Cold TTID bad-behaviour threshold per Android Vitals. */
    const val COLD_START_MS = 5000L
    /** Warm TTID bad-behaviour threshold per Android Vitals. */
    const val WARM_START_MS = 2000L
    /** Hot TTID bad-behaviour threshold per Android Vitals. */
    const val HOT_START_MS = 1500L
    /** Percentage of daily sessions with a partial wake lock held for more than 1 hour. */
    const val WAKELOCK_SESSION_PERCENT = 5.0
    /** 1 hour in milliseconds — the Play Console long-held wake lock boundary. */
    const val WAKELOCK_LONG_HELD_MS = 3_600_000L

    /**
     * Applies all Vitals threshold values to [t], overwriting every field unconditionally.
     * This implements the "vitals profile wins" policy (D1): when `profile: "vitals"` is set,
     * the Play Console gates take precedence regardless of any user-supplied values.
     *
     * Note: [ThresholdConfig.maxJankyPercent] is NOT overwritten — excluded per spec.
     */
    fun applyTo(t: ThresholdConfig): ThresholdConfig = t.copy(
        maxFrozenFrames = 0,
        maxStrictModeViolations = 0,
        maxPotentialAnrHints = 50,
        maxColdStartMs = COLD_START_MS.toInt(),
        maxWarmStartMs = WARM_START_MS.toInt(),
        maxHotStartMs = HOT_START_MS.toInt(),
        maxWakelockSessionPercent = WAKELOCK_SESSION_PERCENT,
        maxWakelockLongHeldMs = WAKELOCK_LONG_HELD_MS,
    )
}

fun loadScenario(file: File): ScenarioConfig =
    json.decodeFromString(file.readText())
