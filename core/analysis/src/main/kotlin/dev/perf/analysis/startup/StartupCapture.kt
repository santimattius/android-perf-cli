package dev.perf.analysis.startup

import dev.perf.adb.AdbClient
import dev.perf.analysis.model.ColdStartMeasurement
import dev.perf.analysis.model.Severity

/**
 * Measures **cold start** using `adb shell am force-stop` followed by
 * `adb shell am start -W`, matching the common Vitals / local debugging workflow.
 *
 * Design decision: when [activityComponent] is null, we use MAIN/LAUNCHER
 * against [packageName] (same intent family as `monkey -p … -c LAUNCHER`).
 */
object StartupCapture {

    private val totalTimeRegex = Regex("""TotalTime:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val thisTimeRegex = Regex("""ThisTime:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val waitTimeRegex = Regex("""WaitTime:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val launchStateRegex = Regex("""LaunchState:\s*(\S+)""", RegexOption.IGNORE_CASE)
    private val statusRegex = Regex("""Status:\s*(\S+)""", RegexOption.IGNORE_CASE)

    fun measureColdStart(
        device: AdbClient,
        packageName: String,
        activityComponent: String?,
    ): ColdStartMeasurement {
        val stop = device.shellAllowFailure("am", "force-stop", packageName)
        if (!stop.ok) {
            return failed(
                "am force-stop failed (exit ${stop.exitCode}): ${stop.stderr.ifBlank { stop.stdout }}".trim(),
            )
        }

        val startArgs = buildList {
            add("am")
            add("start")
            add("-W")
            if (activityComponent != null) {
                add("-n")
                add(activityComponent)
            } else {
                add("-a")
                add("android.intent.action.MAIN")
                add("-c")
                add("android.intent.category.LAUNCHER")
                add("-p")
                add(packageName)
            }
        }.toTypedArray()

        val start = device.shellAllowFailure(*startArgs)
        val combined = (start.stdout + "\n" + start.stderr).trim()
        if (!start.ok) {
            return failed(
                "am start -W failed (exit ${start.exitCode}): ${combined.take(500)}",
            )
        }
        return parseAmStartWOutput(combined)
    }

    /**
     * Parses `am start -W` stdout. Exposed for unit tests.
     */
    fun parseAmStartWOutput(output: String): ColdStartMeasurement {
        val total = totalTimeRegex.find(output)?.groupValues?.get(1)?.toIntOrNull()
            ?: thisTimeRegex.find(output)?.groupValues?.get(1)?.toIntOrNull()
        val wait = waitTimeRegex.find(output)?.groupValues?.get(1)?.toIntOrNull()
        val launchState = launchStateRegex.find(output)?.groupValues?.get(1)
        val status = statusRegex.find(output)?.groupValues?.get(1)

        if (total == null) {
            return ColdStartMeasurement(
                totalTimeMs = null,
                waitTimeMs = wait,
                launchState = launchState,
                status = status,
                parsedOk = false,
                message = "Could not parse TotalTime/ThisTime from am start -W output.",
                severity = Severity.WARNING,
            )
        }

        val sev = when {
            total >= 5000 -> Severity.CRITICAL
            total >= 2000 -> Severity.WARNING
            else -> Severity.INFO
        }
        return ColdStartMeasurement(
            totalTimeMs = total,
            waitTimeMs = wait,
            launchState = launchState,
            status = status,
            parsedOk = true,
            message = "Cold start TotalTime=${total}ms" +
                (wait?.let { ", WaitTime=${it}ms" } ?: "") +
                (launchState?.let { ", LaunchState=$it" } ?: "") +
                (status?.let { ", Status=$it" } ?: ""),
            severity = sev,
        )
    }

    private fun failed(msg: String) = ColdStartMeasurement(
        totalTimeMs = null,
        waitTimeMs = null,
        launchState = null,
        status = null,
        parsedOk = false,
        message = msg,
        severity = Severity.WARNING,
    )
}
