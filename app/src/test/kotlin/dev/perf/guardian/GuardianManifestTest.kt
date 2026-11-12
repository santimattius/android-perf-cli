package dev.perf.guardian

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression guard for [GuardianManifest] fields that are deserialized but read by no
 * production code yet: [GuardianManifest.verifiedAgainst] and
 * [DocSnippetEntry.markerBegin]/[DocSnippetEntry.markerEnd] (PR1 review finding #7).
 *
 * PR2+ will add the consumers (citation reporting, `AppendBlock` marker-delimited merges
 * into files like `AGENTS.md`) — until then, this proves the fields round-trip correctly
 * through JSON so a future consumer can rely on them without re-verifying the schema.
 */
class GuardianManifestTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `verifiedAgainst round-trips spec, source, and date through JSON`() {
        val text = """
            {
              "guardianVersion": "v1",
              "verifiedAgainst": [
                {
                  "spec": "Android ANR guidance",
                  "source": "https://developer.android.com/topic/performance/vitals/anr",
                  "date": "2026-08-01"
                }
              ],
              "skills": [],
              "personas": [],
              "docSnippets": []
            }
        """.trimIndent()

        val manifest = json.decodeFromString(GuardianManifest.serializer(), text)

        assertEquals(1, manifest.verifiedAgainst.size, "verifiedAgainst must deserialize its single entry")
        val entry = manifest.verifiedAgainst.first()
        assertEquals("Android ANR guidance", entry.spec)
        assertEquals("https://developer.android.com/topic/performance/vitals/anr", entry.source)
        assertEquals("2026-08-01", entry.date)
    }

    @Test
    fun `docSnippet markerBegin and markerEnd round-trip through JSON`() {
        val text = """
            {
              "guardianVersion": "v1",
              "verifiedAgainst": [],
              "skills": [],
              "personas": [],
              "docSnippets": [
                {
                  "id": "codex-agents-md",
                  "target": "codex",
                  "version": "1.0.0",
                  "path": "AGENTS.md",
                  "resource": "snippets/codex-agents-md.md",
                  "markerBegin": "<!-- guardian:begin codex-agents-md -->",
                  "markerEnd": "<!-- guardian:end codex-agents-md -->"
                }
              ]
            }
        """.trimIndent()

        val manifest = json.decodeFromString(GuardianManifest.serializer(), text)

        assertEquals(1, manifest.docSnippets.size, "docSnippets must deserialize its single entry")
        val snippet = manifest.docSnippets.first()
        assertEquals("<!-- guardian:begin codex-agents-md -->", snippet.markerBegin)
        assertEquals("<!-- guardian:end codex-agents-md -->", snippet.markerEnd)
    }
}
