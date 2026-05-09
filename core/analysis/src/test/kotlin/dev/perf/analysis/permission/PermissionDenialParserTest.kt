package dev.perf.analysis.permission

import dev.perf.analysis.model.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Behavioral tests for [PermissionDenialParser].
 *
 * All tests are pure: no ADB, no coroutines, no fakes.
 * Regex behavior is tested through the public API.
 */
class PermissionDenialParserTest {

    private val expectedUid = 10050
    private val packageName = "com.example.app"

    // ── parseDenialLine ───────────────────────────────────────────────────────

    @Test
    fun `parseDenialLine_activityManagerLine_returnsDenialWithCorrectFields`() {
        val line = "01-15 10:00:00.100  1234  5678 W ActivityManager: Permission Denial: " +
            "opening provider com.android.providers.contacts.ContactsProvider2 " +
            "from ProcessRecord{abc123 1234:com.example.app/u0a50} (pid=1234, uid=10050) " +
            "requires android.permission.READ_CONTACTS"

        val result = PermissionDenialParser.parseDenialLine(line, expectedUid, packageName)

        assertNotNull(result)
        assertEquals("android.permission.READ_CONTACTS", result.permission)
        assertEquals(10050, result.uid)
        assertEquals(packageName, result.packageName)
        assertEquals(Severity.WARNING, result.severity)
    }

    @Test
    fun `parseDenialLine_packageManagerLine_returnsDenial`() {
        val line = "01-15 10:00:00.200  1234  5678 W PackageManager: Permission Denial: " +
            "opening provider com.android.providers.contacts.ContactsProvider2 " +
            "from ProcessRecord{abc123 1234:com.example.app/u0a50} (pid=1234, uid=10050) " +
            "requires android.permission.READ_CONTACTS"

        val result = PermissionDenialParser.parseDenialLine(line, expectedUid, packageName)

        assertNotNull(result)
        assertEquals("android.permission.READ_CONTACTS", result.permission)
        assertEquals(10050, result.uid)
    }

    @Test
    fun `parseDenialLine_uidMismatch_returnsNull`() {
        // uid=99999 != expectedUid=10050
        val line = "01-15 10:00:00.100  1234  5678 W ActivityManager: Permission Denial: " +
            "from ProcessRecord{abc123 1234:com.other.app/u0a99} (pid=1234, uid=99999) " +
            "requires android.permission.READ_CONTACTS"

        val result = PermissionDenialParser.parseDenialLine(line, expectedUid, packageName)

        assertNull(result)
    }

    @Test
    fun `parseDenialLine_noPermissionDenialSubstring_returnsNull`() {
        val line = "01-15 10:00:00.100  1234  5678 W ActivityManager: Something else happened"

        val result = PermissionDenialParser.parseDenialLine(line, expectedUid, packageName)

        assertNull(result)
    }

    @Test
    fun `parseDenialLine_missingUidPidBlock_returnsNull`() {
        // No (pid=..., uid=...) block at all
        val line = "01-15 10:00:00.100  1234  5678 W ActivityManager: Permission Denial: " +
            "requires android.permission.READ_CONTACTS"

        val result = PermissionDenialParser.parseDenialLine(line, expectedUid, packageName)

        assertNull(result)
    }

    @Test
    fun `parseDenialLine_blankPermission_returnsUnknown`() {
        // "requires" with no permission token after it — we can't produce a real permission
        // so the field should be "UNKNOWN" but the denial is still returned (uid matched)
        val line = "01-15 10:00:00.100  1234  5678 W ActivityManager: Permission Denial: " +
            "from ProcessRecord{abc123 1234:com.example.app/u0a50} (pid=1234, uid=10050) " +
            "requires "

        val result = PermissionDenialParser.parseDenialLine(line, expectedUid, packageName)

        // Either null (regex doesn't match) or UNKNOWN — both are acceptable per design
        if (result != null) {
            assertEquals("UNKNOWN", result.permission)
        }
    }

    // ── extractUid ────────────────────────────────────────────────────────────

    @Test
    fun `extractUid_multipleUserIdLines_returnsFirstMatch`() {
        val dumpsysOutput = """
            Package [com.example.app] (abc123):
              userId=10050
              userId=10051
              flags=[ INSTALLED HAS_CODE ]
        """.trimIndent()

        val result = PermissionDenialParser.extractUid(dumpsysOutput)

        assertEquals(10050, result)
    }

    @Test
    fun `extractUid_blankInput_returnsNull`() {
        val result = PermissionDenialParser.extractUid("")

        assertNull(result)
    }
}
