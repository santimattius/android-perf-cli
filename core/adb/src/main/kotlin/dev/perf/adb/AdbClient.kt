package dev.perf.adb

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Contract for ADB operations.
 *
 * Design decision: interface instead of open class so tests can provide a
 * FakeAdbClient without subclassing ProcessBuilder-backed production code.
 * The suspend variants have default implementations (IO-dispatched wrappers)
 * so fakes only need to implement the blocking primitives.
 */
interface AdbClient {
    val serial: String

    fun shell(vararg args: String): String
    fun stream(vararg args: String, onLine: (String) -> Boolean)
    fun pull(devicePath: String, destination: File): File
    fun push(source: File, devicePath: String)
    fun install(apk: File, replace: Boolean = true)

    suspend fun shellAsync(vararg args: String): String =
        withContext(Dispatchers.IO) { shell(*args) }

    suspend fun pullAsync(devicePath: String, destination: File): File =
        withContext(Dispatchers.IO) { pull(devicePath, destination) }
}
