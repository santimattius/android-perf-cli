package dev.perf.analysis.logcat

import dev.perf.analysis.model.PotentialAnrHintType
import dev.perf.analysis.model.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PotentialAnrParseTest {

    @Test
    fun `skipped frames above threshold yields hint`() {
        val h = parsePotentialAnrLine(
            "01-15 10:00:00.100  1234  5678 I Choreographer: Skipped 42 frames!  The application may be doing too much work on its main thread.",
        )
        assertNotNull(h)
        assertEquals(PotentialAnrHintType.CHOREOGRAPHER_SKIPPED_FRAMES, h.hintType)
        assertEquals(Severity.WARNING, h.severity)
    }

    @Test
    fun `skipped frames below 5 ignored`() {
        assertNull(parsePotentialAnrLine("I Choreographer: Skipped 3 frames!"))
    }

    @Test
    fun `input dispatching timeout is critical`() {
        val h = parsePotentialAnrLine(
            "Input dispatching timed out (5008ms) ...",
        )
        assertNotNull(h)
        assertEquals(PotentialAnrHintType.INPUT_DISPATCH_TIMEOUT_RISK, h.hintType)
        assertEquals(Severity.CRITICAL, h.severity)
    }
}
