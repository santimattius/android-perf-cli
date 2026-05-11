package dev.perf.analysis.permission

import dev.perf.adb.AdbClient
import dev.perf.analysis.model.PermissionDenial
import dev.perf.analysis.model.PermissionDenialReport

/**
 * Captures permission denials emitted by `system_server` (`ActivityManager:W` / `PackageManager:W`)
 * and filters by the target package's UID.
 *
 * Lifecycle:
 *  1. Resolve UID once via `dumpsys package <pkg>` at start.
 *     - If resolution fails, returns an empty INFO report immediately (no stream opened).
 *  2. Open a SINGLE logcat stream filtered to the two `system_server` tags at WARN level.
 *  3. On each line, parse + filter by UID; append to a synchronized list.
 *  4. Cancellation of the parent coroutine scope stops the stream — the list snapshot is the result.
 *
 * ADR-3: UID-based filtering ensures we capture denials emitted via Binder on behalf of the app,
 * which a PID-based filter would miss.
 * ADR-4: UID resolved once; if resolution fails, capture short-circuits (no stream opened).
 */
class PermissionDenialCapture(private val adbClient: AdbClient) {

    suspend fun capture(packageName: String): PermissionDenialReport {
        val uid = resolveUid(packageName) ?: return PermissionDenialReport(denials = emptyList())

        val denials = mutableListOf<PermissionDenial>()
        val lock = Any()

        adbClient.stream(
            "logcat",
            "-s", "ActivityManager:W", "PackageManager:W",
            "-v", "threadtime",
        ) { line ->
            PermissionDenialParser.parseDenialLine(line, uid, packageName)?.let { d ->
                synchronized(lock) { denials.add(d) }
            }
            true
        }

        return PermissionDenialReport(denials = synchronized(lock) { denials.toList() })
    }

    internal fun resolveUid(packageName: String): Int? {
        val res = adbClient.shellAllowFailure("dumpsys", "package", packageName)
        if (res.exitCode != 0 || res.stdout.isBlank()) return null
        return PermissionDenialParser.extractUid(res.stdout)
    }
}
