package dev.perf.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.terminal
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.file
import com.github.ajalt.clikt.parameters.types.int
import dev.perf.adb.AdbDevice
import dev.perf.adb.EmulatorManager
import dev.perf.analysis.MonitorConfig
import dev.perf.analysis.PerfWatchdog
import dev.perf.analysis.perfetto.PerfettoCapture
import dev.perf.analysis.perfetto.PerfettoRecordResult
import dev.perf.analysis.startup.StartupCapture
import dev.perf.cli.config.PerfConfig
import dev.perf.cli.config.configExists
import dev.perf.cli.config.loadConfig
import dev.perf.cli.config.saveConfig
import dev.perf.cli.error.runFriendly
import dev.perf.cli.sdk.AndroidSdkLocator
import dev.perf.cli.scenario.ScenarioConfig
import dev.perf.cli.scenario.ThresholdEvaluator
import dev.perf.cli.docs.AndroidCliDocsRunner
import dev.perf.cli.scenario.loadScenario
import dev.perf.report.ConsoleReporter
import dev.perf.report.MarkdownReporter
import dev.perf.report.ReportRenderExtras
import dev.perf.report.source.SourceLinker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ProcessBuilder

/**
 * `perf run` — installs, launches, monitors and reports.
 */
class RunCommand : CliktCommand(
    name = "run",
    help = "Monitor app performance and generate a report.",
) {
    private val packageName: String by option(
        "--package", "-p",
        help = "Application package name (e.g. com.myapp.debug)",
    ).required()

    private val apk: File? by option(
        "--apk",
        help = "APK to install before monitoring. Skip if the app is already installed.",
    ).file(mustExist = true)

    private val scenario: File? by option(
        "--scenario",
        help = "Path to a scenario JSON file. Overrides --duration and --heap.",
    ).file(mustExist = true)

    private val duration: Int by option(
        "--duration", "-d",
        help = "Monitoring duration in seconds (ignored when --scenario is provided)",
    ).int().default(30)

    private val analyzeHeap: Boolean by option(
        "--heap",
        help = "Trigger a heap dump at end of session (ignored when --scenario is provided)",
    ).flag(default = false)

    private val output: File by option(
        "--output", "-o",
        help = "Output path for the Markdown report",
    ).file().default(File("report.md"))

    private val serial: String? by option(
        "--serial", "-s",
        help = "ADB device serial. Uses the only connected device if omitted.",
    )

    private val projectDir: File? by option(
        "--project-dir",
        help = "Android app project root for source links (default: auto-detect from cwd).",
    ).file(mustExist = true, canBeFile = false)

    private val perfettoFlag: Boolean by option(
        "--perfetto",
        help = "Record a Perfetto system trace for the whole session (device must have `perfetto`). Open in https://ui.perfetto.dev",
    ).flag(default = false)

    private val skipEmulator: Boolean by option(
        "--skip-emulator",
        help = "Do not auto-launch the default AVD from config when no device is connected.",
    ).flag(default = false)

    private val emulatorHeadless: Boolean by option(
        "--emulator-headless",
        help = "When auto-launching an AVD, use -no-window (CI-friendly).",
    ).flag(default = false)

    private val activityComponent: String? by option(
        "--activity",
        help = "Explicit launch component for cold start (e.g. com.example/.MainActivity). Optional if startup is enabled.",
    )

    private val startupFlag: Boolean by option(
        "--startup",
        help = "Measure cold start once at the beginning (am force-stop + am start -W).",
    ).flag(default = false)

    private val skipAndroidDocs: Boolean by option(
        "--skip-android-docs",
        help = "Do not run `android docs search` on the host even if `android` is on PATH.",
    ).flag(default = false)

    override fun run(): Unit = runFriendly {
        runBlocking {
            guardConfig()

        val config = loadConfig()
        val scenarioConfig = scenario?.let { loadScenario(it) }

        val effectiveDuration = scenarioConfig?.durationSeconds ?: duration
        val effectiveHeap = scenarioConfig?.engines?.heap ?: analyzeHeap
        val engines = scenarioConfig?.engines ?: dev.perf.cli.scenario.EngineConfig()

        val effectiveStartup = engines.startup || startupFlag

        val monitor = MonitorConfig(
            strictMode = engines.strictMode,
            anrHints = engines.anrHints,
            rendering = engines.rendering,
            startup = effectiveStartup,
        )

        val effectivePerfetto = engines.perfetto || perfettoFlag

        val device = ensureAdbDevice(config)

        val linker = projectDir?.let { SourceLinker(it) } ?: SourceLinker()

        coroutineScope {
            val perfettoDeferred = if (effectivePerfetto) {
                echo("→ Perfetto: recording system trace in parallel (${effectiveDuration}s)…")
                async(Dispatchers.IO) { capturePerfettoSession(device, effectiveDuration) }
            } else {
                null
            }

            apk?.let {
                echo("→ Installing ${it.name}…")
                device.install(it)
            }

            val coldStartMeasurement = if (monitor.startup) {
                echo("→ Measuring cold start (am force-stop + am start -W)…")
                withContext(Dispatchers.IO) {
                    StartupCapture.measureColdStart(device, packageName, activityComponent)
                }
            } else {
                null
            }

            val label = scenarioConfig?.name ?: "ad-hoc"
            echo("→ Starting scenario \"$label\" for $packageName (${effectiveDuration}s)…")
            device.shell("monkey", "-p", packageName, "-c", "android.intent.category.LAUNCHER", "1")

            val watchdog = PerfWatchdog(device, packageName, monitor)
            watchdog.start()
            delay(effectiveDuration * 1000L)

            echo("→ Collecting results…")
            var report = watchdog.stop(effectiveHeap)
            if (coldStartMeasurement != null) {
                report = report.copy(coldStart = coldStartMeasurement)
            }

            val perfettoOutcome = perfettoDeferred?.await()
            report = when (perfettoOutcome) {
                is PerfettoOutcome.Ok -> report.copy(
                    perfettoTraceFile = perfettoOutcome.file,
                    perfettoMessage = null,
                )
                is PerfettoOutcome.Skipped -> report.copy(perfettoMessage = perfettoOutcome.reason)
                null -> report
            }

            val breaches = scenarioConfig?.let { cfg ->
                ThresholdEvaluator.evaluate(report, cfg.thresholds)
            }.orEmpty()
            if (breaches.isNotEmpty()) {
                report = report.copy(thresholdBreaches = breaches)
            }

            val extras = ReportRenderExtras(
                androidCliDocsSnippet = if (!skipAndroidDocs) {
                    AndroidCliDocsRunner().searchForReport(report)
                } else {
                    null
                },
            )

            ConsoleReporter(terminal, linker).print(report, extras)
            MarkdownReporter(linker).write(report, output, extras)
            echo("→ Report saved to ${output.absolutePath}")

            if (breaches.isNotEmpty()) {
                throw ProgramResult(1)
            }
        }
        }
    }

    private suspend fun ensureAdbDevice(config: PerfConfig): AdbDevice {
        val sdk = config.androidSdkPath
        val adbPath = "$sdk/platform-tools/adb"

        fun listDevices() = AdbDevice.listDevices(adbPath = adbPath)

        var devices = listDevices()
        if (devices.isEmpty() && config.defaultAvd != null && !skipEmulator) {
            echo("→ No device connected — launching AVD \"${config.defaultAvd}\" …")
            EmulatorManager(sdk).launch(
                config.defaultAvd!!,
                headless = emulatorHeadless,
            )
            withContext(Dispatchers.IO) {
                ProcessBuilder(adbPath, "wait-for-device").inheritIO().start().waitFor()
            }
            repeat(180) {
                delay(1000L)
                val booted = withContext(Dispatchers.IO) {
                    runCatching {
                        ProcessBuilder(adbPath, "shell", "getprop", "sys.boot_completed")
                            .redirectErrorStream(true)
                            .start()
                            .inputStream.bufferedReader().readText().trim() == "1"
                    }.getOrDefault(false)
                }
                if (booted) return@repeat
            }
            devices = listDevices()
            if (devices.isEmpty()) {
                throw CliktError("Emulator launched but no device appears in `adb devices`.")
            }
        }

        return when {
            serial != null -> devices.firstOrNull { it.serial == serial }
                ?: throw CliktError(
                    "Device '$serial' not found. Connected: ${devices.map { it.serial }}",
                )

            devices.size == 1 -> devices.first()
            devices.isEmpty() -> throw CliktError(
                "No ADB devices connected.\n" +
                    "Connect a device, start an emulator, or set `defaultAvd` via `perf init` " +
                    "(use --skip-emulator to avoid auto-launch).",
            )

            else -> throw CliktError(
                "Multiple devices connected — specify one with `--serial <device>`.",
            )
        }
    }

    private suspend fun capturePerfettoSession(device: AdbDevice, durationSec: Int): PerfettoOutcome {
        val cap = PerfettoCapture(device)
        val remote = when (val rec = cap.recordToRemotePath(durationSec)) {
            is PerfettoRecordResult.Ok -> rec.remotePath
            is PerfettoRecordResult.Failed ->
                return PerfettoOutcome.Skipped(rec.reason)
        }
        val local = File(
            System.getProperty("java.io.tmpdir"),
            "perfetto-${packageName.replace('.', '-')}-$durationSec.perfetto-trace",
        )
        device.pull(remote, local)
        runCatching { device.shell("rm", remote) }
        return PerfettoOutcome.Ok(local)
    }

    private fun guardConfig() {
        if (configExists()) return
        val discovered = AndroidSdkLocator.discoverValidSdkRoot()
        if (discovered != null) {
            saveConfig(PerfConfig(androidSdkPath = discovered.absolutePath, defaultAvd = null))
            echo("→ Using Android SDK at ${discovered.absolutePath} (auto-detected).")
            return
        }
        echo("No Android SDK configured and adb was not found in ANDROID_HOME or common install paths.", err = true)
        echo("Install the SDK with:  perf sdk install", err = true)
        echo("Then run:               perf init   (optional if install saved config)", err = true)
        throw ProgramResult(1)
    }

    private sealed class PerfettoOutcome {
        data class Ok(val file: File) : PerfettoOutcome()
        data class Skipped(val reason: String) : PerfettoOutcome()
    }
}
