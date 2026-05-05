package dev.perf.report

/**
 * Optional content appended to reports without bloating [dev.perf.analysis.PerfReport].
 */
data class ReportSessionMeta(
    /** Instant the Markdown was generated (ISO-8601, host clock). */
    val generatedAtIso: String,
    /** Watchdog.collect window started (typically UTC). */
    val monitorWindowStartIso: String,
    val monitorWindowEndIso: String,
    val scenarioLabel: String?,
    val fullMode: Boolean,
)

data class ReportRenderExtras(
    /** Raw or trimmed stdout from `android docs search` when available. */
    val androidCliDocsSnippet: String? = null,
    val sessionMeta: ReportSessionMeta? = null,
)
