package dev.perf.adb

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class AdbDeviceStreamCancelTest {

    private fun fakeBlockingAdbPath(): String {
        val bytes =
            checkNotNull(javaClass.getResourceAsStream("/fake-blocking-adb.sh")) {
                "fake-blocking-adb.sh missing from test resources"
            }.readBytes()
        val tmp = File.createTempFile("fake-blocking-adb", ".sh")
        tmp.deleteOnExit()
        tmp.writeBytes(bytes)
        check(tmp.setExecutable(true)) { "could not mark fake adb executable" }
        return tmp.absolutePath
    }

    @Test
    fun `stream returns within bounded time when cancelled during blocking read`() =
        runBlocking {
            val device = AdbDevice(serial = "test-device", adbPath = fakeBlockingAdbPath())
            val streamJob =
                launch(Dispatchers.IO) {
                    device.stream("logcat") { true }
                }
            delay(50)
            streamJob.cancel()
            val completed =
                kotlin.runCatching {
                    withTimeout(2_000) { streamJob.join() }
                }.isSuccess
            assertTrue(completed, "stream did not complete within 2s after cancellation")
        }

    @Test
    fun `three concurrent streams in coroutineScope all cancel within bounded time`() =
        runBlocking {
            // Mirrors the ExitSignalCapture pattern: three streams inside coroutineScope.
            // With invokeOnCompletion(onCancelling = false) the process is only destroyed
            // after the job completes — but the job can't complete because readLine() blocks.
            // With onCancelling = true, the process is destroyed as soon as cancellation
            // starts, closing the InputStream and unblocking readLine() via EOF.
            val device = AdbDevice(serial = "test-device", adbPath = fakeBlockingAdbPath())
            val captureJob = launch {
                coroutineScope {
                    launch { device.stream("logcat", "-b", "events", "-s", "am_anr") { true } }
                    launch { device.stream("logcat", "-b", "crash") { true } }
                    launch { device.stream("logcat", "-s", "lmkd") { true } }
                }
            }
            delay(50)
            captureJob.cancel()
            val completed = kotlin.runCatching {
                withTimeout(2_000) { captureJob.join() }
            }.isSuccess
            assertTrue(completed, "Three concurrent streams did not cancel within 2s")
        }

    @Test
    fun `stream stops when onLine returns false`() =
        runBlocking {
            val device = AdbDevice(serial = "test-device", adbPath = fakeBlockingAdbPath())
            var sawLine = false
            withTimeout(2_000) {
                device.stream("logcat") { line ->
                    sawLine = line == "logcat-ready"
                    false
                }
            }
            assertTrue(sawLine)
        }
}
