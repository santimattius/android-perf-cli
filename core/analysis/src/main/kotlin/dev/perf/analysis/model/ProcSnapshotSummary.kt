package dev.perf.analysis.model

// Coarse meminfo + thread count from dumpsys / proc.
data class ProcSnapshotSummary(
    val meminfoSnippet: String,
    val threadCount: Int?,
)
