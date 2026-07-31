package app.cloudmoji.android.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Count mode's own state, host-testable independent of Compose: which round
 * is live, the tile most recently counted, and the phrase currently on
 * screen. Ported from iOS `CountView.swift`'s `round`/`lastCounted`/`phrase`
 * `@State` and the functions that mutate them (`tap`, `startRound`, `shuffle`,
 * `nextRound`, the language-change handler).
 *
 * Deliberately holds no timer of its own. Unlike `WordsViewModel`'s bubble
 * and bounce, [lastCounted] is never put back to `null` on a delay — like
 * iOS's own `@State private var lastCounted`, it simply changes on the next
 * tap or a fresh round, so the tile just counted stays visually "just
 * counted" until something supersedes it. And unlike Words, this class knows
 * nothing about the mascot's mood: Count's celebration is a per-round event
 * with its own timing (1200ms delay, 3500ms hold — different from Words'
 * milestone timing), not a cumulative tap-count milestone, so it is driven
 * through a *second*, Count-specific `MascotMoodMachine` instance
 * (`CloudmojiApplication.countMoodMachine`) that `CountScreen` wires
 * directly — see that class's `celebrateNow`/`reset`.
 *
 * Every phrase-building call takes a `phraseFor` lambda rather than a
 * `CountingGrammar`/`Language` of its own, mirroring `WordsViewModel.tapEmoji`
 * taking an already-resolved `word: String` — this class never decides which
 * language a phrase is in.
 *
 * **Threading: not thread-safe, by design**, the same confined-to-one-thread
 * contract as `WordsViewModel`/`MascotMoodMachine`/`SpeechController`.
 */
class CountViewModel {

    private val roundState = MutableStateFlow<CountRound?>(null)

    /** The round on screen, or `null` only when the catalogue handed to
     * [startRound] was empty — a broken bundle, not a real state. */
    val round: StateFlow<CountRound?> = roundState.asStateFlow()

    private val lastCountedState = MutableStateFlow<Int?>(null)

    /** The tile counted most recently, which bounces once. `null` at the
     * start of a round. */
    val lastCounted: StateFlow<Int?> = lastCountedState.asStateFlow()

    private val phraseState = MutableStateFlow("")

    /** The phrase currently on screen — blank until something has been
     * counted, matching iOS `CountView.numeral(for:)`'s "blank at zero" rule
     * for the readout it sits beside. */
    val phrase: StateFlow<String> = phraseState.asStateFlow()

    /**
     * A fresh round: a new item (never the one just retired, per
     * [CountRound.pick]) at [target]. Used for the very first round, Shuffle,
     * Next, and a settings change that invalidates the round on screen —
     * every one of those is "start over" in iOS `CountView`, just with a
     * different [target].
     *
     * `null` in [round] afterward only when [countables] is empty, which
     * [CountRound.pick] already treats as the unreachable-in-production
     * degraded case: an empty screen beats a crash in front of a child.
     */
    fun startRound(countables: List<Countable>, target: Int) {
        val item = CountRound.pick(countables, roundState.value?.item)
        roundState.value = item?.let { CountRound(it, target) }
        lastCountedState.value = null
        phraseState.value = ""
    }

    /**
     * One tile tapped. Returns the freshly spoken phrase, or `null` when the
     * tap was refused (already counted, or out of range) — the caller must
     * not speak on `null`, matching iOS `CountView.tap`'s "a refused tap must
     * not speak" rule; the tile itself still presses either way, which is the
     * caller's concern, not this class's.
     */
    fun tap(index: Int, phraseFor: (Countable, Int) -> String): String? {
        val current = roundState.value ?: return null
        val next = current.tap(index) ?: return null
        roundState.value = next
        lastCountedState.value = index
        val spoken = phraseFor(next.item, next.progress)
        phraseState.value = spoken
        return spoken
    }

    /**
     * The round's closing line for the item on screen: the same phrase,
     * exclaimed — mirrors iOS `CountView.completionPhrase(_:)`. `null` when
     * there is no round to close out.
     */
    fun completionPhrase(phraseFor: (Countable, Int) -> String): String? {
        val current = roundState.value ?: return null
        return phraseFor(current.item, current.target) + "!"
    }

    /**
     * A language change must keep the on-screen phrase current — otherwise
     * it stays in the old language until the next tap, and would be handed
     * to the new language's voice on a replay. Mirrors iOS `CountView`'s
     * `onChange(of: model.effectiveLanguage)`. A no-op before anything has
     * been counted, matching [phrase]'s own "blank at zero" rule.
     */
    fun refreshPhrase(phraseFor: (Countable, Int) -> String) {
        val current = roundState.value ?: return
        if (current.progress > 0) phraseState.value = phraseFor(current.item, current.progress)
    }

    /** Overwrites the on-screen phrase directly — used for the round's
     * closing line, which is not built from a tap. */
    fun setPhrase(text: String) {
        phraseState.value = text
    }
}
