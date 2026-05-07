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
import dev.perf.analysis.screenshot.ScreenshotCapture
import dev.perf.analysis.perfetto.PerfettoCapture
import dev.perf.analysis.perfetto.PerfettoRecordResult
import dev.perf.analysis.perfetto.resolveTracingPreset
import dev.perf.analysis.proc.ProcSnapshotCapture
import dev.perf.analysis.model.startup.StartupType
import dev.perf.analysis.model.startup.toLegacyColdStart
import dev.perf.analysis.startup.StartupCapture
import dev.perf.analysis.startup.StartupCaptureConfig
import dev.perf.analysis.startup.ThresholdsView
import dev.perf.cli.gradle.GradleBuildRunner
import dev.perf.cli.config.PerfConfig
import dev.perf.cli.config.configExists
import dev.perf.cli.config.loadConfig
import dev.perf.cli.config.saveConfig
import dev.perf.cli.docs.AndroidCliDocsRunner
import dev.perf.cli.error.runFriendly
import dev.perf.cli.sdk.AndroidSdkLocator
import dev.perf.cli.scenario.EngineConfig
import dev.perf.cli.scenario.GradleBuildConfig
import dev.perf.cli.scenario.PerfettoCoverage
import dev.perf.cli.scenario.ScenarioConfig
import dev.perf.cli.scenario.ThresholdEvaluator
import dev.perf.cli.scenario.loadScenario
import dev.perf.cli.scenario.ThresholdConfig
import dev.perf.cli.scenario.resolveRunParameters
import dev.perf.cli.workflow.WorkflowExecutor
import dev.perf.report.ConsoleReporter
import dev.perf.report.MarkdownReporter
import dev.perf.report.ReportRenderExtras
import dev.perf.report.ReportSessionMeta
import dev.perf.report.source.SourceLinker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.ProcessBuilder
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.io.path.relativeTo

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
        help = "Android Gradle project root (source links + optional Gradle assemble).",
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

    private val fullCli: Boolean by option(
        "--full",
        help = "Heavy profile when no `--scenario`: heap+perfetto+startup, Vitals preset, snapshots, screenshots, longer duration.",
    ).flag(default = false)

    private val wakelocksFlag: Boolean by option(
        "--wakelocks",
        help = "Measure partial wake locks via dumpsys batterystats (runs destructive `batterystats --reset` at session start).",
    ).flag(default = false)

    private val exitSignalsFlag: Boolean by option(
        "--exit-signals",
        help = "Detect confirmed ANR, crash, and LMK events via logcat stream. Any confirmed exit fails the run.",
    ).flag(default = false)

    private val artifactsDir: File? by option(
        "--artifacts-dir",
        help = "Store screenshots and pulled trace here (default: directory next to `-o`).",
    ).file(canBeFile = false)

    private val buildVariant: String? by option(
        "--build-variant",
        help = "With `--project-dir`, run `gradlew :module:assemble<Variant>` first (`debug` or `release`).",
    )

    override fun run(): Unit = runFriendly {
        runBlocking {
            guardConfig()

            val config = loadConfig()
            val scenarioConfig = scenario?.let { loadScenario(it) }
            val resolved = resolveRunParameters(scenarioConfig, fullCli, duration)

            val mergedHeap = resolved.engines.heap || (scenarioConfig == null && analyzeHeap)
            val mergedPerfetto = resolved.engines.perfetto || (scenarioConfig == null && perfettoFlag)
            val mergedStartup = resolved.engines.startup || (scenarioConfig == null && startupFlag)
            val mergedWakelocks = resolved.engines.wakelocks || (scenarioConfig == null && wakelocksFlag)
            val mergedExitSignals = resolved.engines.exitSignals || (scenarioConfig == null && exitSignalsFlag)

            val presetEnum =
                resolveTracingPreset(
                    resolved.perfettoPreset,
                    resolved.perfettoPreset == "custom" && !resolved.perfettoCategories.isNullOrEmpty(),
                )

            val effectiveDurationSeconds = resolved.durationSeconds

            val gradleCfg: GradleBuildConfig? =
                resolved.build?.takeIf { it.enabled } ?: buildVariant.takeIf {
                    scenarioConfig?.build?.enabled != true &&
                        it != null &&
                        !it.equals("disabled", ignoreCase = true)
                }?.let { variant ->
                    GradleBuildConfig(enabled = true, variant = variant, module = scenarioConfig?.build?.module ?: ":app")
                }

            val device = ensureAdbDevice(config)
            val linker = projectDir?.let { SourceLinker(it) } ?: SourceLinker()

            val artifactRootHost: File =
                artifactsDir ?: File(output.absoluteFile.parentFile ?: File("."), "perf-artifacts").also { it.mkdirs() }
            artifactRootHost.mkdirs()

            coroutineScope {
                if (apk != null && gradleCfg != null) {
                    throw CliktError("Use either `--apk` or Gradle build (`scenario.build` / `--build-variant`), not both.")
                }
                if (gradleCfg != null) {
                    val root = projectDir
                        ?: throw CliktError("Gradle assemble requires `--project-dir` pointing to the Android project root.")
                    echo("→ Gradle: ${GradleBuildRunner.assembleTask(gradleCfg.module, gradleCfg.variant)} …")
                    val built = GradleBuildRunner.assemble(root, gradleCfg).getOrElse { e ->
                        throw CliktError(e.message ?: e.toString())
                    }
                    echo("→ Installing ${built.absolutePath}…")
                    device.install(built)
                }
                apk?.let {
                    echo("→ Installing ${it.name}…")
                    device.install(it)
                }

                val tracingSeconds =
                    when (resolved.perfettoCoverage) {
                        PerfettoCoverage.SESSION ->
                            effectiveDurationSeconds.coerceAtLeast(5)

                        PerfettoCoverage.STARTUP ->
                            kotlin.math.min(effectiveDurationSeconds, 120).coerceAtLeast(
                                kotlin.math.min(15, effectiveDurationSeconds),
                            )
                    }.coerceAtLeast(5)

                val perfettoDeferred =
                    if (mergedPerfetto) {
                        echo("→ Perfetto: preset=${resolved.perfettoPreset}, ${tracingSeconds}s, coverage=${resolved.perfettoCoverage}…")
                        async(Dispatchers.IO) {
                            capturePerfettoSession(
                                device = device,
                                durationSec = tracingSeconds,
                                presetEnum = presetEnum,
                                categories = resolved.perfettoCategories,
                                artifactCopyDir = artifactRootHost,
                                packageLabel = packageName,
                            )
                        }
                    } else {
                        null
                    }

                val startupReport =
                    if (mergedStartup) {
                        val typeLabel =
                            if (resolved.engines.startupTypes.isEmpty()) {
                                "cold"
                            } else {
                                resolved.engines.startupTypes.joinToString(", ")
                            }
                        val samples = resolved.engines.startupSamples.coerceIn(1, 10)
                        echo("→ Measuring startup ($typeLabel, $samples samples/type)…")
                        delay(400)
                        withContext(Dispatchers.IO) {
                            val cfg = buildStartupCaptureConfig(scenarioConfig, resolved.engines)
                            StartupCapture(device).capture(packageName, activityComponent, cfg)
                        }
                    } else {
                        null
                    }

                if (startupReport != null) {
                    delay(300)
                }

                val label = scenarioConfig?.name ?: "ad-hoc"
                echo("→ Scenario \"$label\" for $packageName (${effectiveDurationSeconds}s monitoring)…")
                if (mergedWakelocks) {
                    echo(
                        "⚠ Wake locks: `dumpsys batterystats --reset` runs at session start " +
                            "(clears system-wide battery stats).",
                    )
                }

                val screenshotArtifacts = mutableListOf<String>()
                val wfShotFiles = mutableListOf<File>()
                if (resolved.screenshotsEnabled) {
                    val f = screenshotFileNext(artifactRootHost, "start", screenshotArtifacts.size)
                    ScreenshotCapture.capture(device, f)
                    screenshotArtifacts.add(relativePathForReport(output, f))
                }

                device.shell(
                    "monkey",
                    "-p",
                    packageName,
                    "-c",
                    "android.intent.category.LAUNCHER",
                    "1",
                )

                if (resolved.workflow.isNotEmpty()) {
                    kotlinx.coroutines.delay(300)
                    WorkflowExecutor(device, packageName).run(
                        resolved.workflow,
                        screenshotFile = { step, index ->
                            val labelSlug =
                                step.label?.replace(nonAlnum, "-")?.trim('-')
                                    ?: "step-$index"
                            screenshotFileNext(artifactRootHost, labelSlug.ifEmpty { "wf-$index" }, index)
                        },
                        emittedScreenshots = wfShotFiles,
                    )
                    wfShotFiles.forEach {
                        screenshotArtifacts.add(relativePathForReport(output, it))
                    }
                }

                val monitor = MonitorConfig(
                    strictMode = resolved.engines.strictMode,
                    anrHints = resolved.engines.anrHints,
                    rendering = resolved.engines.rendering,
                    startup = mergedStartup,
                    wakelocks = mergedWakelocks,
                    exitSignals = mergedExitSignals,
                )

                val monitorStartedAt = Instant.now()
                val watchdog = PerfWatchdog(device, packageName, monitor)
                watchdog.start()
                delay(effectiveDurationSeconds * 1000L)
                val monitorEndedAt = Instant.now()

                echo("→ Collecting results…")

                val reportBeforeSidecars = watchdog.stop(mergedHeap)
                val procSnapshot =
                    if (resolved.collectProcSnapshot) {
                        withContext(Dispatchers.IO) { ProcSnapshotCapture(device).capture(packageName) }
                    } else {
                        null
                    }

                var report = reportBeforeSidecars.copy(procSnapshot = procSnapshot)

                if (startupReport != null) {
                    report =
                        report.copy(
                            startup = startupReport,
                            coldStart = startupReport.cold?.toLegacyColdStart(),
                        )
                }

                if (resolved.screenshotsEnabled) {
                    val f =
                        screenshotFileNext(artifactRootHost, "end", screenshotArtifacts.size)
                    kotlinx.coroutines.delay(400)
                    ScreenshotCapture.capture(device, f)
                    screenshotArtifacts.add(relativePathForReport(output, f))
                }

                report = report.copy(screenshotArtifacts = screenshotArtifacts)

                val perfettoOutcome = perfettoDeferred?.await()
                report =
                    when (perfettoOutcome) {
                        is PerfettoOutcome.Ok -> report.copy(
                            perfettoTraceFile = perfettoOutcome.file,
                            perfettoMessage = null,
                            perfettoDegradedFromVitalsPreset = perfettoOutcome.degraded,
                        )

                        is PerfettoOutcome.Skipped -> report.copy(perfettoMessage = perfettoOutcome.reason)
                        null -> report
                    }

                report =
                    ThresholdEvaluator.applyToReport(
                        report,
                        scenarioConfig?.thresholds ?: ThresholdConfig(),
                    )

                val generatedAt = Instant.now()
                val extras =
                    ReportRenderExtras(
                        androidCliDocsSnippet = if (!skipAndroidDocs) {
                            AndroidCliDocsRunner().searchForReport(report)
                        } else {
                            null
                        },
                        sessionMeta =
                            ReportSessionMeta(
                                generatedAtIso = isoInstant(generatedAt),
                                monitorWindowStartIso = isoInstant(monitorStartedAt),
                                monitorWindowEndIso = isoInstant(monitorEndedAt),
                                scenarioLabel = scenarioConfig?.name,
                                fullMode = fullCli || scenarioConfig?.profile.equals(
                                    "full",
                                    ignoreCase = true,
                                ),
                            ),
                    )

                ConsoleReporter(terminal, linker).print(report, extras)
                MarkdownReporter(linker).write(report, output, extras)
                echo("→ Report saved to ${output.absolutePath}")

                if (report.thresholdBreaches.isNotEmpty()) {
                    throw ProgramResult(1)
                }
            }
        }
    }

    private fun isoInstant(ins: Instant) = DateTimeFormatter.ISO_INSTANT.withZone(ZoneOffset.UTC).format(ins)

    private val nonAlnum = Regex("[^a-zA-Z0-9]+")

    private fun screenshotFileNext(root: File, label: String, index: Int): File {
        val safe = "${System.currentTimeMillis()}-$index-$label.png".replace("/", "-")
        return File(root, safe)
    }

    private fun relativePathForReport(reportOut: File, artifact: File): String {
        val base = reportOut.absoluteFile.parentFile ?: artifact.parentFile ?: return artifact.name
        return runCatching {
            artifact.toPath().relativeTo(base.toPath()).toString().replace('\\', '/')
        }.getOrElse { artifact.name }
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

    private suspend fun capturePerfettoSession(
        device: AdbDevice,
        durationSec: Int,
        presetEnum: dev.perf.analysis.perfetto.PerfettoTracingPreset,
        categories: List<String>?,
        artifactCopyDir: File,
        packageLabel: String,
    ): PerfettoOutcome {
        val cap = PerfettoCapture(device)
        val rec = cap.recordToRemotePath(durationSec, presetEnum, categories)
        when (rec) {
            is PerfettoRecordResult.Ok -> {
                val pulled =
                    File(
                        System.getProperty("java.io.tmpdir"),
                        "perfetto-${packageLabel.replace('.', '-')}-$durationSec.perfetto-trace",
                    )
                artifactCopyDir.mkdirs()
                val copyTo =
                    File(artifactCopyDir, "perfetto-${System.currentTimeMillis()}.perfetto-trace").absoluteFile
                device.pull(rec.remotePath, pulled)
                pulled.copyTo(copyTo, overwrite = true)
                runCatching { pulled.delete() }
                runCatching { device.shell("rm", rec.remotePath) }
                return PerfettoOutcome.Ok(copyTo, rec.degradedFromVitalsPreset)
            }

            is PerfettoRecordResult.Failed -> return PerfettoOutcome.Skipped(rec.reason)
        }
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
        data class Ok(val file: File, val degraded: Boolean = false) : PerfettoOutcome()
        data class Skipped(val reason: String) : PerfettoOutcome()
    }
}

private fun buildStartupCaptureConfig(
    scenarioConfig: ScenarioConfig?,
    engines: EngineConfig,
): StartupCaptureConfig {
    val th = scenarioConfig?.thresholds ?: ThresholdConfig()
    val types =
        if (engines.startupTypes.isEmpty()) {
            setOf(StartupType.COLD)
        } else {
            engines.startupTypes
                .mapNotNull { w ->
                    when (w.lowercase()) {
                        "cold" -> StartupType.COLD
                        "warm" -> StartupType.WARM
                        "hot" -> StartupType.HOT
                        else -> null
                    }
                }
                .toSet()
                .ifEmpty { setOf(StartupType.COLD) }
        }
    return StartupCaptureConfig(
        startupTypes = types,
        samplesPerType = engines.startupSamples.coerceIn(1, 10),
        thresholds =
            ThresholdsView(
                maxColdStartMs = th.maxColdStartMs,
                maxWarmStartMs = th.maxWarmStartMs,
                maxHotStartMs = th.maxHotStartMs,
            ),
    )
}
