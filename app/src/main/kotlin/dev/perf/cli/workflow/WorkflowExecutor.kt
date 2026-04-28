package dev.perf.cli.workflow

import dev.perf.analysis.screenshot.ScreenshotCapture
import dev.perf.adb.AdbDevice
import dev.perf.cli.scenario.WorkflowStep
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val keyAliases =
    mapOf(
        "BACK" to "4",
        "HOME" to "3",
        "ENTER" to "66",
    )

/**
 * Runs typed workflow actions (ADB `input`, `am start`, `gfx reset`, optional screenshot).
 */
class WorkflowExecutor(private val device: AdbDevice, private val packageName: String) {

    suspend fun run(
        steps: List<WorkflowStep>,
        screenshotFile: suspend (WorkflowStep, Int) -> File,
        emittedScreenshots: MutableList<File>? = null,
    ) = withContext(Dispatchers.IO) {
            var screenshotIndex = 0
            for (step in steps) {
                when (step.type.lowercase()) {
                    "screenshot", "screensnap", "screenshot_here" -> {
                        val dst = screenshotFile(step, screenshotIndex++)
                        kotlinx.coroutines.delay(400)
                        ScreenshotCapture.capture(device, dst)
                        emittedScreenshots?.add(dst)
                        kotlinx.coroutines.delay(200)
                    }
                    else -> runNonScreenshot(step)
                }
            }
        }

    private fun runNonScreenshot(step: WorkflowStep) {
        when (step.type.lowercase()) {
            "wait" ->
                Thread.sleep(((step.seconds ?: 1.0) * 1000).toLong().coerceAtLeast(0))

            "gfx_reset", "gfxreset" ->
                device.shell("dumpsys", "gfxinfo", packageName, "reset")

            "deeplink", "intent" -> {
                val uri = step.uri ?: error("workflow step '${step.type}' requires uri")
                val ret = device.shellAllowFailure(
                    "/system/bin/sh",
                    "-c",
                    "am start -a android.intent.action.VIEW -d ${shellQuote(uri)}",
                )
                check(ret.ok) { "am start VIEW failed (${ret.exitCode}): ${ret.stderr.take(300)}" }
            }

            "keyevent", "key" -> {
                val key = step.key ?: error("keyevent step requires key")
                val sent = keyAliases[key.uppercase()]
                    ?: if (key.all { ch -> ch.isDigit() }) key else error("unsupported key \"$key\"")
                device.shell("input", "keyevent", sent)
            }

            "tap" ->
                device.shell(
                    "input",
                    "tap",
                    (step.x ?: error("tap needs x")).toString(),
                    (step.y ?: error("tap needs y")).toString(),
                )

            "swipe" ->
                device.shell(
                    "input",
                    "swipe",
                    (step.x ?: error("swipe needs x")).toString(),
                    (step.y ?: error("swipe needs y")).toString(),
                    (step.x2 ?: error("swipe needs x2")).toString(),
                    (step.y2 ?: error("swipe needs y2")).toString(),
                    (step.durationMs ?: 300).toString(),
                )

            else -> error("Unknown workflow step type: ${step.type}")
        }
    }

    companion object {
        private fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"
    }
}
