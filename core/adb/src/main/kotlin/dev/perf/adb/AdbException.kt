package dev.perf.adb

class AdbException(
    val command: String,
    val exitCode: Int,
    val stderr: String,
) : RuntimeException("ADB command failed (exit $exitCode): $command\n$stderr")
