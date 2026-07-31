package app.cloudmoji.android.platform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ported from
 * `ios/CloudmojiCore/Tests/CloudmojiCoreTests/EntitlementReconciliationTests.swift`.
 * Nothing in [EntitlementReconciliationGuard] is StoreKit-specific, so the
 * three cases translate directly.
 */
class EntitlementReconciliationGuardTest {

    @Test
    fun `a newer scan prevents an older scan from publishing`() {
        val reconciliation = EntitlementReconciliationGuard()
        val olderScan = reconciliation.begin()
        val newerScan = reconciliation.begin()

        assertNull(reconciliation.resolvedAccess(token = olderScan, candidates = setOf(41uL), revoked = emptySet()))
        assertEquals(true, reconciliation.resolvedAccess(token = newerScan, candidates = setOf(41uL), revoked = emptySet()))
    }

    @Test
    fun `a revoked candidate cannot grant from a lagging entitlement snapshot`() {
        val reconciliation = EntitlementReconciliationGuard()
        val scan = reconciliation.begin()

        assertEquals(
            false,
            reconciliation.resolvedAccess(token = scan, candidates = setOf(72uL), revoked = setOf(72uL)),
        )
    }

    @Test
    fun `a live invalidation rejects the scan that started before it`() {
        val reconciliation = EntitlementReconciliationGuard()
        val beforeInvalidation = reconciliation.begin()
        reconciliation.invalidate()

        assertNull(
            reconciliation.resolvedAccess(token = beforeInvalidation, candidates = setOf(99uL), revoked = setOf(99uL)),
        )
    }
}
