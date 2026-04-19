package dev.perf.analysis

import dev.perf.analysis.model.MemoryLeak
import dev.perf.analysis.model.PotentialAnrHint
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StrictModeViolation
import java.io.File

data class PerfReport(
    val packageName: String,
    val strictModeViolations: List<StrictModeViolation>,
    val renderingIncident: RenderingIncident,
    val memoryLeaks: List<MemoryLeak>,
    val potentialAnrHints: List<PotentialAnrHint> = emptyList(),
    /** Local path after pull; open in https://ui.perfetto.dev */
    val perfettoTraceFile: File? = null,
    /** Non-null when perfetto was skipped or failed (inform user). */
    val perfettoMessage: String? = null,
) {
    val hasIssues: Boolean
        get() = strictModeViolations.isNotEmpty()
            || memoryLeaks.isNotEmpty()
            || renderingIncident.severity != Severity.INFO
            || potentialAnrHints.any { it.severity != Severity.INFO }

    val overallSeverity: Severity
        get() = sequenceOf(
            memoryLeaks.maxOfOrNull { it.severity },
            strictModeViolations.maxOfOrNull { it.severity },
            renderingIncident.severity,
            potentialAnrHints.maxOfOrNull { it.severity },
        ).filterNotNull().maxOrNull() ?: Severity.INFO
}
