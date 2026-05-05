package dev.perf.cli

import dev.perf.cli.gradle.GradleBuildRunner
import kotlin.test.Test
import kotlin.test.assertEquals

class GradleBuildRunnerTest {

    @Test
    fun `assembleTask builds standard debug task`() {
        assertEquals(":app:assembleDebug", GradleBuildRunner.assembleTask(":app", "debug"))
    }

    @Test
    fun `assembleTask trims module colon`() {
        assertEquals(":features:demo:assembleRelease", GradleBuildRunner.assembleTask("features:demo", "release"))
    }
}
