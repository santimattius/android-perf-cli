package dev.perf.analysis

import dev.perf.adb.AdbClient
import dev.perf.analysis.gfx.GfxInfoCapture
import dev.perf.analysis.wakelock.WakelockCapture
import dev.perf.analysis.logcat.LogcatParser
import dev.perf.analysis.logcat.SessionLogcatEvent
import dev.perf.analysis.memory.HeapAnalyzer
import dev.perf.analysis.model.PerfIncident
import dev.perf.analysis.model.PotentialAnrHint
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StrictModeViolation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * Coordinates perf engines for a monitoring session.
 */
class PerfWatchdog(
    private val device: AdbClient,
    private val packageName: String,
    private val monitor: MonitorConfig = MonitorConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO),
) {
    private val logcatParser = LogcatParser(
        device,
        collectStrictMode = monitor.strictMode,
        collectAnrHints = monitor.anrHints,
    )
    private val gfxCapture = GfxInfoCapture(device)
    private val heapAnalyzer = HeapAnalyzer(device)
    private val wakelockCapture = WakelockCapture(device)

    private val _incidents = MutableSharedFlow<PerfIncident>(extraBufferCapacity = 512)
    val incidents: SharedFlow<PerfIncident> = _incidents.asSharedFlow()

    private val collectedStrict = mutableListOf<StrictModeViolation>()
    private val collectedAnr = mutableListOf<PotentialAnrHint>()
    private var logcatJob: Job? = null

    fun start() {
        if (monitor.rendering) {
            device.shell("dumpsys", "gfxinfo", packageName, "reset")
        }
        if (monitor.wakelocks) {
            wakelockCapture.reset(packageName)
        }

        if (!monitor.strictMode && !monitor.anrHints) {
            return
        }

        logcatJob = scope.launch {
            logcatParser.sessionEvents(packageName).collect { event ->
                when (event) {
                    is SessionLogcatEvent.StrictMode -> {
                        collectedStrict.add(event.violation)
                        _incidents.emit(event.violation)
                    }
                    is SessionLogcatEvent.AnrHint -> {
                        collectedAnr.add(event.hint)
                        _incidents.emit(event.hint)
                    }
                }
            }
        }
    }

    suspend fun stop(analyzeHeap: Boolean = false): PerfReport {
        logcatJob?.cancel()

        val renderingDeferred = if (monitor.rendering) {
            scope.async { gfxCapture.capture(packageName) }
        } else {
            null
        }

        val leaksDeferred = if (analyzeHeap) {
            scope.async { heapAnalyzer.analyze(packageName) }
        } else {
            null
        }

        val wakelockDeferred = if (monitor.wakelocks) {
            scope.async { wakelockCapture.capture(packageName) }
        } else {
            null
        }

        val rendering = renderingDeferred?.await() ?: disabledRenderingPlaceholder()
        val leaks = leaksDeferred?.await() ?: emptyList()
        val wakelocks = wakelockDeferred?.await()

        val strictFinal = collectedStrict.toList()
        val anrFinal = dedupeAnrHints(collectedAnr)

        return PerfReport(
            packageName = packageName,
            strictModeViolations = strictFinal,
            renderingIncident = rendering,
            memoryLeaks = leaks,
            potentialAnrHints = anrFinal,
            wakelockReport = wakelocks,
        )
    }

    private fun dedupeAnrHints(hints: List<PotentialAnrHint>): List<PotentialAnrHint> {
        if (hints.size <= 80) return hints
        return hints.distinctBy { it.hintType to it.message }.take(80)
    }

    private fun disabledRenderingPlaceholder() = RenderingIncident(
        severity = Severity.INFO,
        message = "Rendering capture disabled for this run",
        jankyFrames = 0,
        frozenFrames = 0,
        totalFrames = 0,
        p90Ms = 0.0,
        p95Ms = 0.0,
        p99Ms = 0.0,
    )
}
