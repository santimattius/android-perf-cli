package dev.perf.analysis.wifi

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WifiScanParserTest {

    private val pkg = "com.example.app"

    // Fixture with wifi scan line in the correct UID block
    private val rawWithScanLine = """
        Statistics since last unplugged:
          Uid 10123: com.example.app
            Wifi scan: 12 times
            Wake lock SyncManager: 3 times, 1m 30s 200ms realtime
          Uid 10124: com.other.app
            Wifi scan: 99 times
    """.trimIndent()

    // Fixture without wifi scan line
    private val rawNoScanLine = """
        Statistics since last unplugged:
          Uid 10123: com.example.app
            Wake lock SyncManager: 3 times, 1m 30s 200ms realtime
    """.trimIndent()

    // Fixture with wrong package name
    private val rawWrongPackage = """
        Statistics since last unplugged:
          Uid 10124: com.other.app
            Wifi scan: 99 times
    """.trimIndent()

    @Test
    fun `parse returns scan count when Wifi scan line present in correct uid block`() {
        val count = WifiScanParser.parse(rawWithScanLine, pkg)
        assertNotNull(count)
        assertEquals(12, count)
    }

    @Test
    fun `parse returns 0 when no Wifi scan line in uid block`() {
        val count = WifiScanParser.parse(rawNoScanLine, pkg)
        // returns 0 (not null) — "no scans seen"
        assertNotNull(count)
        assertEquals(0, count)
    }

    @Test
    fun `parse returns null when package block not found`() {
        val count = WifiScanParser.parse(rawWrongPackage, pkg)
        assertNull(count)
    }

    @Test
    fun `parse does not pick up scan count from other package block`() {
        val count = WifiScanParser.parse(rawWithScanLine, pkg)
        // Should be 12 (com.example.app), NOT 99 (com.other.app)
        assertEquals(12, count)
    }
}
