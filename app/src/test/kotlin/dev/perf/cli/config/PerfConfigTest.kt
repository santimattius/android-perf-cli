package dev.perf.cli.config

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PerfConfigTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `androidDocs defaults to true when field absent from JSON`() {
        // Scenario F: legacy config.json without androidDocs key.
        val cfg = json.decodeFromString<PerfConfig>("""{"androidSdkPath":"/sdk"}""")
        assertTrue(cfg.androidDocs)
    }

    @Test
    fun `androidDocs is false when explicitly set to false in JSON`() {
        // Scenario G: explicit opt-out.
        val cfg = json.decodeFromString<PerfConfig>(
            """{"androidSdkPath":"/sdk","androidDocs":false}"""
        )
        assertFalse(cfg.androidDocs)
    }
}
