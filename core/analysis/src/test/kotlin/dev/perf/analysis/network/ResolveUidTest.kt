package dev.perf.analysis.network

import dev.perf.adb.AdbShellResult
import dev.perf.analysis.fake.FakeAdbClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ResolveUidTest {

    // Canonical fixture strings for pm list packages -U
    object Fixtures {
        const val PKG = "com.example.app"
        const val UID = 10234

        val PM_LIST_SUCCESS = "package:com.example.app uid:10234\n"
        val PM_LIST_NO_MATCH = "package:com.other.app uid:10100\n"
        val PM_LIST_EMPTY = ""
    }

    @Test
    fun `resolveUid returns correct uid on happy path`() {
        val fake = FakeAdbClient(
            allowFailureHandler = { args ->
                if (args.toList() == listOf("pm", "list", "packages", "-U", Fixtures.PKG)) {
                    AdbShellResult(0, Fixtures.PM_LIST_SUCCESS, "")
                } else {
                    AdbShellResult(1, "", "unexpected command")
                }
            },
        )
        val capture = BackgroundNetworkCapture(fake)
        assertEquals(Fixtures.UID, capture.resolveUid(Fixtures.PKG))
    }

    @Test
    fun `resolveUid returns null when output contains no matching line`() {
        val fake = FakeAdbClient(
            allowFailureHandler = { AdbShellResult(0, Fixtures.PM_LIST_NO_MATCH, "") },
        )
        val capture = BackgroundNetworkCapture(fake)
        assertNull(capture.resolveUid(Fixtures.PKG))
    }

    @Test
    fun `resolveUid returns null when exit code is non-zero`() {
        val fake = FakeAdbClient(
            allowFailureHandler = { AdbShellResult(1, Fixtures.PM_LIST_SUCCESS, "Permission denied") },
        )
        val capture = BackgroundNetworkCapture(fake)
        assertNull(capture.resolveUid(Fixtures.PKG))
    }

    @Test
    fun `resolveUid returns null when output is empty`() {
        val fake = FakeAdbClient(
            allowFailureHandler = { AdbShellResult(0, Fixtures.PM_LIST_EMPTY, "") },
        )
        val capture = BackgroundNetworkCapture(fake)
        assertNull(capture.resolveUid(Fixtures.PKG))
    }
}
