package app.cloudmoji.android.model

import kotlin.random.Random
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Flash Cards' own state, host-testable independent of Compose: which round
 * is on screen, which tile just bounced, which tile is being celebrated, and
 * whether a correct answer is mid-celebration. Ported from iOS
 * `FlashCardsView.swift`'s `round`/`bouncingID`/`solvedID`/`isAdvancing`
 * `@State` and the `tap`/`nextRound`/`silence` functions that mutate them.
 *
 * Deliberately does not own the mascot's mood (a FlashCards-specific
 * [MascotMoodMachine] instance — see
 * `CloudmojiApplication.flashCardsMoodMachine`) or speech
 * (`SpeechController`); those need the active language and the speech stack,
 * neither of which this class has an opinion about — the same separation
 * [CountViewModel]'s own doc draws.
 *
 * Nor does it own the delays. *What* runs after one — starting a fresh round
 * versus re-speaking the current target — needs the narrowed emoji pool, the
 * active [Language], and the speech/mood stack, none of which belong on a
 * pure round-state class, so `FlashCardsScreen` runs both timers in
 * `LaunchedEffect`s keyed on [pendingAction] and [bounce]. Both of those
 * *values* live here rather than in the composable's own `remember`
 * specifically so they survive the Activity recreation a rotation causes —
 * the same reasoning `CloudmojiApplication`'s class doc gives for
 * `wordsViewModel`/`countViewModel`: without it, a rotation landing mid
 * celebration would leave a solved tile disabled with nothing left to ever
 * call [startRound] again.
 *
 * **Threading: not thread-safe, by design** — the same confined-to-one-thread
 * contract as [WordsViewModel]/[CountViewModel]/[MascotMoodMachine].
 */
class FlashCardsViewModel {
    companion object {
        /** iOS `FlashCardsView.bounceHold` — and the same 400ms
         * `WordsViewModel.BOUNCE_HOLD_MS` uses, so a tapped tile bounces for
         * the same span everywhere in this app. */
        const val BOUNCE_HOLD_MS = 400L

        /** iOS `FlashCardsView.advanceDelay`: how long a correct tile stays
         * solved before the next round starts, and how long a non-matching
         * tap's own word is left to sink in before the question comes back. */
        const val ADVANCE_DELAY_MS = 1400L
    }

    /** A tile mid-bounce. [token] is unique to every [tap], so a second tap on
     * the *same* tile restarts the hold rather than inheriting the first
     * one's remaining time — iOS restarts `bounceTask` unconditionally. */
    data class Bounce(val id: String, val token: Int)

    /**
     * What `FlashCardsScreen` must do [ADVANCE_DELAY_MS] after a tap.
     * [token] is unique to every accepted [tap] — never repeated — so a
     * `LaunchedEffect` keyed on the whole value restarts its delay on every
     * tap, mirroring iOS's `advanceTask?.cancel(); advanceTask = afterDelay { ... }`
     * pair, which *both* branches of `FlashCardsView.tap(_:)` perform.
     */
    data class PendingAction(val kind: Kind, val token: Int) {
        enum class Kind {
            /** A correct tap: start a fresh round. */
            Advance,

            /** A non-matching tap: ask the same question again. Never
             * "try again" — see [tap]. */
            Repeat,
        }
    }

    private val roundState = MutableStateFlow<FlashRound?>(null)

    /** The round on screen, or `null` before the first one and when the pool
     * could not make a question — see [FlashRound.create]. */
    val round: StateFlow<FlashRound?> = roundState.asStateFlow()

    private val bounceState = MutableStateFlow<Bounce?>(null)

    /** The tile currently mid-bounce, or `null`. */
    val bounce: StateFlow<Bounce?> = bounceState.asStateFlow()

    private val solvedState = MutableStateFlow<String?>(null)

    /** The [EmojiEntry.id] of the correctly-tapped tile during the
     * [ADVANCE_DELAY_MS] window before the next round, or `null`. */
    val solvedId: StateFlow<String?> = solvedState.asStateFlow()

    private val advancingState = MutableStateFlow(false)

    /** True only between a correct tap and the next round — the window in
     * which every choice is disabled. A non-matching tap never sets this;
     * see [tap]. */
    val isAdvancing: StateFlow<Boolean> = advancingState.asStateFlow()

    private val pendingActionState = MutableStateFlow<PendingAction?>(null)

    /** The delayed follow-up `FlashCardsScreen` owes the current tap, or
     * `null` when nothing is pending. */
    val pendingAction: StateFlow<PendingAction?> = pendingActionState.asStateFlow()

    private var actionCounter = 0

