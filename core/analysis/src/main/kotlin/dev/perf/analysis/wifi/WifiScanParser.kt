package dev.perf.analysis.wifi

import dev.perf.analysis.batterystats.BatterystatsBlocks

/**
 * Parses `Wifi scan: N times` from the package's UID block in `dumpsys batterystats`.
 *
 * TODO: WifiScanParser duplicates the batterystats ADB call from WakelockCapture.
 * See design §5 for consolidation rationale (deferred — both callers share the
 * BatterystatsBlocks.extractUidBlock helper but each issues its own ADB round-trip).
 */
internal object WifiScanParser {

    private val WIFI_SCAN = Regex("""Wifi scan:\s+(\d+)\s+times""")

    /**
     * Parses the scan count from the [raw] batterystats dump for [packageName].
     *
     * Returns:
     * - The scan count (Int) when the package's UID block is found and contains `Wifi scan:`.
     * - `0` when the UID block is found but has no `Wifi scan:` line (no scans seen).
     * - `null` when the package's UID block cannot be located.
     */
    fun parse(raw: String, packageName: String): Int? {
        val block = BatterystatsBlocks.extractUidBlock(raw, packageName) ?: return null
        return WIFI_SCAN.find(block)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    }
}
