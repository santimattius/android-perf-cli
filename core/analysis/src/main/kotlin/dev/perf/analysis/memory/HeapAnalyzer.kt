package dev.perf.analysis.memory

import dev.perf.adb.AdbClient
import dev.perf.analysis.model.MemoryLeak
import dev.perf.analysis.model.Severity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import shark.AndroidObjectInspectors
import shark.AndroidReferenceMatchers
import shark.HeapAnalyzer as SharkHeapAnalyzer
import shark.Hprof
import shark.HprofHeapGraph
import shark.KeyedWeakReferenceFinder
import shark.OnAnalysisProgressListener
import java.io.File

private const val DEVICE_HEAP_PATH = "/data/local/tmp/perf_heap.hprof"
private const val HEAP_DUMP_POLL_MS = 500L
private const val HEAP_DUMP_TIMEOUT_MS = 30_000L

/**
 * Triggers a heap dump via `am dumpheap`, pulls the HPROF to the host,
 * and analyzes it with Shark to find memory leaks.
 *
 * The HPROF is written to [workDir] and deleted after analysis unless
 * [keepHprof] is true (useful for manual inspection with Android Studio).
 */
class HeapAnalyzer(
    private val device: AdbClient,
    private val workDir: File = File(System.getProperty("java.io.tmpdir"), "perf-cli"),
) {

    suspend fun analyze(
        packageName: String,
        keepHprof: Boolean = false,
    ): List<MemoryLeak> = withContext(Dispatchers.IO) {
        workDir.mkdirs()
        val localHprof = File(workDir, "${packageName}_heap.hprof")

        try {
            dumpAndPull(packageName, localHprof)
            runSharkAnalysis(localHprof)
        } finally {
            if (!keepHprof) localHprof.delete()
            // Clean up device side regardless
            runCatching { device.shell("rm", "-f", DEVICE_HEAP_PATH) }
        }
    }

    private suspend fun dumpAndPull(packageName: String, dest: File) {
        device.shellAsync("am", "dumpheap", packageName, DEVICE_HEAP_PATH)

        // am dumpheap is async — poll until the file appears on device
        val deadline = System.currentTimeMillis() + HEAP_DUMP_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val exists = device.shell("test", "-f", DEVICE_HEAP_PATH, "&&", "echo", "ok")
                .trim() == "ok"
            if (exists) break
            delay(HEAP_DUMP_POLL_MS)
        }

        device.pullAsync(DEVICE_HEAP_PATH, dest)
    }

    private fun runSharkAnalysis(hprofFile: File): List<MemoryLeak> {
        val heapAnalyzer = SharkHeapAnalyzer(OnAnalysisProgressListener.NO_OP)

        val result = Hprof.open(hprofFile).use { hprof ->
            val graph = HprofHeapGraph.indexHprof(hprof, null)
            heapAnalyzer.analyze(
                heapDumpFile = hprofFile,
                graph = graph,
                leakingObjectFinder = KeyedWeakReferenceFinder,
                referenceMatchers = AndroidReferenceMatchers.appDefaults,
                computeRetainedHeapSize = true,
                objectInspectors = AndroidObjectInspectors.appDefaults,
            )
        }

        return when (result) {
            is shark.HeapAnalysisSuccess -> result.applicationLeaks.map { leak ->
                val trace = leak.leakTraces.firstOrNull()
                val leakingObj = trace?.leakingObject?.className ?: "Unknown"
                val retained = trace?.retainedHeapByteSize ?: 0
                val path = trace?.referencePath?.joinToString(" → ") { it.referenceName }.orEmpty()

                MemoryLeak(
                    severity = Severity.CRITICAL,
                    message = "Leak detected in $leakingObj (~${retained / 1024}KB retained)",
                    leakingObject = leakingObj,
                    retainedBytes = retained.toLong(),
                    shortestPath = path,
                )
            }

            is shark.HeapAnalysisFailure ->
                listOf(
                    MemoryLeak(
                        severity = Severity.WARNING,
                        message = "Heap analysis failed: ${result.exception.message}",
                        leakingObject = "N/A",
                        retainedBytes = 0L,
                        shortestPath = "",
                    ),
                )
        }
    }
}
