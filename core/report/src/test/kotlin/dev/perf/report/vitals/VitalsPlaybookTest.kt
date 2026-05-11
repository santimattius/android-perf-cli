package dev.perf.report.vitals

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VitalsPlaybookTest {

    // TASK-34: exitSignalSection returns non-empty list with required URLs
    @Test
    fun `exitSignalSection_containsAnrAndCrashUrls`() {
        val section = VitalsPlaybook.exitSignalSection()
        assertFalse(section.isEmpty())
        assertTrue(section.any { it.contains("https://developer.android.com/topic/performance/vitals/anr") })
        assertTrue(section.any { it.contains("https://developer.android.com/topic/performance/vitals/crash") })
    }

    // Task 1.5: VitalsMetric sourceFooter tests

    @Test
    fun `sourceFooter for STARTUP starts with correct prefix`() {
        val footer = VitalsPlaybook.sourceFooter(VitalsMetric.COLD_START)
        assertTrue(footer.startsWith("_Source: [Android Vitals]("), "Expected footer to start with prefix, got: $footer")
    }

    @Test
    fun `sourceFooter for STARTUP ends with correct suffix`() {
        val footer = VitalsPlaybook.sourceFooter(VitalsMetric.COLD_START)
        assertTrue(footer.endsWith(")._"), "Expected footer to end with ')._', got: $footer")
    }

    @Test
    fun `sourceFooter returns non-empty string for all VitalsMetric members`() {
        VitalsMetric.entries.forEach { metric ->
            val footer = VitalsPlaybook.sourceFooter(metric)
            assertTrue(footer.isNotEmpty(), "Expected non-empty footer for $metric")
        }
    }

    @Test
    fun `sourceFooter for every member starts with Android Vitals prefix`() {
        VitalsMetric.entries.forEach { metric ->
            val footer = VitalsPlaybook.sourceFooter(metric)
            assertTrue(
                footer.startsWith("_Source: [Android Vitals]("),
                "Expected footer for $metric to start with '_Source: [Android Vitals](', got: $footer",
            )
        }
    }

    @Test
    fun `sourceFooter for every member ends with closing suffix`() {
        VitalsMetric.entries.forEach { metric ->
            val footer = VitalsPlaybook.sourceFooter(metric)
            assertTrue(
                footer.endsWith(")._"),
                "Expected footer for $metric to end with ')._', got: $footer",
            )
        }
    }

    @Test
    fun `VitalsMetric has at least 10 members`() {
        assertTrue(VitalsMetric.entries.size >= 10, "Expected at least 10 VitalsMetric members")
    }

    @Test
    fun `all VitalsMetric URLs are non-empty`() {
        VitalsMetric.entries.forEach { metric ->
            assertTrue(metric.url.isNotEmpty(), "Expected non-empty URL for $metric")
        }
    }
}
