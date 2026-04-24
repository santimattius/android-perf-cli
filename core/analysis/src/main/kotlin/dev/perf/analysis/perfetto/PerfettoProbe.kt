package dev.perf.analysis.perfetto

import dev.perf.adb.AdbClient

/**
 * Resolves whether `perfetto` exists on the device and where.
 *
 * Design decision: `command -v perfetto` is the primary probe, but some
 * images expose the binary only at fixed paths without a usable PATH for
 * non-login shells — we fall back to common locations.
 */
internal object PerfettoProbe {

    private const val SCRIPT =
        "if command -v perfetto >/dev/null 2>&1; then command -v perfetto; " +
            "elif test -x /system/bin/perfetto; then echo /system/bin/perfetto; " +
            "elif test -x /system/xbin/perfetto; then echo /system/xbin/perfetto; " +
            "else echo \"\"; fi"

    fun resolvePath(device: AdbClient): String {
        val r = device.shellAllowFailure("sh", "-c", SCRIPT)
        if (!r.ok) return ""
        return r.stdout.lineSequence()
            .map { it.trim() }
            .firstOrNull { it.startsWith("/") && !it.contains(' ') }
            ?: ""
    }
}
