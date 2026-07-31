package app.cloudmoji.android.platform

import app.cloudmoji.android.data.EmojiRepositoryLoader
import app.cloudmoji.android.data.TestCatalog
import app.cloudmoji.android.model.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Ported from `ios/CloudmojiCore/Tests/CloudmojiCoreTests/SpeechControllerTests.swift`.
 * The watchdog tests replace iOS's real `Task.sleep` waits with
 * [FakeSpeechWatchdogScheduler.fireLatest]/[FakeSpeechWatchdogScheduler.fire]
 * — deterministic and instant, rather than 50-200ms of real sleeping per
 * test. The mute tests (last section) are net new: the Android brief asks
 * `SpeechController` itself to honor an injected mute provider, which iOS's
 * controller does not do (iOS checks `settings.muted` at each call site).
 */
class SpeechControllerTest {

    /** Records what would have been spoken, and lets a test decide when an
     * utterance "finishes" — so queue behaviour is deterministic. */
    private class FakeSpeechEngine : SpeechEngine {
        data class Spoken(val text: String, val lang: String)

        val spoken = mutableListOf<Spoken>()
        var stopCount = 0
            private set
        var shutdownCount = 0
            private set
        private var onFinish: (() -> Unit)? = null

        /** The finish callback belonging to whatever was in flight the
         * moment [stop] was last called. A real engine callback can still
         * land after `stop()` returns — [finishLate] models that race
         * instead of pretending `stop()` makes a pending callback vanish. */
        private var lateFinish: (() -> Unit)? = null

        override fun voices(): List<VoiceDescribing> = listOf(FakeVoice(lang = "en-US", name = "Samantha"))

        override fun speak(utterance: SpeechUtterance) {
            spoken += Spoken(utterance.text, utterance.languageTag)
            onFinish = utterance.onFinish
        }

        override fun stop() {
            stopCount += 1
            lateFinish = onFinish
            onFinish = null
        }

        override fun shutdown() {
            shutdownCount += 1
            lateFinish = onFinish
            onFinish = null
        }

        /** Simulates the engine finishing the utterance that is currently
         * speaking — i.e. no [stop] has intervened since the matching
         * [speak]. */
        fun finishCurrent() {
            val callback = onFinish
            onFinish = null
            callback?.invoke()
        }

        /** Simulates a delegate callback arriving for the utterance that was
         * in flight at the moment [stop] was last called — after the fact,
         * regardless of whatever has been spoken since. */
        fun finishLate() {
            val callback = lateFinish
            lateFinish = null
            callback?.invoke()
        }

        private data class FakeVoice(override val lang: String, override val name: String) : VoiceDescribing
    }

    /** Captures every scheduled watchdog rather than actually waiting, so a
     * test can fire — or deliberately re-fire a stale, already-superseded —
     * one on demand. */
    private class FakeSpeechWatchdogScheduler : SpeechWatchdogScheduler {
        private class Entry(val action: () -> Unit) : SpeechWatchdogHandle {
            override fun cancel() = Unit // see SpeechControllerTest's doc: cancellation
            // correctness is `SpeechController`'s own `advanced`/generation
            // guard, not the scheduler's -- this fake deliberately does NOT
            // suppress a cancelled entry's action, so tests can prove that
            // guard actually holds by firing a stale one anyway.
        }

        private val scheduled = mutableListOf<Entry>()

        override fun schedule(delayMillis: Long, action: () -> Unit): SpeechWatchdogHandle {
            val entry = Entry(action)
            scheduled += entry
            return entry
        }

        /** Fires the most recently scheduled watchdog. */
        fun fireLatest() = scheduled.last().action()

        /** Fires the watchdog scheduled at [index], in scheduling order —
         * for firing an old, already-superseded one on purpose. */
        fun fire(index: Int) = scheduled[index].action()
    }

    private data class Fixture(
        val controller: SpeechController,
        val engine: FakeSpeechEngine,
        val scheduler: FakeSpeechWatchdogScheduler,
    )

