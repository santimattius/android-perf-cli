package dev.perf.analysis.startup

import dev.perf.adb.AdbClient
import dev.perf.analysis.model.ColdStartMeasurement
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.startup.StartupReport
import dev.perf.analysis.model.startup.StartupSample
import dev.perf.analysis.model.startup.StartupSource
import dev.perf.analysis.model.startup.StartupStats
import dev.perf.analysis.model.startup.StartupType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Multi-sample startup capture (TTID / TTFD from logcat + `am start -W`).
 *
 * Replaces the historical [measureColdStart] single-shot helper; [parseAmStartWOutput] remains
 * for backward-compatible parsing tests and legacy [ColdStartMeasurement] construction.
 */
class StartupCapture(
    private val device: AdbClient,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun capture(
        packageName: String,
        activityComponent: String?,
        config: StartupCaptureConfig,
    ): StartupReport =
        coroutineScope {
            val ordered =
                listOf(StartupType.COLD, StartupType.WARM, StartupType.HOT)
                    .filter { it in config.startupTypes }
            val buckets = linkedMapOf<StartupType, MutableList<StartupSample>>()
            for (t in ordered) {
                repeat(config.effectiveSamplesPerType) { idx ->
                    buckets.getOrPut(t) { mutableListOf() }.add(
                        runOneSample(t, packageName, activityComponent, config),
                    )
                    if (idx < config.effectiveSamplesPerType - 1) {
                        delay(config.cooldownMs)
                    }
                }
            }
            val cold =
                buckets[StartupType.COLD]?.let {
                    StartupAggregator.aggregate(StartupType.COLD, it, config.thresholds)
                }
            val warm =
                buckets[StartupType.WARM]?.let {
                    StartupAggregator.aggregate(StartupType.WARM, it, config.thresholds)
                }
            val hot =
                buckets[StartupType.HOT]?.let {
                    StartupAggregator.aggregate(StartupType.HOT, it, config.thresholds)
                }
            val all = buckets.values.flatten()
            val parsedOk = StartupAggregator.computeParsedOk(all)
            val message = StartupAggregator.computeReportMessage(parsedOk, all)
            StartupReport(
                cold = cold,
                warm = warm,
                hot = hot,
                parsedOk = parsedOk,
                message = message,
            )
        }

    private suspend fun runOneSample(
        type: StartupType,
        packageName: String,
        activityComponent: String?,
        config: StartupCaptureConfig,
    ): StartupSample =
        coroutineScope {
            preCondition(type, packageName, activityComponent)

            val displayed = CompletableDeferred<TtidParse>()
            val fullyDrawn = CompletableDeferred<Int?>()

            val streamJob =
                launch(ioDispatcher) {
                    try {
                        device.stream(
                            "logcat",
                            "-T",
                            "1",
                            "-v",
                            "threadtime",
                            "ActivityTaskManager:I",
                            "ActivityManager:I",
                            "*:S",
                        ) { line ->
                            if (!displayed.isCompleted) {
                                parseDisplayedLine(line, packageName)?.let { p ->
                                    if (!displayed.isCompleted) {
                                        runCatching { displayed.complete(p) }
                                    }
                                }
                            } else if (!fullyDrawn.isCompleted) {
                                parseFullyDrawnLine(line, packageName)?.let { ms ->
                                    if (!fullyDrawn.isCompleted) {
                                        runCatching { fullyDrawn.complete(ms) }
                                    }
                                }
                            }
                            when {
                                !displayed.isCompleted -> true
                                else -> !fullyDrawn.isCompleted
                            }
                        }
                    } catch (_: Throwable) {
                        // stream ends when process dies — ignore
                    }
                }

            delay(config.streamWarmupMs)

            val startArgs = buildAmStartArgs(packageName, activityComponent)
            val amResult = device.shellAllowFailure(*startArgs.toTypedArray())
            val combined = (amResult.stdout + "\n" + amResult.stderr).trim()
            val am = parseAmStartMetrics(combined)

            val waitBound = (am.waitTimeMs ?: 0).toLong().coerceAtLeast(0L)
            val ttidWindow = maxOf(waitBound, config.perSampleTimeoutMs)
            val ttidParsed: TtidParse? =
                withTimeoutOrNull(ttidWindow) {
                    runCatching { displayed.await() }.getOrNull()
                }

            val ttfdMs: Int? =
                if (ttidParsed != null) {
                    withTimeoutOrNull(config.ttfdTailMs) {
                        runCatching { fullyDrawn.await() }.getOrNull()
                    }
                } else {
                    null
                }

            streamJob.cancelAndJoin()

            val (ttidMs, source, baseFlags) =
                when {
                    ttidParsed != null ->
                        Triple(ttidParsed.ms, ttidParsed.source, emptyList<String>())
                    am.totalTimeMs != null ->
                        Triple(
                            am.totalTimeMs,
                            StartupSource.AM_START_FALLBACK,
                            listOf("no_displayed_line"),
                        )
                    else ->
                        Triple(
                            null,
                            StartupSource.AM_START_FALLBACK,
                            listOf("no_displayed_line", "am_start_unparsed"),
                        )
                }

            val requestedMatches =
                am.launchState?.let { ls ->
                    val got =
                        when (ls.uppercase()) {
                            "COLD" -> StartupType.COLD
                            "WARM" -> StartupType.WARM
                            "HOT" -> StartupType.HOT
                            else -> null
                        }
                    got == type
                } ?: true

            val driftFlags =
                if (!requestedMatches && am.launchState != null) {
                    baseFlags + "pre_condition_drift"
                } else {
                    baseFlags
                }

            StartupSample(
                type = type,
                ttidMs = ttidMs,
                ttfdMs = ttfdMs,
                amStartTotalTimeMs = am.totalTimeMs,
                amStartWaitTimeMs = am.waitTimeMs,
                amStartLaunchState = am.launchState,
                source = source,
                flags = driftFlags,
            )
        }

    private suspend fun preCondition(
        type: StartupType,
        packageName: String,
        activityComponent: String?,
    ) {
        when (type) {
            StartupType.COLD -> {
                device.shellAllowFailure("am", "force-stop", packageName)
            }
            StartupType.WARM -> {
                device.shellAllowFailure("am", "force-stop", packageName)
                device.shellAllowFailure(*buildAmStartArgs(packageName, activityComponent).toTypedArray())
                device.shellAllowFailure("input", "keyevent", "3")
                delay(500)
            }
            StartupType.HOT -> {
                device.shellAllowFailure(*buildAmStartArgs(packageName, activityComponent).toTypedArray())
                device.shellAllowFailure("input", "keyevent", "3")
                delay(500)
            }
        }
    }

    private fun buildAmStartArgs(packageName: String, activityComponent: String?): List<String> =
        buildList {
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
        }

    companion object {
        fun parseAmStartWOutput(output: String): ColdStartMeasurement = parseAmStartWOutputLegacy(output)
    }
}

private fun parseAmStartWOutputLegacy(output: String): ColdStartMeasurement {
    val am = parseAmStartMetrics(output)
    if (am.totalTimeMs == null) {
        return ColdStartMeasurement(
            totalTimeMs = null,
            waitTimeMs = am.waitTimeMs,
            launchState = am.launchState,
            status = am.status,
            parsedOk = false,
            message = "Could not parse TotalTime/ThisTime from am start -W output.",
            severity = Severity.WARNING,
        )
    }
    val total = am.totalTimeMs
    val sev =
        when {
            total >= 5000 -> Severity.CRITICAL
            total >= 2000 -> Severity.WARNING
            else -> Severity.INFO
        }
    return ColdStartMeasurement(
        totalTimeMs = total,
        waitTimeMs = am.waitTimeMs,
        launchState = am.launchState,
        status = am.status,
        parsedOk = true,
        message =
            "Cold start TotalTime=${total}ms" +
                (am.waitTimeMs?.let { ", WaitTime=${it}ms" } ?: "") +
                (am.launchState?.let { ", LaunchState=$it" } ?: "") +
                (am.status?.let { ", Status=$it" } ?: ""),
        severity = sev,
    )
}
