package app.cloudmoji.android.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves [AudioFocusOwner]'s request/abandon **pairing** invariant: whatever
 * mix of internal clients ([AudioFocusClient.SPEECH] now, `.TONE` from
 * Task 10 on) come and go, the platform [AudioFocusSystem] sees exactly one
 * request per 0→1 transition and exactly one abandon per 1→0 transition —
 * never more, never a mismatched count.
 */
class AudioFocusOwnerTest {

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

    @Test
    fun `requesting from idle asks the platform once and holds focus`() {
        val system = FakeAudioFocusSystem()
        val owner = AudioFocusOwner(system)
        assertFalse(owner.isHeld)

        assertTrue(owner.request(AudioFocusClient.SPEECH))

        assertTrue(owner.isHeld)
        assertEquals(1, system.requestCount)
        assertEquals(0, system.abandonCount)
    }

    @Test
    fun `releasing the last client abandons focus`() {
        val system = FakeAudioFocusSystem()
        val owner = AudioFocusOwner(system)
        owner.request(AudioFocusClient.SPEECH)

        owner.release(AudioFocusClient.SPEECH)

        assertFalse(owner.isHeld)
        assertEquals(1, system.requestCount)
        assertEquals(1, system.abandonCount)
    }

    @Test
    fun `a second client while focus is already held does not re-request`() {
        val system = FakeAudioFocusSystem()
        val owner = AudioFocusOwner(system)
        owner.request(AudioFocusClient.SPEECH)

        assertTrue(owner.request(AudioFocusClient.TONE))

        assertEquals("focus was already held; a second client must not re-ask", 1, system.requestCount)
    }

    @Test
    fun `focus is only abandoned once every client has released`() {
        val system = FakeAudioFocusSystem()
        val owner = AudioFocusOwner(system)
        owner.request(AudioFocusClient.SPEECH)
        owner.request(AudioFocusClient.TONE)

        owner.release(AudioFocusClient.SPEECH)
        assertTrue("TONE still holds it", owner.isHeld)
        assertEquals(0, system.abandonCount)

        owner.release(AudioFocusClient.TONE)
        assertFalse(owner.isHeld)
        assertEquals(1, system.abandonCount)
    }

    @Test
    fun `requesting twice for the same client without releasing does not double-count`() {
        val system = FakeAudioFocusSystem()
        val owner = AudioFocusOwner(system)
        owner.request(AudioFocusClient.SPEECH)
        owner.request(AudioFocusClient.SPEECH)

        assertEquals(1, system.requestCount)

        owner.release(AudioFocusClient.SPEECH)
        assertFalse(owner.isHeld)
        assertEquals(1, system.abandonCount)
    }

    @Test
    fun `releasing a client that never requested is a no-op`() {
        val system = FakeAudioFocusSystem()
        val owner = AudioFocusOwner(system)

        owner.release(AudioFocusClient.SPEECH)

        assertEquals(0, system.requestCount)
        assertEquals(0, system.abandonCount)
        assertFalse(owner.isHeld)
    }

    @Test
    fun `releasing one of two clients that never abandons the other's hold`() {
        val system = FakeAudioFocusSystem()
        val owner = AudioFocusOwner(system)
        owner.request(AudioFocusClient.TONE)

        owner.release(AudioFocusClient.SPEECH) // never requested

        assertTrue(owner.isHeld)
        assertEquals(0, system.abandonCount)
    }

    @Test
    fun `a denied request does not mark the client active or the owner held`() {
        val system = FakeAudioFocusSystem(granted = false)
        val owner = AudioFocusOwner(system)

        assertFalse(owner.request(AudioFocusClient.SPEECH))

        assertFalse(owner.isHeld)
        assertEquals(1, system.requestCount)
        // Nothing to abandon: the platform never granted focus in the first place.
        owner.release(AudioFocusClient.SPEECH)
        assertEquals(0, system.abandonCount)
    }

    /**
     * [AudioFocusOwner.releaseAll] — the audio-focus-loss handler's own
     * recovery tool (see `AudioFocusLossPolicy.kt`): the platform has
     * already taken focus away by the time this is called, so it must not
     * ask [AudioFocusSystem.abandonFocus] to give back something it already
     * took for itself.
     *
     * Mutation proof: temporarily changed `releaseAll` to call
     * `system.abandonFocus()` after clearing the set. This test failed
     * (`abandonCount` was 1, not 0) before the extra call was removed.
     */
    @Test
    fun `releaseAll clears every client without telling the platform to abandon`() {
        val system = FakeAudioFocusSystem()
        val owner = AudioFocusOwner(system)
        owner.request(AudioFocusClient.SPEECH)
        owner.request(AudioFocusClient.TONE)

        owner.releaseAll()

        assertFalse(owner.isHeld)
        assertEquals(
            "the platform already took focus away; releaseAll must not ask it to abandon again",
            0,
            system.abandonCount,
        )
    }

    @Test
    fun `after releaseAll, a fresh request asks the platform again`() {
        val system = FakeAudioFocusSystem()
        val owner = AudioFocusOwner(system)
        owner.request(AudioFocusClient.SPEECH)
        owner.releaseAll()

        assertTrue(owner.request(AudioFocusClient.TONE))

        assertEquals(2, system.requestCount)
    }

    @Test
    fun `releaseAll on an already-idle owner is a no-op`() {
        val system = FakeAudioFocusSystem()
        val owner = AudioFocusOwner(system)

        owner.releaseAll()

        assertFalse(owner.isHeld)
        assertEquals(0, system.abandonCount)
    }
}
