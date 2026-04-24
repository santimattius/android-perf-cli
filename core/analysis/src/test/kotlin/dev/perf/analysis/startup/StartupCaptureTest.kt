package dev.perf.analysis.startup

import dev.perf.analysis.model.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StartupCaptureTest {

    @Test
    fun `parses TotalTime from typical am start -W output`() {
        val raw = """
            Starting: Intent { act=android.intent.action.MAIN cat=[android.intent.category.LAUNCHER] flg=0x10000000 pkg=com.example }
            Status: ok
            LaunchState: COLD
            Activity: com.example/.MainActivity
            TotalTime: 1234
            WaitTime: 1234
            Complete
        """.trimIndent()

        val m = StartupCapture.parseAmStartWOutput(raw)
        assertTrue(m.parsedOk)
        assertEquals(1234, m.totalTimeMs)
        assertEquals(1234, m.waitTimeMs)
        assertEquals("COLD", m.launchState)
        assertEquals("ok", m.status)
        assertEquals(Severity.INFO, m.severity)
    }

    @Test
    fun `prefers TotalTime over ThisTime when both present`() {
        val raw = """
            ThisTime: 100
            TotalTime: 200
            Complete
        """.trimIndent()
        val m = StartupCapture.parseAmStartWOutput(raw)
        assertEquals(200, m.totalTimeMs)
    }

    @Test
    fun `marks critical when very slow`() {
        val m = StartupCapture.parseAmStartWOutput("TotalTime: 6000\nComplete\n")
        assertEquals(Severity.CRITICAL, m.severity)
    }

    @Test
    fun `parsedOk false when no timing line`() {
        val m = StartupCapture.parseAmStartWOutput("Status: ok\nComplete\n")
        assertFalse(m.parsedOk)
    }
}
