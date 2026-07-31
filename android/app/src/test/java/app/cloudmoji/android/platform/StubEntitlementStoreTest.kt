package app.cloudmoji.android.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Deterministic coverage for [StubEntitlementStore]. Narrower than iOS's
 * `StubEntitlementStoreTests` -- that suite also covers purchase/restore
 * outcome plumbing for a paywall UI that does not exist yet on Android (Play
 * Billing and the Grown-ups panel are both explicitly out of scope for this
 * task). What carries over is the one product rule that does apply today:
 * the stub defaults to unlocked, and nothing else can lock it except an
 * explicit call.
 */
class StubEntitlementStoreTest {

    @Test
    fun `defaults to unlocked`() {
        val store = StubEntitlementStore()
        assertTrue(store.isUnlocked.value)
    }

    @Test
    fun `can be constructed locked for tests that need a locked start`() {
        val store = StubEntitlementStore(initiallyUnlocked = false)
        assertEquals(false, store.isUnlocked.value)
    }

    @Test
    fun `setUnlocked is a real toggle in both directions`() {
        val store = StubEntitlementStore(initiallyUnlocked = false)
        assertEquals(false, store.isUnlocked.value)

        store.setUnlocked(true)
        assertEquals(true, store.isUnlocked.value)

        store.setUnlocked(false)
        assertEquals(false, store.isUnlocked.value)
    }
}
