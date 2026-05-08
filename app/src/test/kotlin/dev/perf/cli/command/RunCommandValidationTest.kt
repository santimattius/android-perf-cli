package dev.perf.cli.command

import com.github.ajalt.clikt.core.CliktError
import dev.perf.analysis.MonitorConfig
import dev.perf.cli.scenario.EngineConfig
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RunCommandValidationTest {

    // T-6.1 test (a): stuckWakelocks=true + wakelocks=false → CliktError
    @Test
    fun `validateEngineConfig throws CliktError when stuckWakelocks enabled without wakelocks`() {
        val config = EngineConfig(wakelocks = false, stuckWakelocks = true)
        val exception = assertFailsWith<CliktError> {
            validateEngineConfig(config)
        }
        assertTrue(
            exception.message?.contains("stuckWakelocks requires wakelocks") == true,
            "Error message should mention 'stuckWakelocks requires wakelocks', was: ${exception.message}",
        )
    }

    // T-6.1 test (b): stuckWakelocks=true + wakelocks=true → no exception
    @Test
    fun `validateEngineConfig passes when stuckWakelocks enabled with wakelocks`() {
        val config = EngineConfig(wakelocks = true, stuckWakelocks = true)
        // Must not throw
        validateEngineConfig(config)
    }

    @Test
    fun `validateEngineConfig passes when stuckWakelocks disabled regardless of wakelocks`() {
        validateEngineConfig(EngineConfig(wakelocks = false, stuckWakelocks = false))
        validateEngineConfig(EngineConfig(wakelocks = true, stuckWakelocks = false))
    }

    // T-6.2: All three background flags propagate from EngineConfig to MonitorConfig
    @Test
    fun `buildMonitorConfig propagates all three background signal flags`() {
        val engines = EngineConfig(
            backgroundNetwork = true,
            backgroundWifi = true,
            stuckWakelocks = true,
            wakelocks = true,
        )
        val monitor = buildMonitorConfig(engines, mergedWakelocks = true, mergedExitSignals = false, mergedStartup = false)
        assertTrue(monitor.backgroundNetwork, "backgroundNetwork must propagate")
        assertTrue(monitor.backgroundWifi, "backgroundWifi must propagate")
        assertTrue(monitor.stuckWakelocks, "stuckWakelocks must propagate")
    }

    @Test
    fun `buildMonitorConfig with all three flags false leaves them false in MonitorConfig`() {
        val engines = EngineConfig(
            backgroundNetwork = false,
            backgroundWifi = false,
            stuckWakelocks = false,
        )
        val monitor = buildMonitorConfig(engines, mergedWakelocks = false, mergedExitSignals = false, mergedStartup = false)
        assertTrue(!monitor.backgroundNetwork)
        assertTrue(!monitor.backgroundWifi)
        assertTrue(!monitor.stuckWakelocks)
    }
}
