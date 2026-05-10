package dev.perf.analysis.model

/**
 * Rolling-window rate summary for ANR, crash, and slow-session signals.
 *
 * Produced by [dev.perf.cli.history.RateCalculator] and consumed by
 * [dev.perf.report.ReportRenderExtras] so that MarkdownReporter can render
 * the "Session Rates" section.
 *
 * Design: lives in core/analysis so that both core/report and app can depend
 * on it without creating a circular dependency. No serialization annotation
 * because this type is never persisted — only SessionHistoryEntry (app/) is.
 * GraalVM AOT-safe: no reflection.
 */
data class SessionRateSummary(
    val totalRuns: Int,
    val anrRate: Double,
    val crashRate: Double,
    val slowSessionRate: Double,
    val windowSize: Int,
    val hasExitSignalsCaveat: Boolean,
)
