package dev.perf.analysis.batterystats

/**
 * Shared helper for extracting a package-specific UID block from
 * `dumpsys batterystats` output.
 *
 * Both [dev.perf.analysis.wakelock.WakelockParser] and
 * [dev.perf.analysis.wifi.WifiScanParser] use this utility to avoid
 * duplicating the block-extraction logic.
 */
internal object BatterystatsBlocks {

    /**
     * Extracts the UID block from [raw] batterystats output that corresponds
     * to [packageName].
     *
     * Returns the block as a substring, or `null` if the package is not found.
     */
    fun extractUidBlock(raw: String, packageName: String): String? {
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
