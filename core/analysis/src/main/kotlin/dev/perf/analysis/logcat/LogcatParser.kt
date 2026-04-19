package dev.perf.analysis.logcat

import dev.perf.adb.AdbClient
import dev.perf.analysis.model.PotentialAnrHint
import dev.perf.analysis.model.PotentialAnrHintType
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StrictModeViolation
import dev.perf.analysis.model.StrictModeViolation.ViolationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Streams logcat for the app PID and emits [SessionLogcatEvent]s.
 *
 * **StrictMode:** lines tagged `StrictMode` with stack traces (existing behavior).
 *
 * **Potential ANR hints:** heuristics on the same stream — skipped frames, slow
 * looper dispatch, input timeout messages — not a substitute for system ANR traces.
 */
class LogcatParser(
    private val device: AdbClient,
    private val collectStrictMode: Boolean = true,
    private val collectAnrHints: Boolean = true,
) {

    fun sessionEvents(packageName: String): Flow<SessionLogcatEvent> = callbackFlow {
        device.shell("logcat", "-c")

        val headerRegex = Regex(
            """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d+)\s+\d+\s+\d+\s+\w\s+StrictMode\s*:\s+(.+)$""",
        )
        val violationTypeRegex = Regex(
            """StrictMode(DiskRead|DiskWrite|Network|Custom)Violation""",
            RegexOption.IGNORE_CASE,
        )

        var currentViolation: PartialViolation? = null
        val stackBuffer = StringBuilder()

        device.stream("logcat", "-v", "threadtime", "--pid", pidOf(packageName)) { line ->
            val trimmed = line.trimEnd()

            if (collectStrictMode) {
                val headerMatch = headerRegex.matchEntire(trimmed)
                if (headerMatch != null) {
                    currentViolation?.let { trySend(SessionLogcatEvent.StrictMode(it.toViolation(stackBuffer.toString()))) }
                    stackBuffer.clear()

                    val body = headerMatch.groupValues[2]
                    val typeMatch = violationTypeRegex.find(body)
                    currentViolation = PartialViolation(
                        violationType = typeMatch?.groupValues?.get(1).toViolationType(),
                        threadName = extractThread(line),
                        message = body.substringBefore(" penalty").trim(),
                    )
                } else if (line.startsWith("\tat ") || line.startsWith("\t...")) {
                    stackBuffer.appendLine(trimmed)
                } else if (trimmed.isBlank() && currentViolation != null) {
                    currentViolation?.let { trySend(SessionLogcatEvent.StrictMode(it.toViolation(stackBuffer.toString()))) }
                    currentViolation = null
                    stackBuffer.clear()
                }
            }

            if (collectAnrHints) {
                parsePotentialAnrLine(line.trim())?.let { trySend(SessionLogcatEvent.AnrHint(it)) }
            }

            !isClosedForSend
        }

        if (collectStrictMode) {
            currentViolation?.let { trySend(SessionLogcatEvent.StrictMode(it.toViolation(stackBuffer.toString()))) }
        }

        close()
        awaitClose()
    }.flowOn(Dispatchers.IO)

    fun violations(packageName: String): Flow<StrictModeViolation> =
        sessionEvents(packageName)
            .filterIsInstance<SessionLogcatEvent.StrictMode>()
            .map { it.violation }

    private fun pidOf(packageName: String): String =
        device.shell("pidof", packageName).trim().ifEmpty {
            error("Process not found for package: $packageName")
        }

    private fun extractThread(line: String): String =
        line.split(Regex("\\s+")).getOrElse(4) { "unknown" }

    private data class PartialViolation(
        val violationType: ViolationType,
        val threadName: String,
        val message: String,
    ) {
        fun toViolation(stack: String) = StrictModeViolation(
            violationType = violationType,
            threadName = threadName,
            message = message,
            stackTrace = stack.trim(),
        )
    }

    private fun String?.toViolationType(): ViolationType = when (this?.lowercase()) {
        "diskread" -> ViolationType.DISK_READ
        "diskwrite" -> ViolationType.DISK_WRITE
        "network" -> ViolationType.NETWORK
        else -> ViolationType.UNKNOWN
    }

}

private val skippedFramesRegex = Regex(
    """Skipped\s+(\d+)\s+frames""",
    RegexOption.IGNORE_CASE,
)
private val slowDispatchRegex = Regex(
    """Slow dispatch took (\d+)\s*ms""",
    RegexOption.IGNORE_CASE,
)
private val inputTimeoutRegex = Regex(
    """Input dispatching timed out""",
    RegexOption.IGNORE_CASE,
)
private val longFrameRegex = Regex(
    """doFrame took (\d+)\s*ms""",
    RegexOption.IGNORE_CASE,
)

internal fun parsePotentialAnrLine(line: String): PotentialAnrHint? {
    if (line.isBlank()) return null

    if (inputTimeoutRegex.containsMatchIn(line)) {
        return PotentialAnrHint(
            severity = Severity.CRITICAL,
            message = "Input dispatch timeout signal (main thread may be blocked)",
            hintType = PotentialAnrHintType.INPUT_DISPATCH_TIMEOUT_RISK,
            detail = line,
        )
    }

    slowDispatchRegex.find(line)?.let { m ->
        val ms = m.groupValues[1].toIntOrNull() ?: return@let
        return PotentialAnrHint(
            severity = if (ms >= 500) Severity.CRITICAL else Severity.WARNING,
            message = "Slow Looper dispatch (${ms}ms)",
            hintType = PotentialAnrHintType.LOOPER_SLOW_DISPATCH,
            detail = line,
        )
    }

    longFrameRegex.find(line)?.let { m ->
        val ms = m.groupValues[1].toIntOrNull() ?: return@let
        return PotentialAnrHint(
            severity = if (ms >= 32) Severity.WARNING else Severity.INFO,
            message = "Long frame callback (${ms}ms)",
            hintType = PotentialAnrHintType.HIGH_FRAME_TIME,
            detail = line,
        )
    }

    skippedFramesRegex.find(line)?.let { m ->
        val n = m.groupValues[1].toIntOrNull() ?: return@let
        if (n < 5) return null
        val sev = when {
            n >= 60 -> Severity.CRITICAL
            n >= 20 -> Severity.WARNING
            else -> Severity.INFO
        }
        return PotentialAnrHint(
            severity = sev,
            message = "Choreographer: skipped $n frames (main thread overload)",
            hintType = PotentialAnrHintType.CHOREOGRAPHER_SKIPPED_FRAMES,
            detail = line,
        )
    }

    return null
}
