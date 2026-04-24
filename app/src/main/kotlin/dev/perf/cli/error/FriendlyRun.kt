package dev.perf.cli.error

import com.github.ajalt.clikt.core.CliktError
import com.github.ajalt.clikt.core.ProgramResult
import com.github.ajalt.clikt.core.UsageError
import kotlinx.coroutines.CancellationException

/**
 * Wraps the body of a CLI command to translate low-level exceptions into
 * short, actionable messages.
 *
 * Design decision: Clikt already prints [CliktError] / [UsageError] /
 * [ProgramResult] without a stack trace. Anything else (IOException,
 * IllegalStateException from ADB/SDK flows) reaches the JVM uncaught
 * handler and produces the 20-line Java stack the user never needs.
 * This helper normalizes the output.
 *
 * Design decision: the full stack is only suppressed — never lost. Set
 * `PERF_CLI_VERBOSE=1` to see the original exception (useful for bug
 * reports or deep debugging).
 *
 * Cancellation is rethrown untouched — we never swallow it and never
 * convert it into a business error.
 *
 * Design decision: the wrapper is non-suspend so it can surround the
 * outer `runBlocking { ... }` of a command. That way any exception thrown
 * from within coroutines still reaches this handler synchronously after
 * `runBlocking` rethrows it.
 */
inline fun runFriendly(crossinline block: () -> Unit) {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: CliktError) {
        throw e
    } catch (e: ProgramResult) {
        throw e
    } catch (e: UsageError) {
        throw e
    } catch (e: Throwable) {
        if (System.getenv("PERF_CLI_VERBOSE") == "1") {
            throw e
        }
        val kind = e.javaClass.simpleName
        val msg = e.message?.takeIf { it.isNotBlank() } ?: "unexpected error"
        throw CliktError(
            "$kind: $msg\n\n" +
                "Rerun with PERF_CLI_VERBOSE=1 to see the full stack trace.",
        )
    }
}
