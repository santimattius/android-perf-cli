package dev.perf.analysis.score

data class PerfScore(
    val score: Int,
    val tier: PerfScoreTier,
    val breakdown: List<SignalScore>,
    val activeSignalCount: Int,
)
