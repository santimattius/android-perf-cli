package dev.perf.analysis.model

import kotlinx.serialization.Serializable

/**
 * A single permission denial captured from `system_server` logcat.
 *
 * Always [Severity.WARNING] — denials degrade UX but do not crash the app.
 * Aggregate severity lives on [PermissionDenialReport].
 */
@Serializable
data class PermissionDenial(
    override val severity: Severity = Severity.WARNING,
    override val message: String,
    val packageName: String,
    val permission: String,
    val uid: Int,
    val rawLine: String,
    val timestampMs: Long? = null,
) : PerfIncident
