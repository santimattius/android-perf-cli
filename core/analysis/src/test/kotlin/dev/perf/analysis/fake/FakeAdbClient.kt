package dev.perf.analysis.fake

import dev.perf.adb.AdbClient
import dev.perf.adb.AdbShellResult
import java.io.File
import kotlinx.coroutines.delay

/**
 * Test double for [AdbClient].
 *
 * [streamSessions] — one inner list per invocation of [stream] (in order). Each pair is
 * `(delayMsBeforeLine, line)` so [StartupCapture] races can be exercised with
 * [kotlinx.coroutines.test.runTest] virtual time when `delayMsBeforeLine` is positive and the
 * fake shares a [kotlinx.coroutines.test.StandardTestDispatcher] with the capture under test.
 *
 * When [streamSessions] is empty and [streamLines] is non-empty, a single session is built
 * from [streamLines] with zero delays (backward compatible with older tests).
 */
class FakeAdbClient(
    override val serial: String = "emulator-5554",
    private val shellHandler: (Array<out String>) -> String = { "" },
    private val streamLines: List<String> = emptyList(),
    private val allowFailureHandler: ((Array<out String>) -> AdbShellResult)? = null,
    private val streamSessions: List<List<Pair<Long, String>>> = emptyList(),
) : AdbClient {

    private val sessionQueue: ArrayDeque<List<Pair<Long, String>>> =
        ArrayDeque(
            when {
                streamSessions.isNotEmpty() -> streamSessions.toList()
                streamLines.isNotEmpty() -> listOf(streamLines.map { 0L to it })
                else -> emptyList()
            },
        )

    override fun shell(vararg args: String): String = shellHandler(args)

    override fun shellAllowFailure(vararg args: String): AdbShellResult {
        allowFailureHandler?.invoke(args)?.let { return it }
        return runCatching { AdbShellResult(0, shell(*args), "") }
            .getOrElse { e -> AdbShellResult(1, "", e.message ?: e.javaClass.simpleName) }
    }

    override suspend fun stream(vararg args: String, onLine: (String) -> Boolean) {
        val script = sessionQueue.removeFirstOrNull().orEmpty()
        for ((delayMs, line) in script) {
            if (delayMs > 0) delay(delayMs)
            if (!onLine(line)) return
        }
    }

    override fun pull(devicePath: String, destination: File): File = destination
    override fun push(source: File, devicePath: String) = Unit
    override fun install(apk: File, replace: Boolean) = Unit

    override fun execOut(vararg args: String): ByteArray = byteArrayOf()
}
