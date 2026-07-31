package app.cloudmoji.android.ui.parents

/**
 * The parental gate's own state, immutable and pure so it needs no Compose
 * runtime to test. Mirrors the two pieces of `@State` iOS `ParentalGate.swift`
 * keeps per attempt ([entry], [wasWrong]) plus the question-rotation counter
 * iOS's `RootContent` keeps alongside it ([index]).
 *
 * A fresh [GateAttempt] is created every time the gate opens — [index] is the
 * only thing that carries over from the previous attempt (via [next]), which
 * is exactly the rotate-but-never-remember shape iOS uses: no timer, no
 * penalty, no lock-out. See [next]'s own doc.
 */
data class GateAttempt(
    val index: Int = 0,
    val entry: String = "",
    val wasWrong: Boolean = false,
) {
    val challenge: GateChallenge get() = GateChallenge.at(index)

    /**
     * A digit typed (or pasted) into the field. Mirrors `ParentalGate.swift`'s
     * `onChange(of: entry)`: non-digit characters are stripped — a paste or a
     * hardware keyboard can still deliver letters even behind a numeric
     * keypad — and only a *non-empty* result clears [wasWrong]. Clearing it
     * unconditionally would also fire on [submit]'s own `entry = ""`, and the
     * "Not quite" message would never have a frame in which to appear;
     * leaving it up while the field is empty is also the better behaviour —
     * it keeps the message on screen until the parent starts a new answer.
     */
    fun withEntry(raw: String): GateAttempt {
        val digits = raw.filter(Char::isDigit)
        return copy(entry = digits, wasWrong = wasWrong && digits.isEmpty())
    }

    /**
     * Checks [entry] against [challenge]. A right answer is
     * [GateOutcome.Passed] and leaves this attempt untouched — the caller is
     * expected to close the gate. A wrong one is [GateOutcome.Failed],
     * carrying the *next* attempt to keep showing: the same [index] (a wrong
     * answer does not advance the rotation — only closing the gate does, via
     * [next]), the entry cleared, and [wasWrong] set. Mirrors
     * `ParentalGate.swift`'s `submit()`.
     */
    fun submit(): GateOutcome =
        if (challenge.accepts(entry)) {
            GateOutcome.Passed
        } else {
            GateOutcome.Failed(copy(entry = "", wasWrong = true))
        }

    /**
     * The fresh attempt for the *next* time the gate opens, whether this one
     * ended in a pass or a cancel. Mirrors `RootContent`'s `gateIndex += 1`,
     * fired from both `onPass` and `onCancel`: there is deliberately no
     * lock-out and no penalty for a wrong answer or for walking away — the
     * very next attempt is always just the next question in the rotation,
     * with a clean [entry] and [wasWrong] reset to `false`.
     */
    fun next(): GateAttempt = GateAttempt(index = index + 1)
}

/** What [GateAttempt.submit] found. */
sealed interface GateOutcome {
    /** The entry matched [GateAttempt.challenge]'s answer. */
    data object Passed : GateOutcome

    /** The entry did not match; [attempt] is what the gate should show next. */
    data class Failed(val attempt: GateAttempt) : GateOutcome
}
