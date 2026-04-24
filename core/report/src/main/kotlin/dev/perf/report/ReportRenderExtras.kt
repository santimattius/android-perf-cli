package dev.perf.report

/**
 * Optional content appended to reports without bloating [dev.perf.analysis.PerfReport].
 */
data class ReportRenderExtras(
    /** Raw or trimmed stdout from `android docs search` when available. */
    val androidCliDocsSnippet: String? = null,
)
