package dev.perf.analysis.wakelock

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WakelockParserTest {

    @Test
    fun `parseDurationMs handles seconds and milliseconds`() {
        assertEquals(5000, WakelockParser.parseDurationMs("5.0s"))
        assertEquals(1200, WakelockParser.parseDurationMs("1.2s"))
        assertEquals(800, WakelockParser.parseDurationMs("800ms"))
    }

    @Test
    fun `parseDurationMs handles composite minute hour tokens`() {
        assertEquals(90_000, WakelockParser.parseDurationMs("1m30s"))
        assertEquals(8_100_000, WakelockParser.parseDurationMs("2h15m"))
        assertEquals(8_130_000, WakelockParser.parseDurationMs("2h15m30s"))
    }

    @Test
    fun `parse extracts wake lock from uid block`() {
        val raw =
            """
            Misc stats:
            Uid 10123: com.example.app
              Wake lock *alarm*: 1 times, 5.0s realtime
              Wake lock Network: 2 times, 800ms realtime
            Uid 10124: com.other.app
              Wake lock x: 9 times, 1s realtime
            """.trimIndent()

        val report = WakelockParser.parse(raw, "com.example.app")
        assertNotNull(report)
        assertEquals(2, report.entries.size)
        assertEquals("*alarm*", report.entries[0].name)
        assertEquals(1, report.entries[0].count)
        assertEquals(5000, report.entries[0].totalDurationMs)
        assertEquals(5800, report.totalDurationMs)
    }

    @Test
    fun `parse returns empty entries when uid block has no wake locks`() {
        val raw =
            """
            Uid 10123: com.example.app
              Aggregated stats not shown
            """.trimIndent()
        val report = WakelockParser.parse(raw, "com.example.app")
        assertNotNull(report)
        assertEquals(0, report.entries.size)
        assertEquals(0, report.totalDurationMs)
    }

    @Test
    fun `parse returns null when package uid block not found`() {
        val raw =
            """
            Uid 10124: com.other
              Wake lock x: 1 times, 1s realtime
            """.trimIndent()
        assertNull(WakelockParser.parse(raw, "com.example.app"))
    }
}
