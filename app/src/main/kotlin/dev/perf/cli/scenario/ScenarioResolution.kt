package dev.perf.cli.scenario

enum class PerfettoCoverage {
    /** Single trace overlapping the monitored window (post cold start / monkey). */
    SESSION,

    /** Trace recorded primarily across startup when [EngineConfig.startup] is enabled. */
    STARTUP,
}

/**
 * Resolved parameters for [`RunCommand`] after applying `profile`/CLI `--full`.
 */
data class ResolvedRunConfig(
    val durationSeconds: Int,
    val engines: EngineConfig,
    val perfettoPreset: String,
    val perfettoCoverage: PerfettoCoverage,
    val perfettoCategories: List<String>?,
    val collectProcSnapshot: Boolean,
    val screenshotsEnabled: Boolean,
    val workflow: List<WorkflowStep>,
    val build: GradleBuildConfig?,
)

private const val FULL_DEFAULT_CLI_SECONDS = 120

/**
 * Applies [`ScenarioConfig`] and CLI `--full`.
 *
 * **`--full` without `--scenario`:** uses [FULL_DEFAULT_CLI_SECONDS], turns on heavy engines (`heap`,
 * `perfetto`, `startup`), vitals Perfetto preset, proc/thread snapshot, screenshots.
 *
 * **`"profile":"full"`** in JSON: same heavyweight merge; **`durationSeconds`** is taken from the file.
 */
fun resolveRunParameters(
    scenarioConfig: ScenarioConfig?,
    fullCli: Boolean,
    cliDurationSeconds: Int,
): ResolvedRunConfig {
    val scenario = scenarioConfig ?: ScenarioConfig(name = "cli")
    val profileFull = scenario.profile.equals("full", ignoreCase = true)
    val full = fullCli || profileFull

    val durationSeconds =
        when {
            scenarioConfig != null -> scenario.durationSeconds
            fullCli -> FULL_DEFAULT_CLI_SECONDS
            else -> cliDurationSeconds
        }

    val presetRaw = scenario.perfettoPreset.lowercase()
    val presetOut =
        when {
            full && presetRaw == "default" -> "vitals"
            presetRaw == "vitals" || presetRaw == "custom" -> presetRaw
            else -> "default"
        }

    val coverage = when (scenario.perfettoCoverage.lowercase()) {
        "startup" -> PerfettoCoverage.STARTUP
        else -> PerfettoCoverage.SESSION
    }

    val cats =
        scenario.perfettoCategories.takeIf { presetOut == "custom" && it.isNotEmpty() }

    val mergedEngines =
        if (full) {
            scenario.engines.copy(
                heap = true,
                perfetto = true,
                startup = true,
                wakelocks = true,
            )
        } else {
            scenario.engines
        }

    val procSnap = full || scenario.collectProcSnapshot
    val shots = full || scenario.screenshots
    val buildCfg = scenario.build.takeIf { it.enabled }

    return ResolvedRunConfig(
        durationSeconds = durationSeconds,
        engines = mergedEngines,
        perfettoPreset = presetOut,
        perfettoCoverage = coverage,
        perfettoCategories = cats,
        collectProcSnapshot = procSnap,
        screenshotsEnabled = shots,
        workflow = scenario.workflow.ifEmpty { emptyList() },
        build = buildCfg,
    )
}
