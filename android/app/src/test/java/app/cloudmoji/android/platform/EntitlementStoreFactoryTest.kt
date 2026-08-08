package app.cloudmoji.android.platform

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guardrail for ANDROID_MONETIZATION.md §3.
 *
 * [StubEntitlementStore] defaults to **unlocked**, so if it ever reaches a
 * Release build every user gets Full Cloudmoji for free. These tests pin the
 * two invariants that prevent that: a non-debug build never resolves the stub,
 * whatever the flags, and the real store starts locked.
 */
class EntitlementStoreFactoryTest {

    @Test
    fun `a release build never resolves the unlocked stub, even when a stub is requested`() {
        val store = resolveEntitlementStore(isDebug = false, allowStub = true)
        assertTrue(
            "Release must use the Play-Billing store, not the unlocked stub",
            store is PlayBillingEntitlementStore,
        )
        assertFalse(store is StubEntitlementStore)
    }

    @Test
    fun `a debug build uses the stub when one is allowed`() {
        val store = resolveEntitlementStore(isDebug = true, allowStub = true)
        assertTrue(store is StubEntitlementStore)
    }

    @Test
    fun `a debug build can opt out of the stub to exercise the real locked path`() {
        val store = resolveEntitlementStore(isDebug = true, allowStub = false)
        assertTrue(store is PlayBillingEntitlementStore)
    }

    @Test
    fun `the real Play-Billing store starts locked`() {
        assertFalse(PlayBillingEntitlementStore().isUnlocked.value)
    }
}
