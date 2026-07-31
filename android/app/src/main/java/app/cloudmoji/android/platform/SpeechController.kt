package app.cloudmoji.android.platform

import app.cloudmoji.android.model.Language
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One item in a spoken sequence — mirrors iOS CloudmojiCore's `SpeechItem`. */
data class SpeechItem(
    val text: String,
    val onSpeak: (() -> Unit)? = null,
)

/**
 * Schedules the delayed one-shot recovery work [SpeechController] arms per
 * sequence item — see [SpeechController.watchdogIntervalMs]. Kept as a seam
 * (rather than [SpeechController] calling `kotlinx.coroutines.delay` itself)
 * so tests can fire the scheduled work deterministically instead of waiting
 * on a real clock. Production code supplies [CoroutineSpeechWatchdogScheduler].
 */
interface SpeechWatchdogScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): SpeechWatchdogHandle
}

/** A single scheduled watchdog. [cancel] is safe to call more than once, and
 * after the action has already fired. */
interface SpeechWatchdogHandle {
    fun cancel()
}

/** Production [SpeechWatchdogScheduler]: one coroutine per scheduled
 * watchdog, cancelled via [kotlinx.coroutines.Job.cancel]. [scope] decides
 * the dispatcher and lifetime — the app-wiring call site owns that choice,
 * not this class, so it stays usable from a plain background scope as well
 * as a UI-lifecycle-scoped one. */
class CoroutineSpeechWatchdogScheduler(private val scope: CoroutineScope) : SpeechWatchdogScheduler {
    override fun schedule(delayMillis: Long, action: () -> Unit): SpeechWatchdogHandle {
        val job = scope.launch {
            delay(delayMillis)
            action()
        }
        return object : SpeechWatchdogHandle {
            override fun cancel() {
                job.cancel()
            }
        }
    }
}

/**
 * Speaks single words and sequences, and can genuinely cancel either.
 *
 * Ported from iOS CloudmojiCore's `SpeechController`. Sequences chain on the
 * engine's finish callback rather than on a fixed-delay timer — a timer-based
 * queue keeps firing after mute, after a language change, and after the
 * round is replaced, because each callback closes over stale state and
 * nothing can call it back. That is exactly what happened on the web before
 * `useTTS` was fixed to chain on `onend` instead.
 *
 * Pure JVM: [engine] and [scheduler] are the only two seams to the platform,
 * both interfaces, so this class and every rule it encodes — cancellation,
 * the watchdog, mute — are host-testable with fakes.
 *
 * **Threading: not thread-safe, by design.** [generation], the per-sequence
 * `index`, and [watchdogHandle] are plain, unsynchronized fields — every
 * public call (`speak`/`speakSequence`/`cancelAll`), and every callback that
 * flows back in through [engine]'s `onFinish`, must arrive on one confined
 * thread (normally the app's main/UI thread). This is the same assumption
 * iOS gets for free from `SpeechController` being `@MainActor`-isolated at
 * compile time; Kotlin has no equivalent, so the confinement is a runtime
 * contract instead. [AndroidSpeechEngine] is the piece responsible for
 * upholding it in production — `TextToSpeech`'s async callbacks can arrive
 * off-thread, and it routes every one of them through an injected
 * [CallbackPoster] before they ever reach this class. This class does no
 * posting or locking of its own; it trusts the engine to have already
 * delivered the callback on the right thread.
 */
