package dev.perf.report

import com.github.ajalt.mordant.rendering.AnsiLevel
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.terminal.TerminalRecorder
import dev.perf.analysis.PerfReport
import dev.perf.analysis.model.RenderingIncident
import dev.perf.analysis.model.Severity
import dev.perf.analysis.score.PerfScore
import dev.perf.analysis.score.PerfScoreTier
import dev.perf.analysis.score.SignalScore
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class ConsoleReporterTest {

    private fun recorderTerminal(): Pair<Terminal, TerminalRecorder> {
        val recorder = TerminalRecorder(ansiLevel = AnsiLevel.NONE)
        val terminal = Terminal(terminalInterface = recorder)
        return terminal to recorder
    }

    private fun sampleScore(active: Int = 4): PerfScore = PerfScore(
        score = 85,
        tier = PerfScoreTier.GOOD,
        breakdown = listOf(
            SignalScore("startup", 70, 30, true),
            SignalScore("rendering", 40, 20, false),
        ),
        activeSignalCount = active,
    )

    private fun report(
        perfScore: PerfScore? = sampleScore(),
        hasIssues: Boolean = false,
    ): PerfReport {
        val rendering = RenderingIncident(
            severity = if (hasIssues) Severity.WARNING else Severity.INFO,
            message = "test",
            totalFrames = 100,
            jankyFrames = if (hasIssues) 10 else 2,
            frozenFrames = 0,
            p90Ms = 10.0,
            p95Ms = 12.0,
            p99Ms = 15.0,
        )
        return PerfReport(
            packageName = "com.example.test",
            strictModeViolations = emptyList(),
            renderingIncident = rendering,
            memoryLeaks = emptyList(),
            perfScore = perfScore,
        )
    }

    // ── Phase 2: Null Guard ───────────────────────────────────────────────────

    @Test
    fun `omits score section when perfScore null — no-issues branch`() {
        val (terminal, recorder) = recorderTerminal()
        ConsoleReporter(terminal).print(report(perfScore = null))
        val output = recorder.output()
        assertFalse(output.contains("/ 100"), "Expected no '/ 100' when perfScore is null")
        assertFalse(output.contains("Score"), "Expected no 'Score' when perfScore is null")
    }

    @Test
    fun `omits score section when perfScore null — has-issues branch`() {
        val (terminal, recorder) = recorderTerminal()
        ConsoleReporter(terminal).print(report(perfScore = null, hasIssues = true))
        val output = recorder.output()
        assertFalse(output.contains("/ 100"), "Expected no '/ 100' when perfScore is null")
        assertFalse(output.contains("GOOD"), "Expected no tier label when perfScore is null")
        assertFalse(output.contains("NEEDS_IMPROVEMENT"), "Expected no tier label when perfScore is null")
        assertFalse(output.contains("BAD"), "Expected no tier label when perfScore is null")
    }

    // ── Phase 3: Score Headline ───────────────────────────────────────────────

    @Test
    fun `renders score headline on no-issues branch`() {
        val (terminal, recorder) = recorderTerminal()
        ConsoleReporter(terminal).print(report(perfScore = sampleScore()))
        val output = recorder.output()
        assertContains(output, "85 / 100")
        assertContains(output, "GOOD")
    }

    @Test
    fun `renders score headline on has-issues branch`() {
        val (terminal, recorder) = recorderTerminal()
        ConsoleReporter(terminal).print(report(perfScore = sampleScore(), hasIssues = true))
        val output = recorder.output()
        assertContains(output, "85 / 100")
        assertContains(output, "GOOD")
    }

    // ── Phase 4: Tier Label Variants ──────────────────────────────────────────

    @Test
    fun `renders NEEDS_IMPROVEMENT tier label`() {
        val score = PerfScore(score = 55, tier = PerfScoreTier.NEEDS_IMPROVEMENT,
            breakdown = emptyList(), activeSignalCount = 4)
        val (terminal, recorder) = recorderTerminal()
        ConsoleReporter(terminal).print(report(perfScore = score))
        val output = recorder.output()
        assertContains(output, "55 / 100")
        assertContains(output, "NEEDS IMPROVEMENT")
    }

    @Test
    fun `renders BAD tier label`() {
        val score = PerfScore(score = 20, tier = PerfScoreTier.BAD,
            breakdown = emptyList(), activeSignalCount = 4)
        val (terminal, recorder) = recorderTerminal()
        ConsoleReporter(terminal).print(report(perfScore = score))
        val output = recorder.output()
        assertContains(output, "20 / 100")
        assertContains(output, "BAD")
    }

    // ── Phase 5: Breakdown Table ──────────────────────────────────────────────

    @Test
    fun `renders breakdown table headers`() {
        val (terminal, recorder) = recorderTerminal()
        ConsoleReporter(terminal).print(report(perfScore = sampleScore()))
        val output = recorder.output()
        assertContains(output, "Signal")
        assertContains(output, "Sub-score")
        assertContains(output, "Weight")
        assertContains(output, "Active")
    }

    @Test
    fun `renders breakdown rows for active and inactive signals`() {
        val (terminal, recorder) = recorderTerminal()
        ConsoleReporter(terminal).print(report(perfScore = sampleScore()))
        val output = recorder.output()
        // Active signal
        assertContains(output, "startup")
        assertContains(output, "70")
        assertContains(output, "yes")
        // Inactive signal
        assertContains(output, "rendering")
        assertContains(output, "—")
        assertContains(output, "no")
    }

    // ── Phase 6: Low-Signal Warning ───────────────────────────────────────────

    @Test
    fun `emits low-signal warning when activeSignalCount is 2`() {
        val (terminal, recorder) = recorderTerminal()
        ConsoleReporter(terminal).print(report(perfScore = sampleScore(active = 2)))
        assertContains(recorder.output(), "fewer than 3")
    }

    @Test
    fun `emits low-signal warning when activeSignalCount is 0`() {
        val (terminal, recorder) = recorderTerminal()
        ConsoleReporter(terminal).print(report(perfScore = sampleScore(active = 0)))
        assertContains(recorder.output(), "fewer than 3")
    }

    @Test
    fun `no warning when activeSignalCount is 3`() {
        val (terminal, recorder) = recorderTerminal()
        ConsoleReporter(terminal).print(report(perfScore = sampleScore(active = 3)))
        assertFalse(recorder.output().contains("fewer than 3"))
    }

    @Test
    fun `no warning when activeSignalCount is 5`() {
        val (terminal, recorder) = recorderTerminal()
        ConsoleReporter(terminal).print(report(perfScore = sampleScore(active = 5)))
        assertFalse(recorder.output().contains("fewer than 3"))
    }
}
