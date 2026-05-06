package dev.perf.analysis.wakelock

import dev.perf.analysis.model.WakelockEntry
import dev.perf.analysis.model.WakelockReport

/** `Wake lock <name>: N times, <duration> realtime` */
private val WAKELOCK_LINE =
    Regex("""Wake lock\s+(.+?):\s+(\d+)\s+times,\s+([^\n]+?)\s+realtime""")

private val DURATION_TOKEN = Regex("""(\d+(?:\.\d+)?)(ms|s|m|h)""")

internal object WakelockParser {

    /**
     * Parses `dumpsys batterystats` stdout for [packageName]'s UID block.
     * Returns null when the dump does not contain a matching UID section.
     */
    fun parse(raw: String, packageName: String): WakelockReport? {
        val block = extractUidBlock(raw, packageName) ?: return null
        val entries =
            WAKELOCK_LINE.findAll(block).mapNotNull { m ->
                val name = m.groupValues[1].trim()
                val count = m.groupValues[2].toIntOrNull() ?: return@mapNotNull null
                val durRaw = m.groupValues[3].trim()
                val ms = parseDurationMs(durRaw)
                WakelockEntry(name = name, count = count, totalDurationMs = ms)
            }.toList()
        val total = entries.sumOf { it.totalDurationMs }
        return WakelockReport(entries = entries, totalDurationMs = total)
    }

    fun parseDurationMs(raw: String): Long =
        DURATION_TOKEN.findAll(raw).sumOf { m ->
            val n = m.groupValues[1].toDouble()
            val mult =
                when (m.groupValues[2]) {
                    "h" -> 3_600_000.0
                    "m" -> 60_000.0
                    "s" -> 1_000.0
                    "ms" -> 1.0
                    else -> 0.0
                }
            (n * mult).toLong()
        }

    private fun extractUidBlock(raw: String, packageName: String): String? {
        val lines = raw.lines()
        val starts = lines.mapIndexedNotNull { idx, line ->
            if (line.trimStart().startsWith("Uid ")) idx else null
        }
        if (starts.isEmpty()) {
            return raw.takeIf { it.contains(packageName, ignoreCase = true) }
        }
        for (i in starts.indices) {
            val from = starts[i]
            val toExclusive = starts.getOrNull(i + 1) ?: lines.size
            val slice = lines.subList(from, toExclusive).joinToString("\n")
            if (slice.contains(packageName, ignoreCase = true)) {
                return slice
            }
        }
        return null
    }
}
