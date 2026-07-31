package app.cloudmoji.android.ui.parents

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from `ios/Cloudmoji/CloudmojiTests`'s `GateChallenge` coverage —
 * see `ios/Cloudmoji/Cloudmoji/Views/ParentalGate.swift`'s own doc for why
 * the sequence is fixed rather than random: a two-year-old cannot do
 * arithmetic at all, so the gate does not need unpredictability, only
 * testability.
 */
class GateChallengeTest {

    @Test
    fun `the eight pairs match iOS exactly, in order`() {
        val expected = listOf(
            7 to 8, 9 to 6, 12 to 7, 6 to 11, 8 to 9, 11 to 8, 7 to 12, 9 to 7,
        )
        assertEquals(expected, GateChallenge.all.map { it.a to it.b })
    }

    @Test
    fun `answer is the product`() {
        assertEquals(56, GateChallenge(7, 8).answer)
        assertEquals(132, GateChallenge(12, 11).answer)
    }

    @Test
    fun `at wraps rather than throwing past the end of the list`() {
        assertEquals(GateChallenge.all[0], GateChallenge.at(0))
        assertEquals(GateChallenge.all[7], GateChallenge.at(7))
        assertEquals(GateChallenge.all[0], GateChallenge.at(8))
        assertEquals(GateChallenge.all[1], GateChallenge.at(9))
        assertEquals(GateChallenge.all[0], GateChallenge.at(16))
    }

    @Test
    fun `at treats a negative index the same as its absolute value, mirroring iOS`() {
        assertEquals(GateChallenge.at(3), GateChallenge.at(-3))
        assertEquals(GateChallenge.at(0), GateChallenge.at(-8))
    }

    @Test
    fun `accepts the exact right answer`() {
        assertTrue(GateChallenge(7, 8).accepts("56"))
    }

    @Test
    fun `accepts trims surrounding whitespace`() {
        assertTrue(GateChallenge(7, 8).accepts(" 56 "))
        assertTrue(GateChallenge(7, 8).accepts("\t56\n"))
    }

    @Test
    fun `accepts rejects a wrong number`() {
        assertFalse(GateChallenge(7, 8).accepts("55"))
        assertFalse(GateChallenge(7, 8).accepts("57"))
    }

    @Test
    fun `accepts rejects non-numeric input`() {
        assertFalse(GateChallenge(7, 8).accepts("fifty-six"))
        assertFalse(GateChallenge(7, 8).accepts(""))
        assertFalse(GateChallenge(7, 8).accepts("   "))
    }

    @Test
    fun `accepts rejects a decimal even when its value matches`() {
        // Stricter than the web's `Number("56.0")`, matching iOS's `Int(...)`
        // parse — a decimal is not a whole number.
        assertFalse(GateChallenge(7, 8).accepts("56.0"))
    }

    @Test
    fun `accepts rejects the answer with trailing junk`() {
        assertFalse(GateChallenge(7, 8).accepts("56a"))
        assertFalse(GateChallenge(7, 8).accepts("5 6"))
    }
}
