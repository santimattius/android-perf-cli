package dev.perf.cli.command

import dev.perf.analysis.score.PerfScore
import dev.perf.analysis.score.PerfScoreTier
import dev.perf.analysis.score.SignalScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [evaluateMinScore] — the internal score-gate function extracted from RunCommand.
 *
 * T-12.3 RED: written before [evaluateMinScore] exists in RunCommand.kt.
 *
 * Test contract:
 *  1. minScore=80, score=75 → result message contains "Perf score" and "80"; shouldFail=true
 *  2. minScore=80, score=80 → result is null (gate passes); shouldFail=false
 *  3. minScore=90, score=70 → result message present; shouldFail=true
 *  4. minScore=null → result is null (gate disabled regardless of score)
 */
class RunCommandMinScoreTest {

    private fun aScore(score: Int): PerfScore = PerfScore(
        score = score,
        tier = when {
            score >= 80 -> PerfScoreTier.GOOD
            score >= 50 -> PerfScoreTier.NEEDS_IMPROVEMENT
            else -> PerfScoreTier.BAD
        },
        breakdown = listOf(SignalScore("rendering", score, 20, true)),
        activeSignalCount = 1,
    )

    // T-12.3 test 1: score < minScore → message contains score and minimum, shouldFail=true
    @Test
    fun `evaluateMinScore - score below minimum returns failure message`() {
        val (message, shouldFail) = evaluateMinScore(minScore = 80, perfScore = aScore(75))

        assertTrue(shouldFail, "shouldFail must be true when score < minScore")
        assertTrue(
            message != null && message.contains("75"),
            "Message must contain the actual score (75), was: $message",
        )
        assertTrue(
            message != null && message.contains("80"),
            "Message must contain the minimum (80), was: $message",
        )
    }

    // T-12.3 test 2: score == minScore → gate passes, no message
    @Test
    fun `evaluateMinScore - score equal to minimum passes with no message`() {
        val (message, shouldFail) = evaluateMinScore(minScore = 80, perfScore = aScore(80))

        assertEquals(false, shouldFail, "shouldFail must be false when score == minScore")
        assertNull(message, "Message must be null when gate passes, was: $message")
    }

    // T-12.3 test 3: score > minScore → gate passes, no message
    @Test
    fun `evaluateMinScore - score above minimum passes with no message`() {
        val (message, shouldFail) = evaluateMinScore(minScore = 80, perfScore = aScore(90))

        assertEquals(false, shouldFail, "shouldFail must be false when score > minScore")
        assertNull(message, "Message must be null when gate passes, was: $message")
    }

    // T-12.3 test 4: minScore null → gate disabled, no message, shouldFail=false
    @Test
    fun `evaluateMinScore - minScore null disables the gate`() {
        val (message, shouldFail) = evaluateMinScore(minScore = null, perfScore = aScore(0))

        assertEquals(false, shouldFail, "shouldFail must be false when minScore is null (gate disabled)")
        assertNull(message, "Message must be null when gate is disabled, was: $message")
    }

    // T-12.3 test 5: message contains "Perf score" prefix
    @Test
    fun `evaluateMinScore - failure message starts with Perf score`() {
        val (message, _) = evaluateMinScore(minScore = 90, perfScore = aScore(70))

        assertTrue(
            message != null && message.contains("Perf score", ignoreCase = true),
            "Message must contain 'Perf score', was: $message",
        )
    }
}
