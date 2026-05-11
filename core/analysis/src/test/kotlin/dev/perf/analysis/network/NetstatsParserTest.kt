package dev.perf.analysis.network

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NetstatsParserTest {

    // ── T-2.1: Modern form rx_bytes/tx_bytes ─────────────────────────────────

    @Test
    fun `parse sums rx_bytes and tx_bytes for matching uid`() {
        val raw = """
            Active interfaces:
              iface=wlan0 ident=...
            History:
              st=0 uid=10234 set=DEFAULT tag=0x0 rx_bytes=12345 tx_bytes=678 rx_packets=10 tx_packets=8
              st=0 uid=10234 set=FOREGROUND tag=0x0 rx_bytes=200 tx_bytes=50 rx_packets=2 tx_packets=1
              st=0 uid=10099 set=DEFAULT tag=0x0 rx_bytes=9999 tx_bytes=9999 rx_packets=5 tx_packets=5
        """.trimIndent()
        val result = NetstatsParser.parse(raw, 10234)
        assertEquals(12545L, result?.rxBytes, "rxBytes should sum all uid=10234 entries")
        assertEquals(728L, result?.txBytes, "txBytes should sum all uid=10234 entries")
    }

    @Test
    fun `parse returns null when uid absent from all lines`() {
        val raw = """
            st=0 uid=10099 set=DEFAULT tag=0x0 rx_bytes=9999 tx_bytes=9999
        """.trimIndent()
        assertNull(NetstatsParser.parse(raw, 10234))
    }

    // ── T-2.2: Legacy fallback rx/tx ─────────────────────────────────────────

    @Test
    fun `parse uses fallback rx tx when primary regex finds no entries`() {
        val raw = """
            uid=10234 set=DEFAULT tag=0x0 rx=1000 tx=200
        """.trimIndent()
        val result = NetstatsParser.parse(raw, 10234)
        assertEquals(1000L, result?.rxBytes)
        assertEquals(200L, result?.txBytes)
    }

    @Test
    fun `parse skips malformed fallback line and sums valid lines`() {
        // First line is malformed (missing tx=), second is valid
        val raw = """
            uid=10234 set=DEFAULT tag=0x0 rx=500
            uid=10234 set=DEFAULT tag=0x0 rx=600 tx=300
        """.trimIndent()
        // The malformed line won't match either regex (missing tx=), only valid line matches
        val result = NetstatsParser.parse(raw, 10234)
        assertEquals(600L, result?.rxBytes)
        assertEquals(300L, result?.txBytes)
    }

    @Test
    fun `parse returns null for empty input`() {
        assertNull(NetstatsParser.parse("", 10234))
    }

    @Test
    fun `parse does not mix uid entries from different uids`() {
        val raw = """
            uid=10234 set=DEFAULT tag=0x0 rx=1000 tx=200
            uid=10099 set=DEFAULT tag=0x0 rx=5000 tx=9000
        """.trimIndent()
        val result = NetstatsParser.parse(raw, 10234)
        assertEquals(1000L, result?.rxBytes)
        assertEquals(200L, result?.txBytes)
    }

    @Test
    fun `parse sets capturedAtMs to 0 as placeholder`() {
        val raw = "uid=10234 set=DEFAULT tag=0x0 rx=1000 tx=200"
        val result = NetstatsParser.parse(raw, 10234)
        assertEquals(0L, result?.capturedAtMs)
    }
}
