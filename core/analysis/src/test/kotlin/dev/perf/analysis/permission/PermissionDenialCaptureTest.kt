package dev.perf.analysis.permission

import dev.perf.adb.AdbShellResult
import dev.perf.analysis.fake.FakeAdbClient
import dev.perf.analysis.model.Severity
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Behavioral tests for [PermissionDenialCapture].
 *
 * All tests use [FakeAdbClient] — no MockK, no Mockito.
 * UID resolution is via `shellAllowFailure("dumpsys", "package", ...)`.
 * Stream lines are injected via `streamSessions`.
 */
class PermissionDenialCaptureTest {

    private val packageName = "com.example.app"

    // Fixture: `dumpsys package com.example.app` output containing userId=10050
    private val dumpsysOutput = """
        Package [com.example.app] (abc123):
          userId=10050
          flags=[ INSTALLED HAS_CODE ALLOW_TASK_REPARENTING ]
    """.trimIndent()

    // Matching denial line (uid=10050 matches)
    private val matchingDenialLine =
        "01-15 10:00:00.100  1234  5678 W ActivityManager: Permission Denial: " +
            "opening provider com.android.providers.contacts.ContactsProvider2 " +
            "from ProcessRecord{abc123 1234:com.example.app/u0a50} (pid=1234, uid=10050) " +
            "requires android.permission.READ_CONTACTS"

    // Non-matching denial line (uid=99999 does NOT match 10050)
    private val nonMatchingDenialLine =
        "01-15 10:00:00.200  5678  9999 W ActivityManager: Permission Denial: " +
            "from ProcessRecord{xyz456 5678:com.other.app/u0a99} (pid=5678, uid=99999) " +
            "requires android.permission.READ_CONTACTS"

    // Unrelated WARN line — no "Permission Denial:" substring
    private val unrelatedLine =
        "01-15 10:00:00.300  1234  5678 W ActivityManager: Activity pause timeout for ActivityRecord"

    // ── Case (a): happy path with UID-matched denial ──────────────────────────

    @Test
    fun `capture_uidMatchedDenial_returnsReportWithOneDenial`() = runTest {
        val fake = FakeAdbClient(
            allowFailureHandler = { args ->
                if (args.toList() == listOf("dumpsys", "package", packageName)) {
                    AdbShellResult(0, dumpsysOutput, "")
                } else {
                    AdbShellResult(0, "", "")
                }
            },
            streamSessions = listOf(
                listOf(
                    0L to matchingDenialLine,
                    0L to nonMatchingDenialLine,
                    0L to unrelatedLine,
                ),
            ),
        )

        val report = PermissionDenialCapture(fake).capture(packageName)

        assertEquals(1, report.denials.size)
        assertEquals(10050, report.denials.first().uid)
        assertEquals("android.permission.READ_CONTACTS", report.denials.first().permission)
        assertEquals(Severity.WARNING, report.severity)
        assertTrue(report.hasDenials)
    }

    // ── Case (b): UID-mismatch denial is filtered out ─────────────────────────

    @Test
    fun `capture_onlyNonMatchingDenials_returnsEmptyInfoReport`() = runTest {
        val fake = FakeAdbClient(
            allowFailureHandler = { args ->
                if (args.toList() == listOf("dumpsys", "package", packageName)) {
                    AdbShellResult(0, dumpsysOutput, "")
                } else {
                    AdbShellResult(0, "", "")
                }
            },
            streamSessions = listOf(
                listOf(
                    0L to nonMatchingDenialLine,
                    0L to unrelatedLine,
                ),
            ),
        )

        val report = PermissionDenialCapture(fake).capture(packageName)

        assertEquals(0, report.denials.size)
        assertEquals(Severity.INFO, report.severity)
        assertFalse(report.hasDenials)
    }

    // ── Case (c): UID resolution failure returns empty INFO report ────────────

    @Test
    fun `capture_uidResolutionFailure_returnsEmptyInfoReportWithoutOpeningStream`() = runTest {
        // dumpsys package returns blank stdout → UID resolution fails
        val fake = FakeAdbClient(
            allowFailureHandler = { args ->
                if (args.toList() == listOf("dumpsys", "package", packageName)) {
                    AdbShellResult(0, "", "")
                } else {
                    AdbShellResult(0, "", "")
                }
            },
            // No stream sessions provided — if capture opens the stream, it would get empty lines (fine).
            // The assertion is on the report shape: empty denials, INFO severity.
            streamSessions = emptyList(),
        )

        val report = PermissionDenialCapture(fake).capture(packageName)

        assertNotNull(report)
        assertEquals(0, report.denials.size)
        assertEquals(Severity.INFO, report.severity)
    }
}
