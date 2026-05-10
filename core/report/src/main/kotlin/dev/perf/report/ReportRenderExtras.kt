package dev.perf.report

import dev.perf.analysis.model.SessionRateSummary

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
    /**
     * Rolling-window session rates computed from prior runs.
     * Null when fewer than 2 prior runs exist (not enough data for a meaningful signal).
     * Existing call sites that omit this field continue to compile unchanged.
     */
    val sessionRates: SessionRateSummary? = null,
)
