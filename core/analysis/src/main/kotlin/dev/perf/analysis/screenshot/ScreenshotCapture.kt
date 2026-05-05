package dev.perf.analysis.screenshot

import dev.perf.adb.AdbClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Writes a PNG via `adb exec-out screencap -p`. */
object ScreenshotCapture {

    suspend fun capture(device: AdbClient, destination: File): File = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()
        val png = device.execOut("screencap", "-p")
        destination.writeBytes(png)
        destination
    }
}
