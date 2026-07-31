package app.cloudmoji.android.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ToneDirector]'s arbitration — the Android analogue of iOS
 * `AudioDirectorTests` (`ios/Cloudmoji/CloudmojiTests/AudioTests.swift`),
 * scoped to what Music actually needs: attach/detach balance, stalled-engine
 * recovery on the next tap, silence when nobody is attached or the platform
 * denies focus, and that mashing pads never stacks focus requests.
 */
class ToneDirectorTest {

    private class FakeToneEngine : ToneEngineDriving {
        override var isRunning = false
        var starts = 0
            private set
        var stops = 0
            private set
        val tones = mutableListOf<Int>()
        var sleepStarts = 0
            private set
        var sleepStops = 0
            private set

        override fun start() {
            starts += 1
            isRunning = true
        }

        override fun stop() {
            stops += 1
            isRunning = false
        }

        override fun playTone(index: Int) {
            tones += index
        }

        override fun playSleepNoise() {
            sleepStarts += 1
        }

        override fun stopSleepNoise() {
            sleepStops += 1
        }
    }

    /** [granted] is a `var` so one test can model the case that matters
     * most for the ambience: focus denied when the session starts (a call in
     * progress), granted a moment later. */
    private class FakeAudioFocusSystem(var granted: Boolean = true) : AudioFocusSystem {
        var requestCount = 0
            private set
        var abandonCount = 0
            private set

        override fun requestTransientDuckFocus(): Boolean {
            requestCount += 1
            return granted
        }

        override fun abandonFocus() {
            abandonCount += 1
        }
    }

    private class Fixture(granted: Boolean = true) {
        val system = FakeAudioFocusSystem(granted)
        val engine = FakeToneEngine()

        /** Exposed, not inlined into [director], because the focus-loss path
         * this suite models calls `releaseAll()` on it directly — that is
         * what `CloudmojiApplication.onAudioFocusChange` does. */
        val focusOwner = AudioFocusOwner(system)
        val director = ToneDirector(focusOwner, engine)

        /** Whether the platform is currently granting focus. */
        var granting: Boolean
            get() = system.granted
            set(value) { system.granted = value }
    }

    /**
     * Attach starts the engine and requests nothing yet (focus is only asked
     * for on an actual tap); detach gives it back, and a second detach does
     * not stop an already-stopped engine again — mirrors iOS
     * `attachDetachIsBalanced`.
     *
     * Mutation proof: temporarily removed the `if (!isAttached) return`
     * guard from `ToneDirector.detach()`. This test failed
     * (`engine.stops` was 2, not 1) before the guard was restored.
     */
    @Test
    fun `attach starts the engine and detach gives it back, once`() {
        val fixture = Fixture()
        assertFalse(fixture.director.isAttached)

        fixture.director.attach()
        assertTrue(fixture.director.isAttached)
        assertEquals(1, fixture.engine.starts)

        fixture.director.detach()
        fixture.director.detach()
        assertFalse(fixture.director.isAttached)
        assertEquals(1, fixture.engine.stops)
    }

    /**
     * The interruption recovery, checked at the point of use: the engine
     * stalled (Android's analogue of an iOS `AVAudioEngine` interruption)
     * and the very next tap restarts it before playing.
     *
     * Mutation proof: temporarily deleted the `restartIfStalled()` call
     * from `ToneDirector.playTone`. This test failed (`engine.starts` stayed
     * at 1) before the call was restored.
     */
    @Test
    fun `a tap after an interruption restarts a stalled engine`() {
        val fixture = Fixture()
        fixture.director.attach()

        // What an interruption looks like from here: the engine stopped and
        // nothing told us.
        fixture.engine.isRunning = false

        fixture.director.playTone(3)
        assertEquals(2, fixture.engine.starts)
        assertEquals(listOf(3), fixture.engine.tones)

        // A running engine is not restarted on every tap.
        fixture.director.playTone(4)
        assertEquals(2, fixture.engine.starts)
        assertEquals(listOf(3, 4), fixture.engine.tones)
    }

