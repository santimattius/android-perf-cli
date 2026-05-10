package dev.perf.analysis.score

data class SignalScore(
    val signal: String,
    val subScore: Int,
    val weight: Int,
    val active: Boolean,
)
