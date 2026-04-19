package dev.perf.cli.sdk

import java.io.File

/**
 * Finds an existing Android SDK root that already contains [platform-tools/adb][adb].
 */
object AndroidSdkLocator {

    private val adb: String = "${File.separator}platform-tools${File.separator}adb"

    fun discoverValidSdkRoot(): File? =
        candidateRoots()
            .map { it.absoluteFile.normalize() }
            .distinct()
            .firstOrNull { root ->
                File(root.path + adb).isFile
            }

    private fun candidateRoots(): List<File> = buildList {
        System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
        System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }?.let { add(File(it)) }
        val home = System.getProperty("user.home") ?: return@buildList
        val homeDir = File(home)
        when {
            Os.current == Os.MAC -> add(File(homeDir, "Library/Android/sdk"))
            Os.current == Os.LINUX -> add(File(homeDir, "Android/Sdk"))
            Os.current == Os.WINDOWS -> {
                System.getenv("LOCALAPPDATA")?.let { add(File(it, "Android/Sdk")) }
                System.getenv("USERPROFILE")?.let { add(File(it, "AppData/Local/Android/Sdk")) }
            }
        }
    }

    fun hasAdb(sdkRoot: File): Boolean = File(sdkRoot.path + adb).isFile
}

enum class Os {
    MAC,
    LINUX,
    WINDOWS,
    ;

    companion object {
        val current: Os
            get() {
                val os = System.getProperty("os.name")?.lowercase() ?: return LINUX
                return when {
                    os.contains("mac") -> MAC
                    os.contains("win") -> WINDOWS
                    else -> LINUX
                }
            }
    }
}