    /** Nothing plays, and nothing is asked of the platform, for a screen
     * that is not (or no longer) attached — a stray call must not spin the
     * engine back up or request focus. */
    @Test
    fun `a detached director does not play or restart the engine`() {
        val fixture = Fixture()

        fixture.director.playTone(0)

        assertEquals(0, fixture.engine.starts)
        assertTrue(fixture.engine.tones.isEmpty())
        assertEquals(0, fixture.system.requestCount)
    }

    /**
     * A denied focus request is silent: no crash, no tone, matching this
     * project's "no failure states" rule.
     *
     * Mutation proof: temporarily removed the
     * `if (!focusOwner.request(...)) return` guard from `playTone`. This
     * test failed (`engine.tones` contained `0`) before the guard was
     * restored.
     */
    @Test
    fun `a denied focus request is silent`() {
        val fixture = Fixture(granted = false)
        fixture.director.attach()

        fixture.director.playTone(0)

        assertTrue(fixture.engine.tones.isEmpty())
        assertEquals(1, fixture.system.requestCount)
    }

    /** Rapid repeated taps must not stack focus requests — `AudioFocusOwner`
     * is idempotent per client once granted, and this is the property that
     * makes mashing pads safe. */
    @Test
    fun `rapid repeated taps request focus once and never stack`() {
        val fixture = Fixture()
        fixture.director.attach()

        fixture.director.playTone(0)
        fixture.director.playTone(1)
        fixture.director.playTone(2)

        assertEquals(1, fixture.system.requestCount)
        assertEquals(listOf(0, 1, 2), fixture.engine.tones)
    }

    /** [ToneDirector.silence] stops the engine without giving up the
     * screen's hold on it — the audio-focus-loss handler's own contract, see
     * [AudioFocusLossAction]. */
    @Test
    fun `silence stops the engine without detaching`() {
        val fixture = Fixture()
        fixture.director.attach()

        fixture.director.silence()

        assertTrue(fixture.director.isAttached)
        assertEquals(1, fixture.engine.stops)
    }

    /**
     * Sleepy Cloud's own calls, gated exactly like [ToneDirector.playTone]:
     * nothing starts, and no focus is requested, for a screen that has not
     * attached.
     *
     * Mutation proof: temporarily removed the `if (!isAttached) return`
     * guard from `ToneDirector.playSleepNoise()`. This test failed
     * (`engine.sleepStarts` was 1, not 0) before the guard was restored.
     */
    @Test
    fun `playSleepNoise and stopSleepNoise are silent while not attached`() {
        val fixture = Fixture()

        fixture.director.playSleepNoise()
        fixture.director.stopSleepNoise()

        assertEquals(0, fixture.engine.sleepStarts)
        assertEquals(0, fixture.engine.sleepStops)
        assertEquals(0, fixture.system.requestCount)
    }

    /**
     * Once attached, [ToneDirector.playSleepNoise] requests focus and reaches
     * the engine; [ToneDirector.stopSleepNoise] reaches it too, without
     * detaching — a mute toggle mid-session silences the loop but Sleepy
     * Cloud is still the screen holding the engine.
     */
    @Test
    fun `playSleepNoise and stopSleepNoise reach the engine once attached`() {
        val fixture = Fixture()
        fixture.director.attach()

        fixture.director.playSleepNoise()
        assertEquals(1, fixture.engine.sleepStarts)
        assertEquals(1, fixture.system.requestCount)

        fixture.director.stopSleepNoise()
        assertEquals(1, fixture.engine.sleepStops)
        assertTrue(fixture.director.isAttached)
    }

    /** A denied focus request silences the ambience just like a denied pad
     * tap — no crash, no ambience, matching this project's "no failure
     * states" rule. */
    @Test
    fun `a denied focus request keeps the ambience silent`() {
        val fixture = Fixture(granted = false)
        fixture.director.attach()

        fixture.director.playSleepNoise()

        assertEquals(0, fixture.engine.sleepStarts)
    }

