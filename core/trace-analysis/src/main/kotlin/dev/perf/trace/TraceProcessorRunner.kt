package dev.perf.trace

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Shells out to `trace_processor` — the JVM never parses trace protobuf.
 */
class TraceProcessorRunner(
    private val processFactory: (List<String>) -> Process = { cmd ->
        ProcessBuilder(cmd).redirectErrorStream(true).start()
    },
) {

    data class RunResult(val exitCode: Int, val output: String)

    fun runMetrics(
        executable: File,
        trace: File,
        metricsName: String,
        timeoutSeconds: Long = 120,
    ): RunResult =
        run(
            executable,
            listOf(executable.absolutePath, trace.absolutePath, "--run-metrics", metricsName),
            timeoutSeconds,
        )

    fun runQueryFile(
        executable: File,
        trace: File,
        queryFile: File,
        timeoutSeconds: Long = 120,
    ): RunResult =
        run(
            executable,
            listOf(
                executable.absolutePath,
                trace.absolutePath,
                "--query-file",
                queryFile.absolutePath,
            ),
            timeoutSeconds,
        )

    private fun run(executable: File, command: List<String>, timeoutSeconds: Long): RunResult {
        require(executable.isFile) { "trace_processor not found: ${executable.absolutePath}" }
        val process = processFactory(command)
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        val output =
            process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        if (!finished) {
            process.destroyForcibly()
            return RunResult(-1, output + "\n(trace_processor timed out after ${timeoutSeconds}s)")
        }
        return RunResult(process.exitValue(), output)
    }
}
