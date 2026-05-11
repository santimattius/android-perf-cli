package dev.perf.cli.history

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * T-2.1 RED: Serialization round-trip tests for SessionHistoryEntry.
 *
 * Uses explicit SessionHistoryEntry.serializer() for GraalVM AOT-safety.
 * No typeOf<>, no reified, no KClass reflection.
 */
class SessionHistoryEntrySerializationTest {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    // Case 1: Round-trip all fields present
    @Test
    fun `round-trip all fields present`() {
        val entry = SessionHistoryEntry(
            packageName = "com.example.app",
            runId = "2026-05-10T12:00:00Z-abc123",
            timestampIso = "2026-05-10T12:00:00Z",
            anrCount = 2,
            crashCount = 1,
            totalFrames = 500,
            slowFrames = 50,
            frozenFrames = 5,
            exitSignalsEnabled = true,
            scenarioLabel = "login_flow",
            coldStartP50Ms = 800,
        )

        val encoded = json.encodeToString(SessionHistoryEntry.serializer(), entry)
        val decoded = json.decodeFromString(SessionHistoryEntry.serializer(), encoded)

        assertEquals(entry, decoded)
    }

    // Case 2: Round-trip nullable fields absent
    @Test
    fun `round-trip nullable fields absent deserializes to null`() {
        val jsonWithoutNullables = """
            {
              "packageName": "com.example.app",
              "runId": "2026-05-10T12:00:00Z-def456",
              "timestampIso": "2026-05-10T12:00:00Z",
              "anrCount": 0,
              "crashCount": 0,
              "totalFrames": 200,
              "slowFrames": 10,
              "frozenFrames": 0,
              "exitSignalsEnabled": false
            }
        """.trimIndent()

        val decoded = json.decodeFromString(SessionHistoryEntry.serializer(), jsonWithoutNullables)

        assertNull(decoded.scenarioLabel)
        assertNull(decoded.coldStartP50Ms)
        assertEquals("com.example.app", decoded.packageName)
    }

    // Case 3: Forward-compat — unknown field silently ignored
    @Test
    fun `forward-compat json with extra unknown field deserializes without exception`() {
        val jsonWithExtraField = """
            {
              "packageName": "com.example.app",
              "runId": "2026-05-10T12:00:00Z-ghi789",
              "timestampIso": "2026-05-10T12:00:00Z",
              "anrCount": 0,
              "crashCount": 0,
              "totalFrames": 300,
              "slowFrames": 0,
              "frozenFrames": 0,
              "exitSignalsEnabled": true,
              "futureMetric": 42
            }
        """.trimIndent()

        val decoded = json.decodeFromString(SessionHistoryEntry.serializer(), jsonWithExtraField)

        assertEquals("com.example.app", decoded.packageName)
        assertEquals(300, decoded.totalFrames)
    }
}
