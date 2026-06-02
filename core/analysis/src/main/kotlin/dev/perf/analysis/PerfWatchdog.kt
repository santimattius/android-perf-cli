package dev.perf.analysis

import dev.perf.adb.AdbClient
import dev.perf.analysis.exit.ExitSignalCapture
import dev.perf.analysis.gfx.GfxInfoCapture
import dev.perf.analysis.network.BackgroundNetworkCapture
import dev.perf.analysis.network.NetworkSnapshot
import dev.perf.analysis.permission.PermissionDenialCapture
import dev.perf.analysis.wakelock.StuckWakelockDetector
import dev.perf.analysis.wakelock.WakelockCapture
import dev.perf.analysis.wifi.WifiScanCapture
import dev.perf.analysis.wifi.WifiScanSnapshot
import dev.perf.analysis.logcat.LogcatParser
import dev.perf.analysis.logcat.SessionLogcatEvent
import dev.perf.analysis.memory.HeapAnalyzer
import dev.perf.analysis.model.ExitSignalReport
import dev.perf.analysis.model.PermissionDenialReport
import dev.perf.analysis.model.PerfIncident
import dev.perf.analysis.model.PotentialAnrHint
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.StrictModeViolation
import dev.perf.analysis.model.WakelockReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    private val exitSignalCapture = ExitSignalCapture(device)
    private val backgroundNetworkCapture = BackgroundNetworkCapture(device)
    private val wifiScanCapture = WifiScanCapture(device)
    private val permissionDenialCapture = PermissionDenialCapture(device)

    private val _incidents = MutableSharedFlow<PerfIncident>(extraBufferCapacity = 512)
    val incidents: SharedFlow<PerfIncident> = _incidents.asSharedFlow()

    private val collectedStrict = mutableListOf<StrictModeViolation>()
    private val collectedAnr = mutableListOf<PotentialAnrHint>()
    private var logcatJob: Job? = null
    private var exitSignalJob: Job? = null
    private var exitSignalResult: ExitSignalReport? = null
    private var permissionDenialJob: Job? = null
    private var permissionDenialResult: PermissionDenialReport? = null

    // Background signal baseline snapshots (captured at session start, after reset)
    private var netStartSnapshot: NetworkSnapshot? = null
    private var wifiStartSnapshot: WifiScanSnapshot? = null
    private var wakelockStartSnapshot: WakelockReport? = null
    private var baselineJob: Job? = null

    fun start() {
        if (monitor.rendering) {
            device.shell("dumpsys", "gfxinfo", packageName, "reset")
        }
        if (monitor.wakelocks) {
            // Reset MUST happen before any batterystats-based baselines
            wakelockCapture.reset(packageName)
        }
        if (monitor.exitSignals) {
            exitSignalJob = scope.launch {
                exitSignalResult = exitSignalCapture.capture(packageName, 0L)
            }
        }
        if (monitor.permissionDenials) {
            permissionDenialJob = scope.launch {
                permissionDenialResult = permissionDenialCapture.capture(packageName)
            }
        }

        // Capture baselines AFTER reset (ordering is critical for batterystats accuracy).
        // Job is stored so stop() can await completion before computing deltas.
        baselineJob = scope.launch {
            captureBaselines()
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

    /** Captures all enabled baseline snapshots for the session. Called after [WakelockCapture.reset]. */
    private suspend fun captureBaselines() {
        if (monitor.backgroundWifi) {
            wifiStartSnapshot = wifiScanCapture.snapshot(packageName)
        }
        if (monitor.backgroundNetwork) {
            netStartSnapshot = backgroundNetworkCapture.snapshot(packageName)
        }
        if (monitor.stuckWakelocks && monitor.wakelocks) {
            // Near-empty baseline immediately after reset — used as "start" for stuck detection
            wakelockStartSnapshot = wakelockCapture.capture(packageName)
        }
    }

    suspend fun stop(analyzeHeap: Boolean = false): PerfReport {
        permissionDenialJob?.cancelAndJoin()
        exitSignalJob?.cancel()
        withTimeoutOrNull(1_500) { exitSignalJob?.join() }
        logcatJob?.cancel()
        // Ensure baseline snapshots are captured before computing end-of-session deltas
        baselineJob?.join()

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

        val (bgNetwork, bgWifi, stuckWakelocks) = computeDeltas(wakelocks)

        return PerfReport(
            packageName = packageName,
            strictModeViolations = strictFinal,
            renderingIncident = rendering,
            memoryLeaks = leaks,
            potentialAnrHints = anrFinal,
            wakelockReport = wakelocks,
            exitSignalReport = exitSignalResult,
            backgroundNetwork = bgNetwork,
            backgroundWifi = bgWifi,
            stuckWakelocks = stuckWakelocks,
            permissionDenials = permissionDenialResult,
        )
    }

    /**
     * Computes the three background-signal deltas using end-of-session snapshots.
     * Returns a triple: (backgroundNetworkReport, wifiScanReport, stuckWakelockReport).
     */
    private suspend fun computeDeltas(
        wakelockEnd: WakelockReport?,
    ): Triple<
        dev.perf.analysis.model.BackgroundNetworkReport?,
        dev.perf.analysis.model.WifiScanReport?,
        dev.perf.analysis.model.StuckWakelockReport?,
    > {
        val bgNetwork = if (monitor.backgroundNetwork) {
            val start = netStartSnapshot
            val end = backgroundNetworkCapture.snapshot(packageName)
            if (start != null && end != null) backgroundNetworkCapture.delta(start, end) else null
        } else null

        val bgWifi = if (monitor.backgroundWifi) {
            val start = wifiStartSnapshot
            val end = wifiScanCapture.snapshot(packageName)
            if (start != null && end != null) wifiScanCapture.delta(start, end) else null
        } else null

        val stuckWakelocks = if (monitor.stuckWakelocks && monitor.wakelocks) {
            StuckWakelockDetector.detect(wakelockStartSnapshot, wakelockEnd)
        } else null

        return Triple(bgNetwork, bgWifi, stuckWakelocks)
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
