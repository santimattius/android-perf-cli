package dev.perf.cli.history

import kotlinx.serialization.Serializable

/**
 * Persisted record of a single `perf run` invocation.
 *
 * Schema evolution: only ADD nullable fields with defaults; never rename or remove.
 * [kotlinx.serialization.json.Json] with [ignoreUnknownKeys = true] handles
 * forward-compat reads from older versions of this schema.
 *
 * GraalVM AOT-safe: KSP generates the serializer statically — no reflection.
 * Always reference via explicit [SessionHistoryEntry.serializer()] at call sites.
 */
@Serializable
data class SessionHistoryEntry(
    val packageName: String,
    val runId: String,                 // "<isoTimestamp>-<uuid>" — lexicographic = chronological
    val timestampIso: String,          // ISO-8601 UTC
    val anrCount: Int,
    val crashCount: Int,
    val totalFrames: Int,
    val slowFrames: Int,
    val frozenFrames: Int,
    val exitSignalsEnabled: Boolean,   // drives hasExitSignalsCaveat in RateCalculator
    val scenarioLabel: String? = null,
    val coldStartP50Ms: Int? = null,
)
