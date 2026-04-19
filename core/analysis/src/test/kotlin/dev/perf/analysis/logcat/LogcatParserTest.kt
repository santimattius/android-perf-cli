package dev.perf.analysis.logcat

import dev.perf.analysis.fake.FakeAdbClient
import dev.perf.analysis.model.StrictModeViolation.ViolationType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * Behavioral tests for [LogcatParser].
 *
 * Each test provides fixture logcat lines through [FakeAdbClient.streamLines]
 * and asserts on the domain objects emitted by the Flow — not on regex
 * internals or intermediate state.
 *
 * Fixture format (logcat -v threadtime):
 *   MM-DD HH:MM:SS.mmm  PID  TID PRIORITY TAG: message
 *   <blank line separates violations>
 */
class LogcatParserTest {

    private val packageName = "com.example.app"
    private val pid = "1234"

    // ── DiskRead ──────────────────────────────────────────────────────────────

    @Test
    fun `parses DiskRead violation type correctly`() = runTest {
        val violations = parserWith(diskReadFixture()).violations(packageName).toList()

        assertEquals(1, violations.size)
        assertEquals(ViolationType.DISK_READ, violations.first().violationType)
    }

    @Test
    fun `extracts violation message without penalty clause`() = runTest {
        val violations = parserWith(diskReadFixture()).violations(packageName).toList()

        assertEquals("StrictModeDiskReadViolation", violations.first().message)
    }

    @Test
    fun `captures stack trace lines associated with violation`() = runTest {
        val violations = parserWith(diskReadFixture()).violations(packageName).toList()

        val stack = violations.first().stackTrace
        assertContains(stack, "ActivityThread.performLaunchActivity")
        assertContains(stack, "MainActivity.onCreate")
    }

    // ── Network ───────────────────────────────────────────────────────────────

    @Test
    fun `parses Network violation type correctly`() = runTest {
        val violations = parserWith(networkFixture()).violations(packageName).toList()

        assertEquals(1, violations.size)
        assertEquals(ViolationType.NETWORK, violations.first().violationType)
    }

    @Test
    fun `parses DiskWrite violation type correctly`() = runTest {
        val violations = parserWith(diskWriteFixture()).violations(packageName).toList()

        assertEquals(1, violations.size)
        assertEquals(ViolationType.DISK_WRITE, violations.first().violationType)
    }

    // ── Multiple violations ───────────────────────────────────────────────────

    @Test
    fun `emits all violations when multiple appear in stream`() = runTest {
        val lines = diskReadFixture() + networkFixture()
        val violations = parserWith(lines).violations(packageName).toList()

        assertEquals(2, violations.size)
        assertEquals(ViolationType.DISK_READ, violations[0].violationType)
        assertEquals(ViolationType.NETWORK, violations[1].violationType)
    }

    @Test
    fun `emits nothing when stream contains no StrictMode lines`() = runTest {
        val irrelevantLines = listOf(
            "01-15 10:00:00.000  1234  5678 D ActivityManager: Start proc com.example.app",
            "01-15 10:00:00.050  1234  5678 D SomeTag: unrelated message",
            "",
        )
        val violations = parserWith(irrelevantLines).violations(packageName).toList()

        assertTrue(violations.isEmpty())
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test
    fun `flushes last violation even without trailing blank line`() = runTest {
        // No blank line at the end — close() in LogcatParser must flush the buffer
        val lines = listOf(
            "01-15 10:23:45.123  1234  5678 D StrictMode: StrictModeDiskReadViolation penalty=death",
            "\tat android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3792)",
            // deliberately no trailing blank line
        )
        val violations = parserWith(lines).violations(packageName).toList()

        assertEquals(1, violations.size)
    }

    @Test
    fun `maps unknown violation type to UNKNOWN`() = runTest {
        val lines = listOf(
            "01-15 10:23:45.123  1234  5678 D StrictMode: StrictModeCustomViolation penalty=log",
            "",
        )
        val violations = parserWith(lines).violations(packageName).toList()

        assertEquals(ViolationType.UNKNOWN, violations.first().violationType)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun parserWith(lines: List<String>): LogcatParser {
        val fake = FakeAdbClient(
            shellHandler = { args ->
                when {
                    args.contains("pidof") -> pid
                    args.contains("-c") -> ""   // logcat -c (clear buffer)
                    else -> ""
                }
            },
            streamLines = lines,
        )
        return LogcatParser(fake)
    }

    private fun diskReadFixture() = listOf(
        "01-15 10:23:45.123  1234  5678 D StrictMode: StrictModeDiskReadViolation penalty=death",
        "\tat android.app.ActivityThread.performLaunchActivity(ActivityThread.java:3792)",
        "\tat com.example.app.MainActivity.onCreate(MainActivity.kt:42)",
        "",
    )

    private fun networkFixture() = listOf(
        "01-15 10:23:46.456  1234  5678 D StrictMode: StrictModeNetworkViolation penalty=log",
        "\tat com.example.app.network.ApiClient.fetchData(ApiClient.kt:88)",
        "",
    )

    private fun diskWriteFixture() = listOf(
        "01-15 10:23:47.789  1234  5678 D StrictMode: StrictModeDiskWriteViolation penalty=death",
        "\tat com.example.app.db.UserDao.insert(UserDao.kt:22)",
        "",
    )
}
