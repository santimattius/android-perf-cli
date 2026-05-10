package dev.perf.cli.history

import dev.perf.analysis.model.SessionRateSummary

/**
 * Pure function: List<SessionHistoryEntry> → SessionRateSummary.
 *
 * No I/O, no state — deterministic for any given inputs.
 *
 * Edge cases:
 * - Empty list → totalRuns=0, all rates=0.0, hasExitSignalsCaveat=false.
 * - Zero totalFrames → slowSessionRate=0.0 (guard prevents division by zero).
 * - When [entries] exceeds [windowSize], only the LAST [windowSize] entries are used.
 */
object RateCalculator {

    /**
     * Computes rolling-window rates from [entries].
     *
     * @param entries List of history entries, expected in chronological ascending order.
     * @param windowSize Maximum number of entries to consider (takes the LAST N).
     */
    fun compute(
        entries: List<SessionHistoryEntry>,
        windowSize: Int = 30,
    ): SessionRateSummary {
        val window = entries.takeLast(windowSize)

        if (window.isEmpty()) {
            return SessionRateSummary(
                totalRuns = 0,
                anrRate = 0.0,
                crashRate = 0.0,
                slowSessionRate = 0.0,
                windowSize = windowSize,
                hasExitSignalsCaveat = false,
            )
        }

        val total = window.size.toDouble()

        val anrRate = window.count { it.anrCount > 0 } / total
        val crashRate = window.count { it.crashCount > 0 } / total
        val slowSessionRate = window.count { isSlowSession(it) } / total
        val hasExitSignalsCaveat = window.any { !it.exitSignalsEnabled }

        return SessionRateSummary(
            totalRuns = window.size,
            anrRate = anrRate,
            crashRate = crashRate,
            slowSessionRate = slowSessionRate,
            windowSize = windowSize,
            hasExitSignalsCaveat = hasExitSignalsCaveat,
        )
    }

    private fun isSlowSession(entry: SessionHistoryEntry): Boolean =
        entry.totalFrames > 0 && entry.slowFrames.toDouble() / entry.totalFrames >= 0.25
}
