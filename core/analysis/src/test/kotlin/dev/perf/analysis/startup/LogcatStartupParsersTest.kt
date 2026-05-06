package dev.perf.analysis.startup

import dev.perf.analysis.model.startup.StartupSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogcatStartupParsersTest {

    private val pkg = "com.example"

    @Test
    fun `parseDisplayed milliseconds only`() {
        val line = "I/ActivityManager: Displayed com.example/.MainActivity: +487ms"
        val p = parseDisplayedLine(line, pkg)
        assertEquals(487, p?.ms)
        assertEquals(StartupSource.LOGCAT_DISPLAYED, p?.source)
    }

    @Test
    fun `parseDisplayed seconds and milliseconds`() {
        val line = "I/ActivityManager: Displayed com.example/.MainActivity: +3s534ms"
        val p = parseDisplayedLine(line, pkg)
        assertEquals(3534, p?.ms)
    }

    @Test
    fun `parseDisplayed minutes format`() {
        val line = "I/ActivityManager: Displayed com.example/.MainActivity: +1m22s643ms"
        val p = parseDisplayedLine(line, pkg)
        assertEquals(82643, p?.ms)
    }

    @Test
    fun `parseFully drawn`() {
        val line = "I/ActivityManager: Fully drawn com.example/.MainActivity: +4s120ms"
        assertEquals(4120, parseFullyDrawnLine(line, pkg))
    }

    @Test
    fun `parseDisplayed ignores other packages`() {
        val line = "I/ActivityManager: Displayed com.other/.MainActivity: +1s"
        assertNull(parseDisplayedLine(line, pkg))
    }
}
