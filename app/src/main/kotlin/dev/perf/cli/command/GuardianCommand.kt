package dev.perf.cli.command

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.path
import dev.perf.guardian.CliTarget
import dev.perf.guardian.GuardianAssets
import dev.perf.guardian.GuardianInstallException
import dev.perf.guardian.GuardianInstaller
import dev.perf.guardian.GuardianManifest
import java.nio.file.Path

/** `perf guardian` — manage the Android Agent Guardian skills and personas. */
class GuardianCommand(
    install: GuardianInstallCommand = GuardianInstallCommand(),
) : CliktCommand(
        name = "guardian",
        help = "Install Android performance skills and agent personas into a repository.",
    ) {
    init {
        subcommands(install)
    }

    override fun run() = Unit
}

/**
 * `perf guardian install --target claude [--directory <repo>]`.
 *
 * Collisions with unowned or locally modified files abort without overwriting; there is no
 * force or backup mode. Installed and unchanged paths go to stdout; failures go to stderr with
 * a non-zero exit code.
 */
class GuardianInstallCommand(
    private val installer: GuardianInstaller = GuardianInstaller(),
    private val catalog: () -> GuardianManifest = GuardianAssets::loadCatalog,
) : CliktCommand(
        name = "install",
        help = "Install Guardian artifacts for a coding agent. Refuses to overwrite files it does not own.",
    ) {
    private val target: String by option(
        "--target",
        help = "Coding agent to install for: ${CliTarget.entries.joinToString(", ") { it.id }}.",
    ).choice(*CliTarget.entries.map { it.id }.toTypedArray()).required()

    private val directory: Path by option(
        "--directory",
        help = "Repository to install into. Defaults to the current working directory.",
    ).path(mustExist = true, canBeFile = false).default(Path.of("").toAbsolutePath())

    override fun run() {
        val result =
            try {
                installer.install(directory, target, catalog())
            } catch (e: GuardianInstallException) {
                throw CliktError(e.message ?: "Guardian installation failed")
            }
        result.installedPaths.forEach { echo("Installed $it") }
        result.unchangedPaths.forEach { echo("Unchanged $it") }
    }
}
