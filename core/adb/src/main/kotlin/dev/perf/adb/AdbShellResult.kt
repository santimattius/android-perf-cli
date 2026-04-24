package dev.perf.adb

/**
 * Result of an `adb shell …` invocation that is allowed to exit non-zero
 * without throwing (used for diagnostics on commands like `perfetto`).
 */
data class AdbShellResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
) {
    val ok: Boolean get() = exitCode == 0
}
