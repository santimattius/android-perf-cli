package dev.perf.adb

import java.io.File

/**
 * Manages AVD lifecycle: dynamic config patching and headless launch.
 *
 * Config.ini patching happens BEFORE the emulator starts so that AVD picks
 * the overrides on its first boot — no hot-reload needed.
 */
class EmulatorManager(
    /** Android SDK root (same as `ANDROID_HOME`), not only from the environment. */
    private val androidHome: String,
) {
    init {
        require(androidHome.isNotBlank()) { "androidHome must not be blank" }
    }
    private val emulatorBin = File(androidHome, "emulator/emulator")
    private val avdHome = File(System.getProperty("user.home"), ".android/avd")

    /**
     * Launches [avdName] with optional RAM/heap overrides.
     *
     * @param headless   If true, adds `-no-window -no-audio -no-boot-anim`.
     * @param ramMb      Overrides hw.ramSize in config.ini when non-null.
     * @param heapMb     Overrides vm.heapSize in config.ini when non-null.
     * @return the launched [Process]; caller is responsible for destruction.
     */
    fun launch(
        avdName: String,
        headless: Boolean = false,
        ramMb: Int? = null,
        heapMb: Int? = null,
    ): Process {
        if (ramMb != null || heapMb != null) {
            patchConfig(avdName, ramMb, heapMb)
        }

        val command = buildList {
            add(emulatorBin.absolutePath)
            add("-avd")
            add(avdName)
            if (headless) {
                add("-no-window")
                add("-no-audio")
                add("-no-boot-anim")
            }
        }

        return ProcessBuilder(command)
            .inheritIO()
            .start()
    }

    private fun patchConfig(avdName: String, ramMb: Int?, heapMb: Int?) {
        val configFile = File(avdHome, "$avdName.avd/config.ini")
        require(configFile.exists()) { "AVD config not found: ${configFile.absolutePath}" }

        val lines = configFile.readLines().toMutableList()

        fun setOrAdd(key: String, value: String) {
            val idx = lines.indexOfFirst { it.startsWith("$key=") }
            if (idx >= 0) lines[idx] = "$key=$value" else lines.add("$key=$value")
        }

        ramMb?.let { setOrAdd("hw.ramSize", "$it") }
        heapMb?.let { setOrAdd("vm.heapSize", "$it") }

        configFile.writeText(lines.joinToString("\n"))
    }
}
