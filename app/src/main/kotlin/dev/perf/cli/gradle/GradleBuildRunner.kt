package dev.perf.cli.gradle

import dev.perf.cli.scenario.GradleBuildConfig
import java.io.File

/**
 * Invokes `./gradlew :module:assemble*` against an Android Gradle project.
 */
object GradleBuildRunner {

    /** Runs Gradle and picks the newest matching APK under `build/outputs/apk`. */
    fun assemble(projectDir: File, cfg: GradleBuildConfig): Result<File> = runCatching {
        require(projectDir.isDirectory) { "projectDir must be a directory: $projectDir" }

        val gradlewName =
            if (System.getProperty("os.name").orEmpty().contains("Windows", ignoreCase = true)) {
                "gradlew.bat"
            } else {
                "gradlew"
            }
        val gradlew = File(projectDir, gradlewName)
        require(gradlew.exists()) { "No Gradle wrapper found at ${gradlew.absolutePath}" }

        val task = cfg.task?.trim()?.takeIf { it.isNotEmpty() }
            ?: assembleTask(cfg.module, cfg.variant)

        val p = ProcessBuilder(gradlew.absolutePath, task)
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()
        val log = p.inputStream.bufferedReader().readText()
        val code = p.waitFor()
        if (code != 0) error("Gradle task failed (exit $code).\n---\n$log")

        val relativeModule = cfg.module.trim().removePrefix(":")
            .replace(':', '/')
        val apkRoot = File(projectDir, relativeModule).resolve("build").resolve("outputs").resolve("apk")

        require(apkRoot.exists()) { "Expected Gradle outputs missing: ${apkRoot.absolutePath}\nGradle log excerpt:\n${log.take(1200)}" }

        val apk = apkRoot.walkTopDown()
            .filter { it.isFile && it.name.endsWith(".apk", ignoreCase = true) }
            .maxByOrNull { it.lastModified() }
            ?: error("No APK files under ${apkRoot.absolutePath}.\nGradle log excerpt:\n${log.take(1200)}")
        apk
    }

    fun assembleTask(module: String, variant: String): String {
        val trimmed = module.trim().let { if (!it.startsWith(":")) ":$it" else it }
        val v = variant.lowercase().replaceFirstChar(Char::uppercaseChar)
        return "$trimmed:assemble${v.ifEmpty { "Debug" }}"
    }
}
