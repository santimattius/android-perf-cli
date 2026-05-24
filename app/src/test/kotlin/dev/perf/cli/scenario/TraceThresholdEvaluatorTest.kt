package dev.perf.cli.scenario

import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.RenderingIncident
import dev.perf.trace.model.AnalyzeResult
import dev.perf.trace.model.TraceAnalysisSummary
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TraceThresholdEvaluatorTest {

    @Test
    fun maxMainThreadBlockedMs_breachesWhenExceeded() {
        val report =
            baseReport().copy(
                perfettoAnalyze =
                    TraceAnalysisSummary(
                        queryPackVersion = "v1",
                        domains =
                            mapOf(
                                "rendering" to
                                    AnalyzeResult(
                                        domain = "rendering",
                                        queryPackVersion = "v1",
                                        tracePath = "/tmp/t.perfetto-trace",
                                        metrics = mapOf("main_thread_blocked_ms" to JsonPrimitive(800)),
                                    ),
                            ),
                    ),
            )
        val out = ThresholdEvaluator.evaluate(report, ThresholdConfig(maxMainThreadBlockedMs = 500))
        assertTrue(out.any { it.message.contains("main thread blocked") })
    }

    @Test
    fun maxMainThreadBlockedMs_skippedWithoutAnalyze() {
        val out = ThresholdEvaluator.evaluate(baseReport(), ThresholdConfig(maxMainThreadBlockedMs = 500))
        assertEquals(0, out.count { it.message.contains("Trace main") })
    }

    private fun baseReport(): PerfReport =
        PerfReport(
            packageName = "com.example",
            strictModeViolations = emptyList(),
            renderingIncident =
                RenderingIncident(
                    severity = dev.perf.analysis.model.Severity.INFO,
                    message = "ok",
                    jankyFrames = 0,
                    frozenFrames = 0,
                    totalFrames = 10,
                    p90Ms = 10.0,
                    p95Ms = 12.0,
                    p99Ms = 16.0,
                ),
            memoryLeaks = emptyList(),
        )
}
