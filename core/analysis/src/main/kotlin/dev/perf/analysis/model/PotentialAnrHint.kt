package dev.perf.analysis.model

import kotlinx.serialization.Serializable

/**
 * Heuristic signals in logcat that suggest **main-thread pressure** or ANR risk.
 * These are not confirmed ANRs (which are hard to capture reliably); they flag
 * patterns like Choreographer frame skips that often precede jank and timeouts.
 */
@Serializable
data class PotentialAnrHint(
    override val severity: Severity,
    override val message: String,
    val hintType: PotentialAnrHintType,
    val detail: String,
) : PerfIncident

@Serializable
enum class PotentialAnrHintType {
    CHOREOGRAPHER_SKIPPED_FRAMES,
    LOOPER_SLOW_DISPATCH,
    INPUT_DISPATCH_TIMEOUT_RISK,
    HIGH_FRAME_TIME,
}
