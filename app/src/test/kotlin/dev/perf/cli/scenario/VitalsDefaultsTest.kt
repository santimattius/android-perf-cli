package dev.perf.cli.scenario

import kotlin.test.Test
import kotlin.test.assertEquals

class VitalsDefaultsTest {

    @Test
    fun `COLD_START_MS is 5000`() {
        assertEquals(5000L, VitalsDefaults.COLD_START_MS)
    }

    @Test
    fun `WARM_START_MS is 2000`() {
        assertEquals(2000L, VitalsDefaults.WARM_START_MS)
    }

    @Test
    fun `HOT_START_MS is 1500`() {
        assertEquals(1500L, VitalsDefaults.HOT_START_MS)
    }

    @Test
    fun `WAKELOCK_SESSION_PERCENT is 5_0`() {
        assertEquals(5.0, VitalsDefaults.WAKELOCK_SESSION_PERCENT)
    }

    @Test
    fun `WAKELOCK_LONG_HELD_MS is 3_600_000`() {
        assertEquals(3_600_000L, VitalsDefaults.WAKELOCK_LONG_HELD_MS)
    }
}
