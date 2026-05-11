package dev.perf.analysis.network

/**
 * Parses `dumpsys netstats --uid N` output to extract total rx/tx bytes for a UID.
 *
 * Format (modern): `st=0 uid=10234 set=DEFAULT tag=0x0 rx_bytes=12345 tx_bytes=678 ...`
 * Format (legacy):  `uid=10234 set=DEFAULT tag=0x0 rx=1000 tx=200`
 *
 * Primary regex tries `rx_bytes`/`tx_bytes` first. Falls back to `rx`/`tx` only when
 * the primary finds zero entries for the target UID.
 */
internal object NetstatsParser {

    /** Modern format: `rx_bytes=N tx_bytes=N` on the same line as `uid=N`. */
    private val PRIMARY = Regex("""uid=(\d+)[^\n]*?\brx_bytes=?(\d+)\b[^\n]*?\btx_bytes=?(\d+)\b""")

    /** Legacy format: `rx=N tx=N` without `_bytes` suffix. */
    private val FALLBACK = Regex("""uid=(\d+)[^\n]*?\brx=(\d+)\s+tx=(\d+)\b""")

    /**
     * Parses [raw] netstats output and sums all entries for [uid].
     * Returns a [NetworkSnapshot] with `capturedAtMs = 0` (caller sets the real timestamp).
     * Returns `null` if no entries for [uid] are found in either regex form.
     */
    fun parse(raw: String, uid: Int): NetworkSnapshot? {
        val primary = sumEntries(PRIMARY, raw, uid)
        if (primary != null) return primary
        return sumEntries(FALLBACK, raw, uid)
    }

    private fun sumEntries(regex: Regex, raw: String, uid: Int): NetworkSnapshot? {
        var rx = 0L
        var tx = 0L
        var matched = false
        for (m in regex.findAll(raw)) {
            if (m.groupValues[1].toIntOrNull() != uid) continue
            rx += m.groupValues[2].toLong()
            tx += m.groupValues[3].toLong()
            matched = true
        }
        return if (matched) NetworkSnapshot(rx, tx, capturedAtMs = 0L) else null
    }
}