    private fun makeController(isMuted: () -> Boolean = { false }): Fixture {
        val engine = FakeSpeechEngine()
        val scheduler = FakeSpeechWatchdogScheduler()
        val resolver = VoiceResolver(EmojiRepositoryLoader.fromJson(TestCatalog.json).languages)
        val controller = SpeechController(resolver, engine, scheduler, isMuted)
        return Fixture(controller, engine, scheduler)
    }

    // MARK: - Cancellation and queueing

    @Test
    fun `speaking cancels whatever came before`() {
        val (controller, engine, _) = makeController()
        controller.speak("apple", Language.English)
        controller.speak("banana", Language.English)
        assertEquals(listOf("apple", "banana"), engine.spoken.map { it.text })
        assertEquals(2, engine.stopCount)
    }

    @Test
    fun `a sequence advances only when the engine reports finished`() {
        val (controller, engine, _) = makeController()
        controller.speakSequence(
            listOf(SpeechItem("one"), SpeechItem("two"), SpeechItem("three")),
            Language.English,
        )
        assertEquals(listOf("one"), engine.spoken.map { it.text })
        engine.finishCurrent()
        assertEquals(listOf("one", "two"), engine.spoken.map { it.text })
        engine.finishCurrent()
        assertEquals(listOf("one", "two", "three"), engine.spoken.map { it.text })
    }

    @Test
    fun `a late finish callback from a cancelled utterance cannot resurrect the sequence`() {
        val (controller, engine, _) = makeController()
        val seen = mutableListOf<String>()
        controller.speakSequence(
            listOf(
                SpeechItem("one"),
                SpeechItem("two") { seen += "two" },
                SpeechItem("three"),
            ),
            Language.English,
        )
        controller.cancelAll()
        engine.finishLate() // a late callback from the utterance in flight when cancelAll() ran
        assertEquals("nothing may speak after cancelAll", listOf("one"), engine.spoken.map { it.text })
        assertTrue("onSpeak must not fire for an item in a chain that was already cancelled", seen.isEmpty())
    }

    @Test
    fun `onSpeak fires for each item as it starts`() {
        val (controller, engine, _) = makeController()
        val seen = mutableListOf<String>()
        controller.speakSequence(
            listOf(
                SpeechItem("one") { seen += "one" },
                SpeechItem("two") { seen += "two" },
            ),
            Language.English,
        )
        assertEquals(listOf("one"), seen)
        engine.finishCurrent()
        assertEquals(listOf("one", "two"), seen)
    }

    @Test
    fun `an onSpeak that reentrantly cancels stops its own item from reaching the engine`() {
        val (controller, engine, _) = makeController()
        controller.speakSequence(
            listOf(
                SpeechItem("one"),
                SpeechItem("TWO") {
                    // A milestone celebration or similar reentrantly
                    // speaking over the sequence mid-item.
                    controller.speak("great job", Language.English)
                },
            ),
            Language.English,
        )
        assertEquals(listOf("one"), engine.spoken.map { it.text })
        engine.finishCurrent() // advances to "TWO", whose onSpeak cancels it
        assertEquals(
            "the superseded item must not be forwarded to the engine after its own onSpeak cancelled it",
            listOf("one", "great job"),
            engine.spoken.map { it.text },
        )
    }

