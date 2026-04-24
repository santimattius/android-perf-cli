package dev.perf.analysis.perfetto

/**
 * Outcome of attempting to record a trace file on the device.
 */
sealed class PerfettoRecordResult {
    data class Ok(val remotePath: String) : PerfettoRecordResult()
    data class Failed(val reason: String) : PerfettoRecordResult()
}