    /**
     * **The regression this suite exists to hold.** A transient focus loss
     * while the app stays in the *foreground* — a notification sound, say —
     * reaches `CloudmojiApplication.onAudioFocusChange`, which calls
     * [ToneDirector.silence]. That stops the track without touching
     * [ToneDirector.isAttached], and Sleepy Cloud has no next tap to run
     * `restartIfStalled` the way Music's next pad does. Before
     * [ToneDirector.resumeAfterFocusGain] existed, the ambience simply died
     * for the rest of a ten-minute session while the cloud went on breathing.
     *
     * Mutation proof: temporarily made `resumeAfterFocusGain()` a no-op
     * (`return` on the first line). This test failed — `sleepStarts` stayed
     * at 1 — before the body was restored.
     */
    @Test
    fun `a foreground focus blip does not end the ambience for good`() {
        val fixture = Fixture()
        fixture.director.attach()
        fixture.director.playSleepNoise()
        assertEquals(1, fixture.engine.sleepStarts)

        // The focus-loss handler's own three calls, in order.
        fixture.director.silence()
        fixture.focusOwner.releaseAll()
        assertTrue("the screen is still Sleepy Cloud's", fixture.director.isAttached)

        fixture.director.resumeAfterFocusGain()

        assertEquals("the ambience never came back", 2, fixture.engine.sleepStarts)
    }

    /**
     * A focus gain must not start a wind-down loop over Music. Music never
     * calls [ToneDirector.playSleepNoise], so it never sets
     * [ToneDirector.isSleepNoiseWanted] — that flag, not "was something
     * playing", is what the resume is gated on.
     *
     * Mutation proof: temporarily changed `resumeAfterFocusGain()` to call
     * `playSleepNoise()` unconditionally. This test failed before the
     * `isSleepNoiseWanted` guard was restored.
     */
    @Test
    fun `a focus gain during Music starts no ambience`() {
        val fixture = Fixture()
        fixture.director.attach()
        fixture.director.playTone(0)
        fixture.director.silence()

        fixture.director.resumeAfterFocusGain()

        assertEquals(0, fixture.engine.sleepStarts)
        assertFalse(fixture.director.isSleepNoiseWanted)
    }

    /**
     * A phone muted mid-session stays silent through any number of focus
     * changes: [ToneDirector.stopSleepNoise] is a *deliberate* stop and
     * clears the want, unlike [ToneDirector.silence].
     *
     * Mutation proof: temporarily removed `isSleepNoiseWanted = false` from
     * `stopSleepNoise()`. This test failed — the ambience restarted on a
     * muted phone — before it was restored.
     */
    @Test
    fun `a focus gain after muting keeps the phone silent`() {
        val fixture = Fixture()
        fixture.director.attach()
        fixture.director.playSleepNoise()
        fixture.director.stopSleepNoise()

        fixture.director.resumeAfterFocusGain()

        assertEquals("mute was undone by a focus change", 1, fixture.engine.sleepStarts)
    }

    /**
     * Leaving the mini-app ends the want outright — a focus gain arriving
     * after the child is back at the launcher must not resurrect a loop over
     * it.
     *
     * Mutation proof: temporarily removed `isSleepNoiseWanted = false` from
     * `detach()`. This test failed before it was restored.
     */
    @Test
    fun `a focus gain after leaving the screen resurrects nothing`() {
        val fixture = Fixture()
        fixture.director.attach()
        fixture.director.playSleepNoise()
        fixture.director.detach()

        fixture.director.resumeAfterFocusGain()

        assertEquals(1, fixture.engine.sleepStarts)
        assertFalse(fixture.director.isSleepNoiseWanted)
    }

    /**
     * A session that begins while focus is *already* denied — a call in
     * progress — still wants its ambience, and gets it the moment focus
     * arrives. Without recording the want before the focus request, such a
     * session would stay silent for its whole ten minutes.
     *
     * Mutation proof: temporarily moved `isSleepNoiseWanted = true` to after
     * the `focusOwner.request(...)` guard in `playSleepNoise()`. This test
     * failed before it was moved back.
     */
    @Test
    fun `a session denied focus at the start still gets its ambience later`() {
        val fixture = Fixture(granted = false)
        fixture.director.attach()

        fixture.director.playSleepNoise()
        assertEquals(0, fixture.engine.sleepStarts)
        assertTrue("the want was not recorded", fixture.director.isSleepNoiseWanted)

        fixture.granting = true
        fixture.director.resumeAfterFocusGain()

        assertEquals("the ambience never arrived", 1, fixture.engine.sleepStarts)
    }
}
