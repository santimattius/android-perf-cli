package dev.perf.analysis.model

import kotlinx.serialization.Serializable

@Serializable
data class ExitEvent(
    override val severity: Severity = Severity.CRITICAL,
    override val message: String,
    val exitType: ExitType,
    val packageName: String,
    val rawLine: String,
    val timestampMs: Long? = null,
) : PerfIncident
