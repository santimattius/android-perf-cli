package dev.perf.analysis.fake

import dev.perf.adb.AdbClient
import java.io.File

/**
 * Test double for [AdbClient].
 *
 * Design decision: this is a hand-written fake, not a generated mock. The
 * parsing logic under test (LogcatParser, GfxInfoCapture) is sensitive to the
 * exact content of the strings returned — a real fake makes fixture data
 * explicit and readable in the test, whereas a mock would bury it in
 * `whenever(...).thenReturn(...)` stubs scattered across setUp methods.
 *
 * [shellHandler] receives the raw vararg array and returns a String — this
 * lets each test decide how to respond to specific commands without coupling
 * the fake to any particular command set.
 *
 * [streamLines] are fed to [stream]'s onLine callback synchronously, line by
 * line, until the list is exhausted or onLine returns false. This mirrors the
 * real device.stream() contract and lets Flow collectors terminate naturally.
 */
class FakeAdbClient(
    override val serial: String = "emulator-5554",
    private val shellHandler: (Array<out String>) -> String = { "" },
    private val streamLines: List<String> = emptyList(),
) : AdbClient {

    override fun shell(vararg args: String): String = shellHandler(args)

    override fun stream(vararg args: String, onLine: (String) -> Boolean) {
        for (line in streamLines) {
            if (!onLine(line)) return
        }
    }

    override fun pull(devicePath: String, destination: File): File = destination
    override fun push(source: File, devicePath: String) = Unit
    override fun install(apk: File, replace: Boolean) = Unit
}
