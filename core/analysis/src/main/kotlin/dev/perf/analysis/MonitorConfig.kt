package dev.perf.analysis

/**
 * Selects which monitoring engines run during a session.
 */
data class MonitorConfig(
    val strictMode: Boolean = true,
    val anrHints: Boolean = true,
    val rendering: Boolean = true,
)
