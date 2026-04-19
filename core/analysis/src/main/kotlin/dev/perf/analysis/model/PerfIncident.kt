package dev.perf.analysis.model

import kotlinx.serialization.Serializable

enum class Severity { CRITICAL, WARNING, INFO }

@Serializable
sealed interface PerfIncident {
    val severity: Severity
    val message: String
}

@Serializable
data class StrictModeViolation(
    override val severity: Severity = Severity.WARNING,
    override val message: String,
    val violationType: ViolationType,
    val stackTrace: String,
    val threadName: String,
) : PerfIncident {
    enum class ViolationType { DISK_READ, DISK_WRITE, NETWORK, UNKNOWN }
}

@Serializable
data class RenderingIncident(
    override val severity: Severity,
    override val message: String,
    val jankyFrames: Int,
    val frozenFrames: Int,
    val totalFrames: Int,
    val p90Ms: Double,
    val p95Ms: Double,
    val p99Ms: Double,
) : PerfIncident

@Serializable
data class MemoryLeak(
    override val severity: Severity = Severity.CRITICAL,
    override val message: String,
    val leakingObject: String,
    val retainedBytes: Long,
    val shortestPath: String,
) : PerfIncident
