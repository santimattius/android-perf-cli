package dev.perf.trace

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TraceProcessorResolverTest {

    @Test
    fun installHint_mentionsManagedPath() {
        val hint = TraceProcessorResolver.installHint()
        assertTrue(hint.contains(".perf-cli"))
        assertTrue(hint.contains("trace_processor"))
    }

    @Test
    fun managedInstallPath_isUnderHome() {
        val path = TraceProcessorResolver.managedInstallPath()
        assertTrue(path.absolutePath.contains(TraceProcessorResolver.DEFAULT_INSTALL_DIR))
    }
}
