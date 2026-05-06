package dev.perf.analysis.startup

import dev.perf.analysis.model.Severity
import dev.perf.analysis.model.startup.StartupSample
import dev.perf.analysis.model.startup.StartupSource
import dev.perf.analysis.model.startup.StartupType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupAggregatorTest {

    private val thresholds = ThresholdsView(maxColdStartMs = 5000, maxWarmStartMs = 2000, maxHotStartMs = 1500)

    @Test
    fun `p50 and p95 for three cold samples`() {
        val samples =
            listOf(
                sample(1200),
                sample(1800),
                sample(1500),
            )
        val stats = StartupAggregator.aggregate(StartupType.COLD, samples, thresholds)
        assertEquals(1500, stats.p50Ms)
        assertEquals(1800, stats.p95Ms)
    }

    @Test
    fun `p50 and p95 for five warm samples`() {
        val samples = listOf(900, 1100, 950, 1300, 1050).map { sample(it, StartupType.WARM) }
        val stats = StartupAggregator.aggregate(StartupType.WARM, samples, thresholds)
        assertEquals(1050, stats.p50Ms)
        assertEquals(1300, stats.p95Ms)
    }

    @Test
    fun `pre condition drift excluded from percentiles`() {
        val samples =
            listOf(
                sample(1000),
                sample(9000, flags = listOf("pre_condition_drift")),
                sample(1100),
                sample(1200),
            )
        val stats = StartupAggregator.aggregate(StartupType.COLD, samples, thresholds)
        assertEquals(1100, stats.p50Ms)
        assertEquals(1200, stats.p95Ms)
    }

    @Test
    fun `parsedOk false when more than half fallback`() {
        val samples =
            listOf(
                sample(100, source = StartupSource.AM_START_FALLBACK, flags = listOf("no_displayed_line")),
                sample(100, source = StartupSource.AM_START_FALLBACK, flags = listOf("no_displayed_line")),
                sample(100),
            )
        assertFalse(StartupAggregator.computeParsedOk(samples))
    }

    @Test
    fun `parsedOk true when half or fewer fallback`() {
        val samples =
            listOf(
                sample(100, source = StartupSource.AM_START_FALLBACK, flags = listOf("no_displayed_line")),
                sample(200),
                sample(300),
            )
        assertTrue(StartupAggregator.computeParsedOk(samples))
    }

    private fun sample(
        ttid: Int,
        type: StartupType = StartupType.COLD,
        flags: List<String> = emptyList(),
        source: StartupSource = StartupSource.LOGCAT_DISPLAYED,
    ) = StartupSample(
        type = type,
        ttidMs = ttid,
        ttfdMs = null,
        amStartTotalTimeMs = ttid,
        amStartWaitTimeMs = ttid,
        amStartLaunchState = type.name,
        source = source,
        flags = flags,
    )
}
