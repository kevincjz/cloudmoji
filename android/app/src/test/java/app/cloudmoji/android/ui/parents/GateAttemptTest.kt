package app.cloudmoji.android.ui.parents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate's state machine — question generation, right/wrong answers,
 * regeneration on close, and (the absence of) a lock-out. Ported from the
 * shape `ios/Cloudmoji/CloudmojiUITests/ParentalGateUITests.swift` proves at
 * the UI level (`testAWrongAnswerDoesNotOpenSettingsAndTheRightOneDoes`) down
 * to the pure logic `GateAttempt` isolates, so it runs as a plain JVM test —
 * no Compose runtime, no device.
 */
class GateAttemptTest {

    // MARK: - A fresh attempt

    @Test
    fun `a fresh attempt starts on the question at its index, empty and not wrong`() {
        val attempt = GateAttempt(index = 2)
        assertEquals(GateChallenge.at(2), attempt.challenge)
        assertEquals("", attempt.entry)
        assertFalse(attempt.wasWrong)
    }

    // MARK: - Typing

    @Test
    fun `withEntry strips non-digit characters`() {
        val attempt = GateAttempt().withEntry("5a6b")
        assertEquals("56", attempt.entry)
    }

    @Test
    fun `a non-empty edit clears wasWrong`() {
        val wrong = GateAttempt(wasWrong = true)
        val edited = wrong.withEntry("5")
        assertFalse(edited.wasWrong)
    }

    @Test
    fun `clearing the field to empty leaves wasWrong standing`() {
        // Load-bearing, not a nicety: ParentalGate.swift's own `submit()`
        // clears `entry` to "" in the same update that sets `wasWrong = true`
        // — an unconditional clear-on-any-change would erase the error the
        // instant it was set, and "Not quite" would never have a frame to
        // render in.
        val wrong = GateAttempt(wasWrong = true)
        val stillEmpty = wrong.withEntry("")
        assertTrue(stillEmpty.wasWrong)
    }

    @Test
    fun `an empty edit when not already wrong stays not wrong`() {
        val attempt = GateAttempt().withEntry("")
        assertFalse(attempt.wasWrong)
    }

    // MARK: - Submitting

    @Test
    fun `the right answer passes and does not mutate the attempt`() {
        val attempt = GateAttempt(index = 0).withEntry("56") // 7 x 8
        val outcome = attempt.submit()
        assertEquals(GateOutcome.Passed, outcome)
    }

    @Test
    fun `a wrong answer fails, clears the entry, and flags wasWrong`() {
        val attempt = GateAttempt(index = 0).withEntry("55")
        val outcome = attempt.submit()
        assertTrue(outcome is GateOutcome.Failed)
        val next = (outcome as GateOutcome.Failed).attempt
        assertEquals("", next.entry)
        assertTrue(next.wasWrong)
    }

    @Test
    fun `a wrong answer does not advance the question`() {
        // Only closing the gate rotates the question (see next()) — retrying
        // the same wrong entry asks the same sum again, which is what lets a
        // parent who mistyped just try again without losing their place.
        val attempt = GateAttempt(index = 3).withEntry("1")
        val outcome = attempt.submit() as GateOutcome.Failed
        assertEquals(attempt.challenge, outcome.attempt.challenge)
        assertEquals(3, outcome.attempt.index)
    }

    @Test
    fun `an empty entry is rejected, not treated as zero`() {
        val attempt = GateAttempt(index = 0) // entry is ""
        assertTrue(attempt.submit() is GateOutcome.Failed)
    }

    // MARK: - Regeneration on close

    @Test
    fun `next advances the index by exactly one`() {
        val attempt = GateAttempt(index = 4, entry = "12", wasWrong = true)
        val fresh = attempt.next()
        assertEquals(5, fresh.index)
    }

    @Test
    fun `next resets entry and wasWrong regardless of what came before`() {
        val attempt = GateAttempt(index = 1, entry = "999", wasWrong = true)
        val fresh = attempt.next()
        assertEquals("", fresh.entry)
        assertFalse(fresh.wasWrong)
    }

    @Test
    fun `next changes the question shown, and wraps through the rotation`() {
        var attempt = GateAttempt(index = 0)
        val seen = mutableListOf(attempt.challenge)
        repeat(GateChallenge.all.size - 1) {
            attempt = attempt.next()
            seen += attempt.challenge
        }
        // Every one of the eight questions was visited, each exactly once,
        // before the rotation repeats.
        assertEquals(GateChallenge.all.toSet(), seen.toSet())
        assertEquals(GateChallenge.all.size, seen.distinct().size)

        val wrapped = attempt.next()
        assertEquals(GateChallenge.at(0), wrapped.challenge)
    }

    @Test
    fun `a pass and a cancel advance the rotation the same way`() {
        // RootContent's gateIndex += 1 fires from both onPass and onCancel —
        // there is nothing that distinguishes how the gate closed from the
        // state machine's point of view.
        val passed = GateAttempt(index = 0).next()
        val cancelled = GateAttempt(index = 0).next()
        assertEquals(passed, cancelled)
    }

    // MARK: - No lock-out

    @Test
    fun `there is no lock-out — any number of consecutive wrong answers still leaves the right one able to pass`() {
        var attempt = GateAttempt(index = 0)
        val correctEntry = attempt.challenge.answer.toString()

        repeat(50) {
            attempt = attempt.withEntry("0")
            val outcome = attempt.submit()
            assertTrue("attempt #$it should have failed", outcome is GateOutcome.Failed)
            attempt = (outcome as GateOutcome.Failed).attempt
            // Still the very same question — no penalty escalates it away.
            assertEquals(GateChallenge.at(0), attempt.challenge)
        }

        val finalOutcome = attempt.withEntry(correctEntry).submit()
        assertEquals(GateOutcome.Passed, finalOutcome)
    }

    @Test
    fun `wrong answers across many closed attempts never repeat a lock — every fresh attempt is answerable`() {
        var index = 0
        repeat(20) {
            val attempt = GateAttempt(index = index)
            val outcome = attempt.withEntry(attempt.challenge.answer.toString()).submit()
            assertEquals(GateOutcome.Passed, outcome)
            index += 1
        }
    }

    @Test
    fun `two different questions in the rotation are not equal`() {
        assertNotEquals(GateChallenge.at(0), GateChallenge.at(1))
    }
}
