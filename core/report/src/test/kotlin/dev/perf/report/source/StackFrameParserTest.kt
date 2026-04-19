package dev.perf.report.source

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class StackFrameParserTest {

    // ── Happy path ────────────────────────────────────────────────────────────

    @Test
    fun `parses standard Kotlin frame`() {
        val frame = StackFrameParser.parse(
            "\tat com.example.app.MainActivity.onCreate(MainActivity.kt:42)"
        )!!

        assertEquals("com.example.app.MainActivity", frame.className)
        assertEquals("onCreate", frame.method)
        assertEquals("MainActivity.kt", frame.file)
        assertEquals(42, frame.line)
    }

    @Test
    fun `parses Java frame`() {
        val frame = StackFrameParser.parse(
            "\tat android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3792)"
        )!!

        assertEquals("android.app.ActivityThread", frame.className)
        assertEquals("performLaunchActivity", frame.method)
        assertEquals("ActivityThread.java", frame.file)
        assertEquals(3792, frame.line)
    }

    @Test
    fun `parses inner class frame stripping dollar sign`() {
        val frame = StackFrameParser.parse(
            "\tat com.example.Outer\$Inner.click(Outer.kt:10)"
        )!!

        assertEquals("com.example.Outer\$Inner", frame.className)
        assertEquals("click", frame.method)
    }

    @Test
    fun `preserves raw line in frame`() {
        val raw = "\tat com.example.Foo.bar(Foo.kt:1)"
        val frame = StackFrameParser.parse(raw)!!
        assertEquals(raw, frame.raw)
    }

    // ── Non-frame lines ───────────────────────────────────────────────────────

    @Test
    fun `returns null for blank line`() {
        assertNull(StackFrameParser.parse(""))
    }

    @Test
    fun `returns null for ellipsis continuation line`() {
        assertNull(StackFrameParser.parse("\t... 12 more"))
    }

    @Test
    fun `returns null for exception message line`() {
        assertNull(StackFrameParser.parse("java.lang.RuntimeException: something went wrong"))
    }

    @Test
    fun `returns null for logcat header line`() {
        assertNull(
            StackFrameParser.parse(
                "01-15 10:23:45.123  1234  5678 D StrictMode: StrictModeDiskReadViolation"
            )
        )
    }
}
