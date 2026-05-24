package dev.perf.trace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class QueryPackLoaderTest {

    @Test
    fun loadV1_includesCoreDomains() {
        val manifest = QueryPackLoader.loadV1()
        assertEquals("v1", manifest.version)
        assertTrue(manifest.domains.keys.containsAll(listOf("startup", "rendering", "memory", "cpu")))
    }

    @Test
    fun loadSqlResource_rendering_nonEmpty() {
        val sql = QueryPackLoader.loadSqlResource("rendering.sql")
        assertTrue(sql.contains("main_thread_blocked_ms"))
    }
}