class SpeechController(
    private val resolver: VoiceResolver,
    private val engine: SpeechEngine,
    /**
     * No default value, unlike [isMuted] below: which [CoroutineScope]/
     * dispatcher backs a real [CoroutineSpeechWatchdogScheduler] is a
     * composition-root decision (and, per this class's Threading section,
     * a thread-confinement-relevant one), so it is made explicitly by
     * whichever task wires the production app together rather than
     * silently by a default argument here.
     */
    private val scheduler: SpeechWatchdogScheduler,
    /**
     * Interface point for Task 3's `Settings.muted`: read fresh on every call
     * rather than pushed in, so this class never depends on DataStore or a
     * settings type, and never acts on a stale snapshot taken at
     * construction time. Defaults to never-muted for callers (and previews)
     * with no sound setting to honor.
     */
    private val isMuted: () -> Boolean = { false },
) {
    companion object {
        /**
         * Speech rate as a **fraction of the engine's normal speed** — 0.85
         * is 15% slower than natural, which is what a toddler needs to catch
         * a new word. This is the Web Speech API's scale, where 1.0 is
         * normal, and it is the number `src/hooks/useTTS.ts` uses. See
         * `AndroidSpeechEngine` for how this maps onto
         * `android.speech.tts.TextToSpeech` (unlike iOS's adapter, unchanged).
         */
        const val RATE: Float = 0.85f

        /** Pitch multiplier. 1.0 is natural on every platform, so this one
         * carries across unchanged everywhere. */
        const val PITCH: Float = 1.1f

        /** iOS's default: long enough for a real word, short enough that a
         * dropped completion callback does not stall a sequence for long. */
        const val DEFAULT_WATCHDOG_MS: Long = 6000
    }

    /** How long to wait for the engine to report finishing before advancing
     * anyway. A real TTS engine can drop its completion callback on a focus
     * change, a route change, or a service hiccup, which would otherwise
     * strand the rest of a sequence. */
    var watchdogIntervalMs: Long = DEFAULT_WATCHDOG_MS

    /** Bumped on every cancel. Queued work compares against it and bails. */
    private var generation = 0
    private var watchdogHandle: SpeechWatchdogHandle? = null

    private val speakingState = MutableStateFlow(false)

    /** Whether something is currently being spoken — the stream the mascot
     * observes to move between its happy and speaking moods. Flips to `true`
     * the moment an utterance is handed to the engine and back to `false`
     * when it (or the whole sequence) finishes or is cancelled. */
    val isSpeaking: StateFlow<Boolean> = speakingState.asStateFlow()

    fun cancelAll() {
        generation += 1
        watchdogHandle?.cancel()
        watchdogHandle = null
        engine.stop()
        speakingState.value = false
    }

    /**
     * Ends this controller's engine for good — see [SpeechEngine.shutdown].
     * Cancels whatever is in flight first (the same as [cancelAll]), so a
     * shutdown mid-utterance does not leave a dangling watchdog behind.
     * This controller must not be used again afterward — there is no way
     * back from a shut-down [SpeechEngine].
     */
    fun shutdown() {
        cancelAll()
        engine.shutdown()
    }

    /**
     * Speaks one word. [onFinish] runs when the engine reports completion,
     * and is dropped if the utterance is cancelled first — the mascot uses
     * it to return from speaking to happy.
     */
    fun speak(text: String, language: Language, onFinish: (() -> Unit)? = null) {
        // An empty request, or a muted one, is itself a cancellation: it
        // means "nothing should be speaking now" (a cleared typing row, a
        // category filter with nothing in it, sound turned off mid-word).
        // Cancel unconditionally, before the early return, or whatever was
        // already playing keeps going.
        cancelAll()
        if (text.isEmpty() || isMuted()) return
        val token = generation
        speakingState.value = true
        emit(text, language) {
            // A late callback for an utterance that was already stopped must
            // not tell the mascot this word finished — by then something
            // else is speaking, or nothing is.
            if (token != generation) return@emit
            speakingState.value = false
            onFinish?.invoke()
        }
    }

    fun speakSequence(items: List<SpeechItem>, language: Language) {
        // Same reasoning as `speak`: cancel first, then bail on empty/muted.
        cancelAll()
        if (items.isEmpty() || isMuted()) return
        val token = generation
        var index = 0

        fun step() {
            // Re-checked on every entry — including the ones reached via a
            // reentrant call from `onSpeak` below — because `step()` is the
            // whole cancellation-correctness surface of this controller. A
            // mid-sequence mute is caught here too, matching the web's
            // per-step `mutedRef.current` check: muting must silence the
            // rest of a sequence, not just block a fresh one from starting.
            if (token != generation || isMuted() || index >= items.size) {
                if (token == generation) speakingState.value = false
                return
            }
            val item = items[index]
            index += 1
            item.onSpeak?.invoke()
            // `onSpeak` can reentrantly call back into this controller (e.g.
            // a milestone celebration firing `speak`), which bumps
            // `generation`. Re-check before emitting, or the item that
            // triggered the cancellation still gets forwarded to the engine
            // and speaks after whatever superseded it.
            if (token != generation) return

            var advanced = false
            val advance = fun() {
                // Whichever arrives first — the engine's callback or the
                // watchdog — moves the chain on, and the other becomes a
                // no-op. The sole guard against a cancelled chain resuming is
                // the generation check: a late engine callback for an
                // utterance that was already stopped must not step into the
                // next item.
                if (advanced || token != generation) return
                advanced = true
                watchdogHandle?.cancel()
                watchdogHandle = null
                step()
            }

            // Armed before the hand-off, not after: an engine that reports
            // finishing synchronously would otherwise recurse into the next
            // item, arm its watchdog, and then have this frame cancel it on
            // the way back out.
            watchdogHandle?.cancel()
            watchdogHandle = scheduler.schedule(watchdogIntervalMs) { advance() }

            speakingState.value = true
            emit(item.text, language, advance)
        }
        step()
    }

    private fun emit(text: String, language: Language, onFinish: () -> Unit) {
        val tag = resolver.speechTag(language)
        val voice = resolver.pick(engine.voices(), language)
        engine.speak(
            SpeechUtterance(
                text = text,
                // Keep the tag consistent with the chosen voice, or some
                // engines re-resolve and ignore the explicit voice.
                languageTag = voice?.lang ?: tag,
                voice = voice,
                onFinish = onFinish,
            ),
        )
    }
}
