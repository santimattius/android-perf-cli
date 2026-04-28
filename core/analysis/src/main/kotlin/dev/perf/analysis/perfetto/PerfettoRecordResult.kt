package dev.perf.analysis.perfetto

/**
 * Outcome of attempting to record a trace file on the device.
 */
sealed class PerfettoRecordResult {
    /** [degradedFromVitalsPreset] documents optional fallback from `vitals` → `default` categories. */
    data class Ok(
        val remotePath: String,
        val degradedFromVitalsPreset: Boolean = false,
    ) : PerfettoRecordResult()

    data class Failed(val reason: String) : PerfettoRecordResult()
}
