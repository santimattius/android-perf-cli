package dev.perf.analysis.exit

import dev.perf.adb.AdbClient
import dev.perf.analysis.model.ExitEvent
import dev.perf.analysis.model.ExitSignalReport
import dev.perf.analysis.model.ExitType
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Captures confirmed process exits (ANR, Crash, LMK) via three concurrent logcat streams.
 *
 * Stream launch order is FIXED and MUST NOT change without updating [ExitSignalCaptureTest]:
 *   1. events-buffer  → `logcat -b events -v threadtime -s am_anr`
 *   2. crash-buffer   → `logcat -b crash -v threadtime`
 *   3. lmkd           → `logcat -v threadtime -s lmkd`
 *
 * NO `--pid` filter is applied to any stream — see design §2 for the full rationale.
 * Post-session enrichment via `cmd activity exit-info` is best-effort: any shell failure
 * is silently ignored.
 */
class ExitSignalCapture(private val adbClient: AdbClient) {

    suspend fun capture(packageName: String, durationMs: Long): ExitSignalReport {
        val events = mutableListOf<ExitEvent>()
        val lock = Any()

        coroutineScope {
            launch {
                adbClient.stream("logcat", "-b", "events", "-v", "threadtime", "-s", "am_anr") { line ->
                    ExitSignalParser.parseAmAnrLine(line, packageName)?.let { event ->
                        synchronized(lock) { events.add(event) }
                    }
                    true
                }
            }

            launch {
                val stateMachine = CrashStateMachine()
                adbClient.stream("logcat", "-b", "crash", "-v", "threadtime") { line ->
                    stateMachine.feed(line, packageName)?.let { event ->
                        synchronized(lock) { events.add(event) }
                    }
                    true
                }
            }

            launch {
                adbClient.stream("logcat", "-v", "threadtime", "-s", "lmkd") { line ->
                    ExitSignalParser.parseLmkLine(line, packageName)?.let { event ->
                        synchronized(lock) { events.add(event) }
                    }
                    true
                }
            }
        }

        enrichFromExitInfo(packageName, events, lock)

        return ExitSignalReport(events = events.toList())
    }

    private fun enrichFromExitInfo(
        packageName: String,
        events: MutableList<ExitEvent>,
        lock: Any,
    ) {
        val result = adbClient.shellAllowFailure("cmd", "activity", "exit-info", packageName)
        if (result.exitCode != 0 || result.stdout.isBlank()) return

        val existingTypes = synchronized(lock) { events.map { it.exitType }.toSet() }

        result.stdout.lines().forEach { line ->
            val exitType = when {
                line.contains("REASON_ANR") -> ExitType.ANR
                line.contains("REASON_CRASH") -> ExitType.CRASH
                line.contains("REASON_LOW_MEMORY") -> ExitType.LMK
                else -> null
            } ?: return@forEach

            if (exitType !in existingTypes) {
                val event = ExitEvent(
                    exitType = exitType,
                    packageName = packageName,
                    message = "exit-info: ${exitType.name} for $packageName",
                    rawLine = line,
                )
                synchronized(lock) { events.add(event) }
            }
        }
    }
}

/**
 * Two-line crash state machine.
 *
 * Arms when a line containing `FATAL EXCEPTION` is seen. The next line is
 * checked for a matching `Process: <pkg>, PID:` pattern. State resets on
 * every [feed] call so partial states from cancelled sessions are not carried
 * across new lines.
 */
internal class CrashStateMachine {
    private var lastLine: String = ""

    fun feed(line: String, packageName: String): ExitEvent? {
        val event = ExitSignalParser.parseCrashLine(line, lastLine, packageName)
        lastLine = line
        return event
    }
}
