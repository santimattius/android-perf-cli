package dev.perf.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import dev.perf.cli.config.PerfConfig
import dev.perf.cli.config.saveConfig
import dev.perf.cli.sdk.AndroidSdkLocator
import dev.perf.cli.sdk.AndroidSdkInstaller
import java.io.File

/**
 * `perf init` — interactive first-time setup.
 *
 * Uses Clikt's `prompt` for interactive paths and supports `--android-sdk` for CI.
 * Use `--install-sdk` to download command-line tools + platform-tools when no SDK is present.
 */
class InitCommand : CliktCommand(
    name = "init",
    help = "Configure the Android SDK path and default AVD for perf-cli.",
) {
    private val installSdk: Boolean by option(
        "--install-sdk",
        help = "Download Android command-line tools and install platform-tools (adb) before saving config.",
    ).flag()

    private val directory: File? by option(
        "-d",
        "--directory",
        help = "SDK install directory when using --install-sdk (default: Android Studio location).",
    ).file(mustExist = false)

    private val androidSdk: String? by option(
        "--android-sdk",
        help = "Path to the Android SDK root (e.g. ~/Library/Android/sdk)",
        envvar = "ANDROID_HOME",
    )

    private val defaultAvd: String? by option(
        "--avd",
        help = "Default AVD name to use when none is specified in perf run",
    )

    override fun run() {
        val sdkPath = resolveSdkPath()
        val config = PerfConfig(
            androidSdkPath = sdkPath,
            defaultAvd = defaultAvd,
        )
        saveConfig(config)
        echo("✔ Configuration saved. Run `perf run --package <pkg>` to start monitoring.")
    }

    private fun resolveSdkPath(): String {
        if (installSdk) {
            val root = directory ?: AndroidSdkInstaller.defaultInstallRoot()
            echo("→ Installing Android SDK components under ${root.absolutePath} …")
            return AndroidSdkInstaller.installMinimal(root, ::echo).absolutePath
        }

        androidSdk?.let { path ->
            validateSdkPath(path)
            return File(path).absolutePath
        }

        val discovered = AndroidSdkLocator.discoverValidSdkRoot()
        if (discovered != null) {
            echo("Found Android SDK at ${discovered.absolutePath}")
            return discovered.absolutePath
        }

        val defaultPath = AndroidSdkInstaller.defaultInstallRoot().absolutePath
        val entered = prompt(
            "Android SDK path (or run `perf sdk install` first)",
            defaultPath,
        )
        val path = entered ?: defaultPath
        validateSdkPath(path)
        return File(path).absolutePath
    }

    private fun validateSdkPath(path: String) {
        require(AndroidSdkLocator.hasAdb(File(path))) {
            "adb not found at $path/platform-tools/adb — run `perf sdk install` or set a valid SDK path."
        }
    }
}
