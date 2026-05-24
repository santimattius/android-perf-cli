package dev.perf.analysis.logcat

import dev.perf.analysis.fake.FakeAdbClient
import dev.perf.analysis.model.PotentialAnrHintType
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Streaming tests for [LogcatParser] ANR stack-trace buffering.
 *
 * These tests exercise the state machine that captures `\tat` continuation
 * lines following an `INPUT_DISPATCH_TIMEOUT_RISK` trigger and attaches them
 * as [PotentialAnrHint.stackTrace]. Each test feeds a scripted list of logcat
 * lines via [FakeAdbClient] and asserts on the emitted [SessionLogcatEvent.AnrHint].
 */
class LogcatParserAnrBufferingTest {

    private val packageName = "com.example.app"
    private val pid = "1234"

    // ── Task 2.1: Trigger + 2 frames → non-empty stackTrace ──────────────────

    @Test
    fun `ANR trigger followed by two tat lines produces non-empty stackTrace`() = runTest {
        val lines = listOf(
            "Input dispatching timed out (5008ms): com.example.app ...",
            "\tat com.example.Main.handleInput(Main.kt:42)",
            "\tat com.example.Main.run(Main.kt:10)",
            "",
        )
        val hints = anrHintsFrom(lines)

        assertEquals(1, hints.size)
        val trace = hints.first().stackTrace
        assertTrue(trace.isNotEmpty(), "Expected non-empty stackTrace")
        assertContains(trace, "\tat com.example.Main.handleInput(Main.kt:42)")
        assertContains(trace, "\tat com.example.Main.run(Main.kt:10)")
    }

    // ── Task 2.2: Buffering stops at blank line ───────────────────────────────

    @Test
    fun `buffering stops at blank line — only pre-blank frames captured`() = runTest {
        val lines = listOf(
            "Input dispatching timed out (5008ms): com.example.app ...",
            "\tat com.example.Main.handleInput(Main.kt:42)",
            "",
            "\tat com.example.Other.method(Other.kt:99)",
        )
        val hints = anrHintsFrom(lines)

        assertEquals(1, hints.size)
        val trace = hints.first().stackTrace
        assertContains(trace, "\tat com.example.Main.handleInput(Main.kt:42)")
        assertTrue(
            !trace.contains("Other.kt"),
            "Frames after blank line must NOT be included in stackTrace",
        )
    }

    // ── Task 2.3: Trigger with no following frames → empty stackTrace ─────────

    @Test
    fun `ANR trigger with no following tat lines produces empty stackTrace`() = runTest {
        val lines = listOf(
            "Input dispatching timed out (5008ms): com.example.app ...",
            "Some unrelated log line",
            "",
        )
        val hints = anrHintsFrom(lines)

        assertEquals(1, hints.size)
        assertEquals("", hints.first().stackTrace)
    }

    // ── Task 2.4: Other hint types do not buffer ──────────────────────────────

    @Test
    fun `CHOREOGRAPHER_SKIPPED_FRAMES followed by tat lines — stackTrace is empty`() = runTest {
        val lines = listOf(
            "01-15 10:00:00.100  1234  5678 I Choreographer: Skipped 42 frames!  The application may be doing too much work on its main thread.",
            "\tat com.example.Main.doSomething(Main.kt:20)",
            "\tat com.example.Main.run(Main.kt:5)",
            "",
        )
        val hints = anrHintsFrom(lines)

        assertEquals(1, hints.size)
        assertEquals(
            PotentialAnrHintType.CHOREOGRAPHER_SKIPPED_FRAMES,
            hints.first().hintType,
        )
        assertEquals("", hints.first().stackTrace)
    }

    // ── Task 2.5: Interleaved StrictMode then ANR — independent stacks ────────

    @Test
    fun `interleaved StrictMode then ANR — ANR hint has only its own frames`() = runTest {
        val lines = listOf(
            // StrictMode trigger with its own frames
            "01-15 10:23:45.123  1234  5678 D StrictMode: StrictModeDiskReadViolation penalty=death",
            "\tat android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3792)",
            "",
            // ANR trigger with its own frames
            "Input dispatching timed out (5008ms): com.example.app ...",
            "\tat com.example.Main.handleInput(Main.kt:42)",
            "",
        )
        val hints = anrHintsFrom(lines)

        assertEquals(1, hints.size)
        val anrTrace = hints.first().stackTrace
        assertContains(anrTrace, "\tat com.example.Main.handleInput(Main.kt:42)")
        assertTrue(
            !anrTrace.contains("ActivityThread"),
            "StrictMode frames must NOT bleed into the ANR stackTrace",
        )
    }

    // ── Task 2.6: Frame cap at 64 + sentinel ──────────────────────────────────

    @Test
    fun `70 tat lines — first 64 captured plus sentinel line`() = runTest {
        val frameLines = (1..70).map { "\tat com.example.Frame.method$it(Frame.kt:$it)" }
        val lines = listOf("Input dispatching timed out (5008ms): com.example.app ...") +
            frameLines + listOf("")

        val hints = anrHintsFrom(lines)

        assertEquals(1, hints.size)
        val trace = hints.first().stackTrace
        val traceLines = trace.lines().filter { it.isNotBlank() }

        // 64 real frames + 1 sentinel = 65 non-blank lines
        assertEquals(
            65,
            traceLines.size,
            "Expected 64 frames + 1 sentinel. Got ${traceLines.size} lines.\nTrace:\n$trace",
        )
        assertTrue(
            trace.contains("more frames omitted"),
            "Expected sentinel line mentioning 'more frames omitted'",
        )
        // The sentinel should mention exactly 6 omitted (70 - 64 = 6)
        assertTrue(
            trace.contains("6 more frames omitted"),
            "Sentinel must say exactly '6 more frames omitted'. Trace:\n$trace",
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parserWith(lines: List<String>): LogcatParser {
        val fake = FakeAdbClient(
            shellHandler = { args ->
                when {
                    args.contains("pidof") -> pid
                    args.contains("-c") -> ""
                    else -> ""
                }
            },
            streamLines = lines,
        )
        return LogcatParser(fake)
    }

    private suspend fun anrHintsFrom(lines: List<String>) =
        parserWith(lines)
            .sessionEvents(packageName)
            .filterIsInstance<SessionLogcatEvent.AnrHint>()
            .map { it.hint }
            .toList()
}
