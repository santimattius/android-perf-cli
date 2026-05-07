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
}
