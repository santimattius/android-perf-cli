package dev.perf.analysis.model

import kotlinx.serialization.Serializable

@Serializable
data class ExitSignalReport(
    val events: List<ExitEvent>,
    val severity: Severity = if (events.isEmpty()) Severity.INFO else Severity.CRITICAL,
) {
    val hasExits: Boolean get() = events.isNotEmpty()
}
