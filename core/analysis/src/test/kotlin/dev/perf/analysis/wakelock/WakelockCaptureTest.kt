package dev.perf.analysis.wakelock

import dev.perf.adb.AdbShellResult
import dev.perf.analysis.fake.FakeAdbClient
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WakelockCaptureTest {

    @Test
    fun `reset invokes batterystats reset`() {
        val invocations = mutableListOf<List<String>>()
        val fake =
            FakeAdbClient(
                shellHandler = { args ->
                    invocations.add(args.toList())
                    ""
                },
            )
        WakelockCapture(fake).reset("com.example.app")
        assertEquals(listOf("dumpsys", "batterystats", "--reset"), invocations.single())
    }

    @Test
    fun `capture returns null when shell fails`() = runTest {
        val fake =
            FakeAdbClient(
                allowFailureHandler = { AdbShellResult(1, "", "Permission denied") },
            )
        assertNull(WakelockCapture(fake).capture("com.example.app"))
    }

    @Test
    fun `capture parses stdout on success`() = runTest {
        val fixture =
            """
            Uid 10123: com.example.app
              Wake lock *alarm*: 1 times, 2s realtime
            """.trimIndent()
        val fake =
            FakeAdbClient(
                allowFailureHandler = { args ->
                    assertContentEquals(listOf("dumpsys", "batterystats", "com.example.app"), args.toList())
                    AdbShellResult(0, fixture, "")
                },
            )
        val r = WakelockCapture(fake).capture("com.example.app")
        assertNotNull(r)
        assertEquals(1, r.entries.size)
        assertEquals(2000, r.totalDurationMs)
    }
}
