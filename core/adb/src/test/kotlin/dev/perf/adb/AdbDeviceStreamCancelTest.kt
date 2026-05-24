package dev.perf.adb

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
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
