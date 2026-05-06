package dev.perf.analysis.startup

import dev.perf.analysis.model.startup.StartupSource

private val DURATION_TOKEN = Regex("""(\d+(?:\.\d+)?)(ms|s|m|h)""")

data class TtidParse(
    val ms: Int,
    val source: StartupSource = StartupSource.LOGCAT_DISPLAYED,
)

/**
 * Parses TTID from `ActivityManager: Displayed …` logcat lines (Vitals TTID semantics).
 */
fun parseDisplayedLine(line: String, packageName: String): TtidParse? {
    if (!line.contains("Displayed", ignoreCase = true)) return null
    if (!line.contains(packageName, ignoreCase = true)) return null
    val colon = line.lastIndexOf(':')
    if (colon < 0 || colon >= line.length - 1) return null
    val tail = line.substring(colon + 1).trim()
    val timePart = tail.removePrefix("+").trim()
    val ms = parseCompositeDurationToMs(timePart) ?: return null
    return TtidParse(ms = ms.toInt().coerceAtLeast(0), source = StartupSource.LOGCAT_DISPLAYED)
}

/**
 * Parses TTFD from `ActivityManager: Fully drawn …` when present.
 */
fun parseFullyDrawnLine(line: String, packageName: String): Int? {
    if (!line.contains("Fully drawn", ignoreCase = true)) return null
    if (!line.contains(packageName, ignoreCase = true)) return null
    val colon = line.lastIndexOf(':')
    if (colon < 0 || colon >= line.length - 1) return null
    val tail = line.substring(colon + 1).trim().removePrefix("+").trim()
    val ms = parseCompositeDurationToMs(tail) ?: return null
    return ms.toInt().coerceAtLeast(0)
}

internal fun parseCompositeDurationToMs(raw: String): Long? {
    if (raw.isBlank()) return null
    var sum = 0.0
    var found = false
    for (m in DURATION_TOKEN.findAll(raw)) {
        found = true
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        val mult =
            when (m.groupValues[2]) {
                "h" -> 3_600_000.0
                "m" -> 60_000.0
                "s" -> 1_000.0
                "ms" -> 1.0
                else -> return null
            }
        sum += n * mult
    }
    if (!found) return null
    return sum.toLong()
}
