package dev.perf.analysis.startup

import dev.perf.adb.AdbShellResult
import dev.perf.analysis.fake.FakeAdbClient
import dev.perf.analysis.model.startup.StartupSource
import dev.perf.analysis.model.startup.StartupType
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class StartupCaptureRaceTest {

    private val pkg = "com.example"

    private fun displayedLine(ttidMs: Int = 450) =
        "ActivityManager: Displayed $pkg/.MainActivity +${ttidMs}ms"

    private fun fullyDrawnLine(ms: Int = 900) =
        "ActivityManager: Fully drawn $pkg/.MainActivity +${ms}ms"

    private fun coldAm(total: Int = 500, launchState: String = "COLD") =
        """
        Starting: Intent { pkg=$pkg }
        Status: ok
        LaunchState: $launchState
        Activity: $pkg/.MainActivity
        TotalTime: $total
        WaitTime: $total
        Complete
        """.trimIndent()

    private fun shellOk(args: Array<out String>): AdbShellResult {
        if (args.size >= 2 && args[0] == "am" && args[1] == "force-stop") {
            return AdbShellResult(0, "", "")
        }
        if (args.size >= 3 && args[0] == "am" && args[1] == "start" && args[2] == "-W") {
            return AdbShellResult(0, coldAm(), "")
        }
        if (args.size >= 2 && args[0] == "input" && args[1] == "keyevent") {
            return AdbShellResult(0, "", "")
        }
        return AdbShellResult(0, "", "")
    }

    private fun baseConfig() =
        StartupCaptureConfig(
            startupTypes = setOf(StartupType.COLD),
            samplesPerType = 1,
            cooldownMs = 0L,
            streamWarmupMs = 0L,
            perSampleTimeoutMs = 15_000L,
            ttfdTailMs = 3_000L,
        )

    @Test
    fun `logcat displayed supplies ttid and fully drawn supplies ttfd`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val session =
            listOf(
                0L to displayedLine(400),
                0L to fullyDrawnLine(800),
            )
        val fake =
            FakeAdbClient(
                streamSessions = listOf(session),
                allowFailureHandler = ::shellOk,
            )
        val report =
            withContext(io) {
                StartupCapture(fake, io).capture(pkg, "$pkg/.MainActivity", baseConfig())
            }
        val cold = assertNotNull(report.cold)
        assertEquals(1, cold.samples.size)
        val s = cold.samples.single()
        assertEquals(400, s.ttidMs)
        assertEquals(800, s.ttfdMs)
        assertEquals(StartupSource.LOGCAT_DISPLAYED, s.source)
        assertTrue(report.parsedOk)
    }

    @Test
    fun `am start fallback when no displayed line before timeout`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val fake =
            FakeAdbClient(
                streamSessions = listOf(listOf(0L to "unrelated noise without package token")),
                allowFailureHandler = ::shellOk,
            )
        val report =
            withContext(io) {
                StartupCapture(fake, io).capture(pkg, "$pkg/.MainActivity", baseConfig())
            }
        val s = assertNotNull(report.cold).samples.single()
        assertEquals(500, s.ttidMs)
        assertEquals(StartupSource.AM_START_FALLBACK, s.source)
        assertContains(s.flags, "no_displayed_line")
        assertNull(s.ttfdMs)
    }

    @Test
    fun `pre_condition_drift when launch state does not match requested type`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val session = listOf(0L to displayedLine(300))
        val fake =
            FakeAdbClient(
                streamSessions = listOf(session),
                allowFailureHandler = { args ->
                    if (args.size >= 3 && args[0] == "am" && args[1] == "start" && args[2] == "-W") {
                        AdbShellResult(0, coldAm(600, launchState = "WARM"), "")
                    } else {
                        shellOk(args)
                    }
                },
            )
        val report =
            withContext(io) {
                StartupCapture(fake, io).capture(pkg, "$pkg/.MainActivity", baseConfig())
            }
        val s = assertNotNull(report.cold).samples.single()
        assertContains(s.flags, "pre_condition_drift")
    }

    @Test
    fun `multi cold samples each get their own stream session`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val s1 = listOf(0L to displayedLine(100))
        val s2 = listOf(0L to displayedLine(200))
        val fake =
            FakeAdbClient(
                streamSessions = listOf(s1, s2),
                allowFailureHandler = ::shellOk,
            )
        val cfg = baseConfig().copy(samplesPerType = 2)
        val report =
            withContext(io) {
                StartupCapture(fake, io).capture(pkg, "$pkg/.MainActivity", cfg)
            }
        val cold = assertNotNull(report.cold)
        assertEquals(2, cold.samples.size)
        assertEquals(listOf(100, 200), cold.samples.map { it.ttidMs })
    }

    @Test
    fun `parsedOk false when more than half of samples use logcat fallback`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val noise = listOf(0L to "noise")
        val fake =
            FakeAdbClient(
                streamSessions = listOf(noise, noise, listOf(0L to displayedLine(50))),
                allowFailureHandler = ::shellOk,
            )
        val cfg = baseConfig().copy(samplesPerType = 3)
        val report =
            withContext(io) {
                StartupCapture(fake, io).capture(pkg, "$pkg/.MainActivity", cfg)
            }
        assertFalse(report.parsedOk)
    }

    @Test
    fun `displayed only with quiet logcat completes without hang`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val fake =
            FakeAdbClient(
                streamSessions = listOf(listOf(0L to displayedLine(400))),
                holdStreamOpenAfterScript = true,
                allowFailureHandler = ::shellOk,
            )
        val deferred =
            async(io) {
                StartupCapture(fake, io).capture(pkg, "$pkg/.MainActivity", baseConfig())
            }
        advanceTimeBy(15_000)
        val report = deferred.await()
        val s = assertNotNull(report.cold).samples.single()
        assertEquals(400, s.ttidMs)
        assertNull(s.ttfdMs)
        assertEquals(StartupSource.LOGCAT_DISPLAYED, s.source)
    }

    @Test
    fun `hold open stream teardown completes within bounded virtual time`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val fake =
            FakeAdbClient(
                streamSessions = listOf(listOf(0L to displayedLine(300))),
                holdStreamOpenAfterScript = true,
                allowFailureHandler = ::shellOk,
            )
        val deferred =
            async(io) {
                StartupCapture(fake, io).capture(pkg, "$pkg/.MainActivity", baseConfig())
            }
        advanceTimeBy(20_000)
        deferred.await()
    }

    @Test
    fun `displayed line after virtual delay still resolves ttid`() = runTest {
        val io = StandardTestDispatcher(testScheduler)
        val session =
            listOf(
                40L to "warming",
                0L to displayedLine(222),
            )
        val fake =
            FakeAdbClient(
                streamSessions = listOf(session),
                allowFailureHandler = ::shellOk,
            )
        val deferred =
            async(io) {
                StartupCapture(fake, io).capture(pkg, "$pkg/.MainActivity", baseConfig())
            }
        advanceTimeBy(50)
        val report = deferred.await()
        assertEquals(222, assertNotNull(report.cold).samples.single().ttidMs)
    }
}