    @Test
    fun `an empty sequence speaks nothing`() {
        val (controller, engine, _) = makeController()
        controller.speakSequence(emptyList(), Language.English)
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun `an empty speakSequence still cancels whatever was already playing`() {
        val (controller, engine, _) = makeController()
        controller.speakSequence(
            listOf(SpeechItem("one"), SpeechItem("two"), SpeechItem("three")),
            Language.English,
        )
        controller.speakSequence(emptyList(), Language.English) // e.g. clearing the typing row
        engine.finishCurrent() // the old sequence's pending callback, if still wired up
        assertEquals(
            "replacing a round with an empty one must stop the old one",
            listOf("one"),
            engine.spoken.map { it.text },
        )
        assertEquals("an empty request is itself a cancellation", 2, engine.stopCount)
    }

    @Test
    fun `an empty speak still cancels whatever was already playing`() {
        val (controller, engine, _) = makeController()
        controller.speak("apple", Language.English)
        controller.speak("", Language.English) // e.g. an unmapped or blank word
        assertEquals(listOf("apple"), engine.spoken.map { it.text })
        assertEquals("an empty request is itself a cancellation", 2, engine.stopCount)
    }

    @Test
    fun `speak interrupts an in-flight sequence`() {
        val (controller, engine, _) = makeController()
        controller.speakSequence(listOf(SpeechItem("one"), SpeechItem("two")), Language.English)
        controller.speak("apple", Language.English)
        engine.finishLate() // late callback from the sequence's first item, now stale
        assertEquals(
            "the sequence must not resume once speak() has taken over",
            listOf("one", "apple"),
            engine.spoken.map { it.text },
        )
    }

    @Test
    fun `a sequence interrupts an in-flight single speak`() {
        val (controller, engine, _) = makeController()
        controller.speak("apple", Language.English)
        controller.speakSequence(listOf(SpeechItem("one"), SpeechItem("two")), Language.English)
        assertEquals(listOf("apple", "one"), engine.spoken.map { it.text })
        engine.finishCurrent()
        assertEquals(listOf("apple", "one", "two"), engine.spoken.map { it.text })
    }

    @Test
    fun `rate and pitch match the product spec`() {
        assertEquals(0.85f, SpeechController.RATE, 0f)
        assertEquals(1.1f, SpeechController.PITCH, 0f)
    }

    @Test
    fun `a single word reports completion`() {
        val (controller, engine, _) = makeController()
        var finished = false
        controller.speak("apple", Language.English) { finished = true }
        assertFalse(finished)
        engine.finishCurrent()
        assertTrue("the mascot cannot return to happy without this", finished)
    }

    @Test
    fun `a cancelled single word does not report completion`() {
        val (controller, engine, _) = makeController()
        var finished = false
        controller.speak("apple", Language.English) { finished = true }
        controller.cancelAll()
        engine.finishLate()
        assertFalse(finished)
    }

    // MARK: - Watchdog

    @Test
    fun `a sequence advances when the engine never reports finishing`() {
        val (controller, engine, scheduler) = makeController()
        controller.speakSequence(listOf(SpeechItem("one"), SpeechItem("two")), Language.English)
        assertEquals(listOf("one"), engine.spoken.map { it.text })
        // The engine never calls back — a real TTS engine can drop its
        // completion callback on a focus change or a service hiccup.
        scheduler.fireLatest()
        assertEquals(
            "a dropped completion callback must not strand the rest of the sequence",
            listOf("one", "two"),
            engine.spoken.map { it.text },
        )
    }

    @Test
    fun `the watchdog does not double-advance when the engine does report`() {
        val (controller, engine, scheduler) = makeController()
        controller.speakSequence(
            listOf(SpeechItem("one"), SpeechItem("two"), SpeechItem("three")),
            Language.English,
        )
        engine.finishCurrent() // "one" genuinely finishes, disarming its own watchdog
        assertEquals(listOf("one", "two"), engine.spoken.map { it.text })
        // Firing "one"'s stale watchdog anyway (index 0) must be a no-op:
        // SpeechController's own guard, not the scheduler, is what protects
        // against this.
        scheduler.fire(0)
        assertEquals(listOf("one", "two"), engine.spoken.map { it.text })
    }

    @Test
    fun `an engine report disarms that item's watchdog`() {
        val (controller, engine, scheduler) = makeController()
        controller.speakSequence(
            listOf(SpeechItem("one"), SpeechItem("two"), SpeechItem("three"), SpeechItem("four")),
            Language.English,
        )
        engine.finishCurrent() // "one" genuinely finished, so its watchdog (index 0) is moot
        assertEquals(listOf("one", "two"), engine.spoken.map { it.text })
        scheduler.fire(0)
        assertEquals(
            "an item that reported finishing must not be advanced past a second time",
            listOf("one", "two"),
            engine.spoken.map { it.text },
        )
        // "two"'s watchdog (the latest one armed) is what recovers a
        // genuinely stalled item.
        scheduler.fireLatest()
        assertEquals(listOf("one", "two", "three"), engine.spoken.map { it.text })
    }

    // MARK: - isSpeaking (the mascot's observer stream)

    @Test
    fun `isSpeaking flips true when a word starts and false when it finishes`() {
        val (controller, engine, _) = makeController()
        assertFalse(controller.isSpeaking.value)
        controller.speak("apple", Language.English)
        assertTrue(controller.isSpeaking.value)
        engine.finishCurrent()
        assertFalse(controller.isSpeaking.value)
    }

    @Test
    fun `isSpeaking returns to false when cancelled mid-utterance`() {
        val (controller, _, _) = makeController()
        controller.speak("apple", Language.English)
        assertTrue(controller.isSpeaking.value)
        controller.cancelAll()
        assertFalse(controller.isSpeaking.value)
    }

    @Test
    fun `isSpeaking stays true between items in a sequence and drops after the last`() {
        val (controller, engine, _) = makeController()
        controller.speakSequence(listOf(SpeechItem("one"), SpeechItem("two")), Language.English)
        assertTrue(controller.isSpeaking.value)
        engine.finishCurrent()
        assertTrue("still mid-sequence", controller.isSpeaking.value)
        engine.finishCurrent()
        assertFalse("the sequence is over", controller.isSpeaking.value)
    }

    // MARK: - Mute (Task 3's sound setting, honored via an injected provider)

    @Test
    fun `speak does nothing while muted`() {
        val (controller, engine, _) = makeController(isMuted = { true })
        controller.speak("apple", Language.English)
        assertTrue(engine.spoken.isEmpty())
        assertFalse(controller.isSpeaking.value)
    }

    @Test
    fun `speak while muted still cancels whatever was already playing`() {
        var muted = false
        val (controller, engine, _) = makeController(isMuted = { muted })
        controller.speak("apple", Language.English)
        assertEquals(listOf("apple"), engine.spoken.map { it.text })
        muted = true
        controller.speak("banana", Language.English)
        assertEquals(
            "a muted request must never reach the engine",
            listOf("apple"),
            engine.spoken.map { it.text },
        )
        assertEquals("a muted request is itself a cancellation, like an empty one", 2, engine.stopCount)
    }

    @Test
    fun `speakSequence does nothing while muted`() {
        val (controller, engine, _) = makeController(isMuted = { true })
        controller.speakSequence(listOf(SpeechItem("one"), SpeechItem("two")), Language.English)
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun `a sequence stops advancing once muted mid-sequence`() {
        var muted = false
        val (controller, engine, _) = makeController(isMuted = { muted })
        controller.speakSequence(
            listOf(SpeechItem("one"), SpeechItem("two"), SpeechItem("three")),
            Language.English,
        )
        engine.finishCurrent() // advances to "two"
        assertEquals(listOf("one", "two"), engine.spoken.map { it.text })
        muted = true
        engine.finishCurrent() // "two" reports finished; muted must stop the chain before "three"
        assertEquals(
            "muting mid-sequence must silence the rest of it, like the web's per-step mute check",
            listOf("one", "two"),
            engine.spoken.map { it.text },
        )
        assertFalse(controller.isSpeaking.value)
    }

    // MARK: - Shutdown (the release path `AndroidSpeechEngine`/`CloudmojiApplication` need)

    @Test
    fun `shutdown cancels whatever is playing before releasing the engine`() {
        val (controller, engine, _) = makeController()
        controller.speak("apple", Language.English) // itself calls cancelAll() once, per `speak`'s own contract
        val stopCountBeforeShutdown = engine.stopCount

        controller.shutdown()

        assertFalse("a shutdown mid-utterance must leave isSpeaking false", controller.isSpeaking.value)
        assertEquals(
            "shutdown's own cancelAll must still call stop()",
            stopCountBeforeShutdown + 1,
            engine.stopCount,
        )
        assertEquals(1, engine.shutdownCount)
    }

    @Test
    fun `a late finish callback after shutdown cannot report completion`() {
        val (controller, engine, _) = makeController()
        var finished = false
        controller.speak("apple", Language.English) { finished = true }

        controller.shutdown()
        engine.finishLate()

        assertFalse("shutdown is a cancellation like any other — a late callback must not resurrect it", finished)
    }

    @Test
    fun `shutdown on an idle controller still reaches the engine`() {
        val (controller, engine, _) = makeController()
        controller.shutdown()
        assertEquals(1, engine.shutdownCount)
    }
}
