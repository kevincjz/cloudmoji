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
    }

    private class FakeAudioFocusSystem(private val granted: Boolean = true) : AudioFocusSystem {
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
        val director = ToneDirector(AudioFocusOwner(system), engine)
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
}
