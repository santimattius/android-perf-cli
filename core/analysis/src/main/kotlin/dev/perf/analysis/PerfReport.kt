package dev.perf.analysis

import dev.perf.analysis.model.ColdStartMeasurement
import dev.perf.analysis.model.MemoryLeak
import dev.perf.analysis.model.startup.StartupReport
import dev.perf.analysis.model.PotentialAnrHint
import dev.perf.analysis.model.ProcSnapshotSummary
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StrictModeViolation
import dev.perf.analysis.model.ThresholdBreach
import dev.perf.analysis.model.WakelockReport
import java.io.File

data class PerfReport(
    val packageName: String,
    val strictModeViolations: List<StrictModeViolation>,
    val renderingIncident: RenderingIncident,
    val memoryLeaks: List<MemoryLeak>,
    val potentialAnrHints: List<PotentialAnrHint> = emptyList(),
    /** Multi-type startup capture (TTID/TTFD); null when the startup engine is disabled. */
    val startup: StartupReport? = null,
    /**
     * Legacy cold-start view derived from [startup] when the new engine runs.
     * Prefer [startup] for full sample data.
     */
    @Deprecated("Use startup.cold instead; this adapter will be removed in a future release.")
    val coldStart: ColdStartMeasurement? = null,
    /**
     * Scenario thresholds that were exceeded for this run (empty if no
     * scenario was provided or all thresholds were satisfied). Rendered as
     * a top-level section so agents reading `report.md` can see what failed.
     */
    val thresholdBreaches: List<ThresholdBreach> = emptyList(),
    /** Local path after pull; open in https://ui.perfetto.dev */
    val perfettoTraceFile: File? = null,
    /** Non-null when perfetto was skipped or failed (inform user). */
    val perfettoMessage: String? = null,
    /** When the `vitals` preset failed and was retried with baseline categories. */
    val perfettoDegradedFromVitalsPreset: Boolean = false,
    val procSnapshot: ProcSnapshotSummary? = null,
    /** Paths relative to report file (or labels) for screenshots taken during the run. */
    val screenshotArtifacts: List<String> = emptyList(),
    /** Partial wake-lock stats when the wakelock engine ran; null when disabled or capture failed. */
    val wakelockReport: WakelockReport? = null,
) {
    val hasIssues: Boolean
        get() = strictModeViolations.isNotEmpty()
            || memoryLeaks.isNotEmpty()
            || renderingIncident.severity != Severity.INFO
            || potentialAnrHints.any { it.severity != Severity.INFO }
            || thresholdBreaches.isNotEmpty()
            || (startup?.overallSeverity?.let { it != Severity.INFO } == true)
            || (startup == null && coldStart?.severity?.let { it != Severity.INFO } == true) || (wakelockReport?.severity?.let { it != Severity.INFO } == true)

    /**
     * Design decision: [Severity] is declared `CRITICAL, WARNING, INFO`
     * (ordinal 0 = worst). We aggregate by picking the **minimum** ordinal —
     * i.e. the worst severity across all signals — so a single CRITICAL
     * leak pins the overall run to CRITICAL regardless of other INFO noise.
     */
    val overallSeverity: Severity
        get() = sequenceOf(
            memoryLeaks.minOfOrNull { it.severity },
            strictModeViolations.minOfOrNull { it.severity },
            renderingIncident.severity,
            potentialAnrHints.minOfOrNull { it.severity },
            startup?.overallSeverity,
            coldStart?.severity.takeIf { startup == null },
            thresholdBreaches.minOfOrNull { it.severity },
            wakelockReport?.severity,
        ).filterNotNull().minOrNull() ?: Severity.INFO
}
