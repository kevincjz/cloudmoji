package app.cloudmoji.android.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Schedules the delayed, one-shot state changes [WordsViewModel] arms: the
 * word bubble's hold and a tapped tile's bounce. Kept as a seam — rather than
 * [WordsViewModel] calling `kotlinx.coroutines.delay` itself — so tests can
 * fire the scheduled work deterministically instead of waiting on a real
 * clock, the same shape as `SpeechWatchdogScheduler` and `MascotScheduler`.
 */
interface WordsScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): WordsScheduleHandle
}

/** A single scheduled callback. [cancel] is safe to call more than once, and
 * after the action has already fired. */
interface WordsScheduleHandle {
    fun cancel()
}

/** Production [WordsScheduler]: one coroutine per scheduled callback,
 * cancelled via [kotlinx.coroutines.Job.cancel]. [scope] is a composition-root
 * choice, the same as `CoroutineMascotScheduler`/`CoroutineSpeechWatchdogScheduler`. */
class CoroutineWordsScheduler(private val scope: CoroutineScope) : WordsScheduler {
    override fun schedule(delayMillis: Long, action: () -> Unit): WordsScheduleHandle {
        val job = scope.launch {
            delay(delayMillis)
            action()
        }
        return object : WordsScheduleHandle {
            override fun cancel() {
                job.cancel()
            }
        }
    }
}

/**
 * Words mode's own state, host-testable independent of Compose: the typing
 * row, the word bubble's lifecycle, and which tile is bouncing.
 *
 * Deliberately does **not** own the mascot's mood (that is
 * `MascotMoodMachine`, shared across every screen that reacts to taps and
 * speech), the emoji sections (a pure derivation — see [buildSections] — with
 * nothing to hold as state), or speech itself (`SpeechController`). This
 * class is the part of `WordsView.swift` that is specific to Words: the
 * typed row and what floats above it.
 *
 * Ported from `WordsView.swift`'s `typed`/`bubble`/`bouncingID` state and the
 * functions that mutate them (`tap`, `showBubble`, `bounce`, `replayAll`'s
 * per-item callback, the typing row's controls, and the `onChange(of:
 * effectiveLanguage)` handler).
 *
 * **Threading: not thread-safe, by design** — the same confined-to-one-thread
 * contract as `SpeechController`/`MascotMoodMachine`. Every public call, and
 * every callback [scheduler] invokes, must arrive on one thread (normally the
 * app's main/UI thread, which is what a caller driven from Compose gets for
 * free).
 */
class WordsViewModel(private val scheduler: WordsScheduler) {
    companion object {
        /** PRD: at most 50 emojis in the typing row, oldest dropped first.
         * Matches iOS `TypingRow.maxTyped` / the web's `MAX_TYPED`. */
        const val MAX_TYPED = 50

        /** The bubble's own `wordFloat` lifetime (`WordBubbleMetrics.lifetime`
         * on iOS, the 2200ms timeout on the web) — both the owner's cue to
         * drop it and the animation's own duration, which must stay the same
         * number or the fade finishes into a jump cut. */
        const val BUBBLE_HOLD_MS = 2200L

        /** `setTimeout(() => setBounceIdx(null), 400)` / iOS `EmojiTileMetrics.bounceDuration`. */
        const val BOUNCE_HOLD_MS = 400L
    }

    private var nextId = 0L
    private var jumpToken = 0

    private val typedState = MutableStateFlow<List<TypedEmoji>>(emptyList())

    /** The typed row, oldest first, capped at [MAX_TYPED]. */
    val typed: StateFlow<List<TypedEmoji>> = typedState.asStateFlow()

    private val bubbleState = MutableStateFlow<TypedEmoji?>(null)

    /** The word bubble currently floating above the grid, or `null` when
     * nothing has been tapped recently enough to still be showing one. */
    val bubble: StateFlow<TypedEmoji?> = bubbleState.asStateFlow()

    private val bouncingState = MutableStateFlow<String?>(null)

    /** The [EmojiEntry.id] of the tile currently mid-bounce, or `null`. */
    val bouncingId: StateFlow<String?> = bouncingState.asStateFlow()

    private var bubbleHandle: WordsScheduleHandle? = null
    private var bounceHandle: WordsScheduleHandle? = null

