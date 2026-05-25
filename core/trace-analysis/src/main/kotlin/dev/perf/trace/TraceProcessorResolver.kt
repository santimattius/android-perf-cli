package dev.perf.trace

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission

/**
 * Locates the host `trace_processor` binary: PATH first, then [DEFAULT_INSTALL_PATH].
 */
object TraceProcessorResolver {

    const val DEFAULT_INSTALL_DIR = ".perf-cli"
    const val BINARY_NAME = "trace_processor"

    data class Resolved(val executable: File, val source: Source) {
        enum class Source { PATH, MANAGED }
    }

    fun resolve(): Resolved? {
        resolveFromPath()?.let { return it }
        return resolveManaged()
    }

    fun managedInstallPath(): File =
        File(System.getProperty("user.home"), "$DEFAULT_INSTALL_DIR/$BINARY_NAME")

    fun resolveFromPath(): Resolved? {
        val pathEnv = System.getenv("PATH") ?: return null
        for (dir in pathEnv.split(File.pathSeparator)) {
            val candidate = File(dir, BINARY_NAME)
            if (candidate.isFile && candidate.canExecute()) {
                return Resolved(candidate, Resolved.Source.PATH)
            }
        }
        return null
    }

    fun resolveManaged(): Resolved? {
        val candidate = managedInstallPath()
        if (candidate.isFile && candidate.canExecute()) {
            return Resolved(candidate, Resolved.Source.MANAGED)
        }
        return null
    }

    /** Install hint shown when [resolve] returns null. */
    fun installHint(): String =
        "Install trace_processor: curl -LO https://get.perfetto.dev/trace_processor && " +
            "chmod +x trace_processor && mv trace_processor ${managedInstallPath().absolutePath} " +
            "(or add it to PATH). See docs/perfetto-investigation.md."
}