    /**
     * A fresh round drawn from [pool] in [language], never repeating the
     * outgoing round's own target — mirrors iOS `FlashCardsView.nextRound()`.
     * [round] is `null` afterward only when [pool] cannot make a question
     * (fewer than two distinct words) — see [FlashRound.create] — the same
     * "no round rather than a one-tile one" call iOS makes.
     *
     * Also the one place [isAdvancing]/[solvedId]/[pendingAction] are put
     * back to their resting state, so a caller never has to remember to
     * clear them separately before starting over.
     */
    fun startRound(pool: List<EmojiEntry>, language: Language, random: Random = Random.Default) {
        clearPendingTap()
        roundState.value = FlashRound.create(
            pool = pool,
            language = language,
            avoiding = roundState.value?.target,
            random = random,
        )
    }

    /**
     * Throws the round away entirely, back to the state before the first one.
     * Called when Flash Cards is opened afresh from the launcher, so that
     * re-entering asks a new question rather than resuming a stale one — the
     * Android stand-in for iOS's view-scoped `@State` dying on a mode switch,
     * which a process-scoped view model does not do on its own. See
     * `CountScreen`'s own doc for the same trade-off in Count mode.
     */
    fun reset() {
        clearPendingTap()
        roundState.value = null
    }

    /**
     * Everything iOS `FlashCardsView.silence()` puts back apart from the
     * mascot and the speech engine (which belong to the caller): no tile is
     * solved, nothing is advancing, and the delayed follow-up a tap armed is
     * abandoned. The round itself is kept — a language change re-asks the
     * *same* question in the new language rather than pulling the emojis out
     * from under a child mid-choice.
     */
    fun clearPendingTap() {
        advancingState.value = false
        solvedState.value = null
        pendingActionState.value = null
    }

    /** What a tap resolved to. The caller (`FlashCardsScreen`) decides the
     * mood/speech/haptic reaction to each case; this class only tracks state. */
    sealed interface TapOutcome {
        val entry: EmojiEntry

        data class Correct(override val entry: EmojiEntry) : TapOutcome

        /**
         * The child touched something that is not the answer. Named for what
         * it *is* rather than "wrong": the tile says its own name and the
         * question comes back, so nothing about this outcome is a failure —
         * `CLAUDE.md` rule 4, and iOS `FlashCardsView.tap`'s own comment.
         */
        data class Other(override val entry: EmojiEntry) : TapOutcome
    }

    /**
     * One tile pressed. `null` when the tap is refused outright: no round on
     * screen, or a correct answer is already being celebrated
     * ([isAdvancing]) — mirrors iOS `FlashCardsView.tap`'s
     * `guard let round, !isAdvancing else { return }`.
     *
     * A **non-matching** tap does not set [isAdvancing] — mirrors iOS
     * exactly: only a correct tap opens the disabled/celebrating window. A
     * child left free to keep exploring the other choices while the tile he
     * touched says its own name is the actual "no failure state" behaviour
     * this mode promises, not a guess: `FlashCardsView.tap`'s `else` branch
     * never touches `isAdvancing`.
     */
    fun tap(entry: EmojiEntry): TapOutcome? {
        val current = roundState.value ?: return null
        if (advancingState.value) return null

        actionCounter += 1
        bounceState.value = Bounce(entry.id, actionCounter)

        return if (current.isCorrect(entry)) {
            solvedState.value = entry.id
            advancingState.value = true
            pendingActionState.value = PendingAction(PendingAction.Kind.Advance, actionCounter)
            TapOutcome.Correct(entry)
        } else {
            pendingActionState.value = PendingAction(PendingAction.Kind.Repeat, actionCounter)
            TapOutcome.Other(entry)
        }
    }

    /** Ends a bounce, once the caller's own [BOUNCE_HOLD_MS] timer has run.
     * Guarded on [token] so a stale timer for a bounce that has already been
     * superseded by a later tap cannot clear the new one out from under it. */
    fun clearBounce(token: Int) {
        if (bounceState.value?.token == token) bounceState.value = null
    }

    /**
     * Drops [pendingAction] once `FlashCardsScreen` has carried it out —
     * called at the end of its `LaunchedEffect`, whichever
     * [PendingAction.Kind] it was, so a later, unrelated recomposition does
     * not find a stale action and repeat it. Guarded on [token] so a late
     * clear for an action that was already superseded (its own
     * `LaunchedEffect` cancelled mid-delay by a fresh tap) cannot drop the
     * *new* one.
     */
    fun clearPendingAction(token: Int) {
        if (pendingActionState.value?.token == token) pendingActionState.value = null
    }
}