    /**
     * Bumped every time [showBubble]/[bounce]/[clear] supersede their own
     * previous timer. The guard against a stale callback resuming is this
     * counter, not [WordsScheduleHandle.cancel] — mirroring
     * `MascotMoodMachine`'s `excitedGeneration`, which exists for exactly the
     * same reason: a real scheduler's cancellation is not guaranteed to land
     * before an already-in-flight callback runs, so correctness cannot
     * depend on it alone.
     */
    private var bubbleGeneration = 0
    private var bounceGeneration = 0

    /**
     * A child tapped a grid emoji: appends it to the row (oldest dropped past
     * [MAX_TYPED] — `suffix`, not `prefix`, so the row keeps moving instead
     * of freezing on the session's first fifty taps), shows the bubble, and
     * bounces the tile. Returns the freshly typed item so the caller can hand
     * its word to speech and to the mascot's mood machine — this class has no
     * opinion about either.
     */
    fun tapEmoji(entry: EmojiEntry, word: String): TypedEmoji {
        val item = TypedEmoji(id = nextId++, emoji = entry.emoji, word = word)
        typedState.value = (typedState.value + item).takeLast(MAX_TYPED)
        showBubble(item)
        bounce(entry.id)
        return item
    }

    /**
     * A category chip was tapped: shows the bubble for the category's own
     * icon and label (not appended to the row — a category is not a typed
     * emoji) and returns a fresh [SectionJump] so the grid can scroll to it.
     * The token always advances, so tapping the chip the child is already
     * looking at still scrolls back to it rather than being a no-op.
     */
    fun tapCategory(tab: CategoryTab, label: String): SectionJump {
        showBubble(TypedEmoji(id = nextId++, emoji = tab.icon, word = label))
        jumpToken += 1
        return SectionJump(id = tab.id, token = jumpToken)
    }

    /** Removes the last typed emoji. A no-op on an empty row — deleting
     * nothing is not a failure state, just nothing to do. */
    fun deleteLast() {
        if (typedState.value.isEmpty()) return
        typedState.value = typedState.value.dropLast(1)
    }

    /** Empties the row and drops whatever bubble is currently showing —
     * mirrors iOS `WordsView`'s `onClear`, which cancels the bubble's own
     * timer rather than letting a stale one null it out again later. */
    fun clear() {
        typedState.value = emptyList()
        bubbleGeneration += 1
        bubbleHandle?.cancel()
        bubbleState.value = null
    }

    /**
     * Re-shows the bubble for [item] — a re-tap of an already-typed emoji, or
     * one step of a replay sequence. The row itself is untouched: a replay
     * does not re-type anything, and a re-tap is not a new tap.
     */
    fun showBubble(item: TypedEmoji) {
        bubbleState.value = item
        bubbleGeneration += 1
        val token = bubbleGeneration
        bubbleHandle?.cancel()
        bubbleHandle = scheduler.schedule(BUBBLE_HOLD_MS) {
            // A tap/replay step that landed after this one was scheduled
            // already superseded it — see the class doc on `bubbleGeneration`.
            if (token != bubbleGeneration) return@schedule
            bubbleState.value = null
        }
    }

    /**
     * A language change must keep every already-typed emoji's word current —
     * otherwise a repeat tap, or a replay, speaks the emoji in its old
     * language's phonetics through the new language's voice. [wordFor] looks
     * the word up fresh (`EmojiRepository.entry(emoji)?.word(newLanguage)`)
     * rather than carrying the old one forward — mirrors iOS `WordsView`'s
     * `onChange(of: model.effectiveLanguage)`. An emoji [wordFor] cannot
     * resolve (should not happen — every typed emoji came from the
     * catalogue) keeps its previous word rather than being dropped from the
     * row, the same "never a failure state" call this app makes everywhere.
     */
    fun onLanguageChanged(wordFor: (String) -> String?) {
        typedState.value = typedState.value.map { item ->
            val fresh = wordFor(item.emoji) ?: return@map item
            item.copy(word = fresh)
        }
    }

    private fun bounce(entryId: String) {
        bouncingState.value = entryId
        bounceGeneration += 1
        val token = bounceGeneration
        bounceHandle?.cancel()
        bounceHandle = scheduler.schedule(BOUNCE_HOLD_MS) {
            if (token != bounceGeneration) return@schedule
            bouncingState.value = null
        }
    }
}
