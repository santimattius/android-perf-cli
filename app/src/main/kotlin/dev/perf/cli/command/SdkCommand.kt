package dev.perf.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.file
import dev.perf.cli.config.PerfConfig
import dev.perf.cli.config.saveConfig
import dev.perf.cli.error.runFriendly
import dev.perf.cli.sdk.AndroidSdkInstaller
import java.io.File

/**
 * `perf sdk` — install or refresh the Android SDK components needed by perf-cli (adb).
 */
class SdkCommand : CliktCommand(
    name = "sdk",
    help = "Install or manage the Android SDK used for adb (platform-tools).",
) {
    init {
        subcommands(SdkInstallCommand())
    }

    override fun run() = Unit
}

class SdkInstallCommand : CliktCommand(
    name = "install",
    help = "Download Android command-line tools (if missing) and install platform-tools (adb).",
) {
    private val directory: File? by option(
        "-d",
        "--directory",
        help = "SDK root directory (default: same as Android Studio — ~/Library/Android/sdk on macOS, etc.)",
    ).file(mustExist = false)

    override fun run() = runFriendly {
        val root = directory ?: AndroidSdkInstaller.defaultInstallRoot()
        echo("→ Installing Android SDK components under ${root.absolutePath} …")
        val sdkRoot = AndroidSdkInstaller.installMinimal(root, ::echo)
        saveConfig(PerfConfig(androidSdkPath = sdkRoot.absolutePath, defaultAvd = null))
        echo("✔ SDK ready. Configuration saved — run `perf run --package <pkg>` when a device is connected.")
    }
}
