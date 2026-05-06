package dev.perf.analysis.startup

/**
 * Raw fields parsed from `adb shell am start -W` stdout/stderr (no severity heuristics).
 */
data class AmStartMetrics(
    val totalTimeMs: Int?,
    val waitTimeMs: Int?,
    val launchState: String?,
    val status: String?,
)

private val totalTimeRegex = Regex("""TotalTime:\s*(\d+)""", RegexOption.IGNORE_CASE)
private val thisTimeRegex = Regex("""ThisTime:\s*(\d+)""", RegexOption.IGNORE_CASE)
private val waitTimeRegex = Regex("""WaitTime:\s*(\d+)""", RegexOption.IGNORE_CASE)
private val launchStateRegex = Regex("""LaunchState:\s*(\S+)""", RegexOption.IGNORE_CASE)
private val statusRegex = Regex("""Status:\s*(\S+)""", RegexOption.IGNORE_CASE)

fun parseAmStartMetrics(output: String): AmStartMetrics {
    val combined = output.trim()
    val total =
        totalTimeRegex.find(combined)?.groupValues?.get(1)?.toIntOrNull()
            ?: thisTimeRegex.find(combined)?.groupValues?.get(1)?.toIntOrNull()
    val wait = waitTimeRegex.find(combined)?.groupValues?.get(1)?.toIntOrNull()
    val launchState = launchStateRegex.find(combined)?.groupValues?.get(1)
    val status = statusRegex.find(combined)?.groupValues?.get(1)
    return AmStartMetrics(
        totalTimeMs = total,
        waitTimeMs = wait,
        launchState = launchState,
        status = status,
    )
}
