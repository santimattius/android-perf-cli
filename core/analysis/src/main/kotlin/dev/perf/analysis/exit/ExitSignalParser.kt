package dev.perf.analysis.exit

import dev.perf.analysis.model.ExitEvent
import dev.perf.analysis.model.ExitType

private val AM_ANR_REGEX = Regex("""am_anr\s*:\s*\[(\d+),\s*\d+,\s*([\w.]+),""")
private val FATAL_EXCEPTION_REGEX = Regex("""FATAL EXCEPTION""")
private val CRASH_PROCESS_REGEX = Regex("""Process:\s+([\w.]+),\s*PID:\s*(\d+)""")
private val LMK_KILL_REGEX = Regex("""lmkd.*(?:Kill|Reclaim)\s+'([\w.]+)'""")

/**
 * Pure parsing functions for the three exit-signal sources.
 *
 * All Regex constants are defined at file scope (not inside function bodies)
 * so GraalVM native image does not require reflection-based substitution.
 *
 * Package matching uses EXACT full-string equality after regex group extraction —
 * never contains() or startsWith() — to prevent false-positives from package prefixes.
 */
internal object ExitSignalParser {

    /**
     * Parses a single logcat line from the events buffer.
     *
     * Returns an ANR [ExitEvent] only when the `am_anr` payload's package token
     * (position 2 inside the brackets) exactly equals [packageName].
     */
    fun parseAmAnrLine(line: String, packageName: String): ExitEvent? {
        val match = AM_ANR_REGEX.find(line) ?: return null
        val extractedPkg = match.groupValues[2]
        if (extractedPkg != packageName) return null
        return ExitEvent(
            exitType = ExitType.ANR,
            packageName = packageName,
            message = "ANR: ${line.substringAfterLast(",").trimEnd(']').trim()}",
            rawLine = line,
        )
    }

    /**
     * Implements the stateful two-line crash detection protocol.
     *
     * Returns a CRASH [ExitEvent] only when [previousLine] contains
     * `FATAL EXCEPTION` AND the `Process:` package in [line] exactly equals
     * [packageName]. Any mismatch returns null.
     */
    fun parseCrashLine(line: String, previousLine: String, packageName: String): ExitEvent? {
        if (!FATAL_EXCEPTION_REGEX.containsMatchIn(previousLine)) return null
        val match = CRASH_PROCESS_REGEX.find(line) ?: return null
        val extractedPkg = match.groupValues[1]
        if (extractedPkg != packageName) return null
        return ExitEvent(
            exitType = ExitType.CRASH,
            packageName = packageName,
            message = "FATAL EXCEPTION in $packageName",
            rawLine = line,
        )
    }

    /**
     * Parses a single logcat line from the lmkd tag stream.
     *
     * Matches both `Kill 'pkg'` and `Reclaim 'pkg'` variants. The single-quote
     * delimiters are server-emitted and act as exact token boundaries. Group(1)
     * is additionally compared with [packageName] via equality for defense in depth.
     */
    fun parseLmkLine(line: String, packageName: String): ExitEvent? {
        val match = LMK_KILL_REGEX.find(line) ?: return null
        val extractedPkg = match.groupValues[1]
        if (extractedPkg != packageName) return null
        return ExitEvent(
            exitType = ExitType.LMK,
            packageName = packageName,
            message = "LMK kill: $packageName",
            rawLine = line,
        )
    }
}
