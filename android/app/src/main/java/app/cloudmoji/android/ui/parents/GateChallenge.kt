package app.cloudmoji.android.ui.parents

import kotlin.math.abs

/**
 * One arithmetic question for the parental gate.
 *
 * Ported from iOS `ParentalGate.swift`'s `GateChallenge`: the same eight
 * pairs, in the same order, rotated rather than randomised. The gate does
 * not need unpredictability — a two-year-old cannot do arithmetic at all —
 * and a fixed sequence is testable.
 */
data class GateChallenge(val a: Int, val b: Int) {

    val answer: Int get() = a * b

    /**
     * Whether [entry] is the right answer. Trimmed, so a numeric keypad's
     * stray whitespace does not fail a parent who typed the right thing;
     * anything that does not parse as a whole number is rejected outright —
     * mirrors iOS's `Int(entry.trimmingCharacters(in: .whitespaces))`, which
     * is already stricter than the web (`Number("")` there is `0`).
     */
    fun accepts(entry: String): Boolean {
        val value = entry.trim().toIntOrNull() ?: return false
        return value == answer
    }

    companion object {
        /** Ported verbatim from iOS `GateChallenge.all` / web `ParentalGate.tsx`. */
        val all: List<GateChallenge> = listOf(
            GateChallenge(7, 8),
            GateChallenge(9, 6),
            GateChallenge(12, 7),
            GateChallenge(6, 11),
            GateChallenge(8, 9),
            GateChallenge(11, 8),
            GateChallenge(7, 12),
            GateChallenge(9, 7),
        )

        /**
         * Wraps, so a parent opening the gate a ninth time gets the first
         * question again rather than an index out of range. Mirrors iOS's
         * `abs(index) % all.count` exactly, including its behaviour on a
         * negative index — [GateAttempt.index] only ever counts up from
         * zero, but a bare `at` should still answer the same question for
         * `index` and `-index` the way the ported formula does.
         */
        fun at(index: Int): GateChallenge = all[abs(index) % all.size]
    }
}
