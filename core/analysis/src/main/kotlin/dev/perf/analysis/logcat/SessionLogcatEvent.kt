package dev.perf.analysis.logcat

import dev.perf.analysis.model.PotentialAnrHint
import dev.perf.analysis.model.StrictModeViolation

/**
 * Unified logcat stream for a single `logcat --pid` session.
 */
sealed interface SessionLogcatEvent {
    data class StrictMode(val violation: StrictModeViolation) : SessionLogcatEvent
    data class AnrHint(val hint: PotentialAnrHint) : SessionLogcatEvent
}
