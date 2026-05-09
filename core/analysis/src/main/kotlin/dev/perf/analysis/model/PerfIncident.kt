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
    /** Frames with render time in (50ms, 700ms] — the "slow session" bucket from Android Vitals. Default 0 for backward-compatible deserialization. */
    val slowFrames: Int = 0,
) : PerfIncident

@Serializable
data class MemoryLeak(
    override val severity: Severity = Severity.CRITICAL,
    override val message: String,
    val leakingObject: String,
    val retainedBytes: Long,
    val shortestPath: String,
) : PerfIncident
