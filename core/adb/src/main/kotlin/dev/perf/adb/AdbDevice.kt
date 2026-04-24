package dev.perf.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Abstraction over ADB commands for a specific connected device/emulator.
 *
 * Uses ProcessBuilder instead of Runtime.exec() to avoid shell-injection vectors
 * and to gain proper stream handling with timeout support.
 */
class AdbDevice(
    override val serial: String,
    private val adbPath: String = resolveAdb(),
) : AdbClient {

    override fun shell(vararg args: String): String = execute("shell", *args)

    override fun shellAllowFailure(vararg args: String): AdbShellResult =
        executeAllowFailure("shell", *args)

    override fun pull(devicePath: String, destination: File): File {
        execute("pull", devicePath, destination.absolutePath)
        return destination
    }

    override fun push(source: File, devicePath: String) {
        execute("push", source.absolutePath, devicePath)
    }

    override fun install(apk: File, replace: Boolean) {
        val flags = if (replace) arrayOf("-r") else emptyArray()
        execute("install", *flags, apk.absolutePath)
    }

    // ── Raw execution ─────────────────────────────────────────────────────────

    /**
     * Executes an arbitrary ADB command against this device's serial and
     * returns trimmed stdout.
     */
    fun execute(vararg args: String): String {
        val command = buildList {
            add(adbPath)
            add("-s")
            add(serial)
            addAll(args)
        }

        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw AdbException(
                command = command.joinToString(" "),
                exitCode = exitCode,
                stderr = stderr.trim(),
            )
        }

        return stdout.trim()
    }

    private fun executeAllowFailure(vararg args: String): AdbShellResult {
        val command = buildList {
            add(adbPath)
            add("-s")
            add(serial)
            addAll(args)
        }

        val process = ProcessBuilder(command)
            .redirectErrorStream(false)
            .start()

        val stdout = process.inputStream.bufferedReader().readText()
        val stderr = process.errorStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        return AdbShellResult(
            exitCode = exitCode,
            stdout = stdout.trim(),
            stderr = stderr.trim(),
        )
    }

    /**
     * Streams output line-by-line via [onLine]. Useful for long-running
     * commands like logcat. Blocks until the process exits or [onLine] returns
     * false (which kills the process).
     */
    override fun stream(vararg args: String, onLine: (String) -> Boolean) {
        val command = buildList {
            add(adbPath)
            add("-s")
            add(serial)
            addAll(args)
        }

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .start()

        try {
            process.inputStream.bufferedReader().use { reader ->
                for (line in reader.lines()) {
                    if (!onLine(line)) break
                }
            }
        } finally {
            process.destroyForcibly()
        }
    }

    companion object {
        /**
         * Lists all connected ADB devices and returns them as [AdbDevice] instances.
         */
        fun listDevices(adbPath: String = resolveAdb()): List<AdbDevice> {
            val output = ProcessBuilder(adbPath, "devices")
                .redirectErrorStream(true)
                .start()
                .inputStream
                .bufferedReader()
                .readText()

            return output.lines()
                .drop(1) // skip "List of devices attached"
                .filter { it.isNotBlank() && it.endsWith("device") }
                .map { line -> AdbDevice(serial = line.substringBefore("\t"), adbPath = adbPath) }
        }

        private fun resolveAdb(): String {
            val fromEnv = System.getenv("ANDROID_HOME")
                ?: System.getenv("ANDROID_SDK_ROOT")

            if (fromEnv != null) {
                val candidate = File(fromEnv, "platform-tools/adb")
                if (candidate.exists()) return candidate.absolutePath
            }

            // Fall back to PATH
            return "adb"
        }
    }
}
