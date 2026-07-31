package app.cloudmoji.android.platform

/**
 * Rejects state collected by a reconciliation that was superseded while it
 * was suspended mid-scan.
 *
 * Ported from iOS CloudmojiCore's `EntitlementReconciliationGuard`. Nothing
 * about it is StoreKit-specific — it is a generic "only the newest scan may
 * publish" generation counter over opaque transaction IDs — so it is ported
 * now, ahead of Play Billing, as infrastructure the future
 * `PlayBillingEntitlementStore` can reuse unchanged: a Billing purchase
 * query is just as re-entrant across suspension points as StoreKit's
 * `Transaction.currentEntitlements`, and needs the exact same guarantee —
 * an older in-flight scan must not overwrite a newer live update (a
 * purchase, or a revocation) that arrived while it was suspended.
 *
 * Not currently wired into [StubEntitlementStore]: the stub is synchronous
 * and has nothing to race.
 */
class EntitlementReconciliationGuard {
    var generation: ULong = 0uL
        private set

    /** Starts a new scan and returns the token it must present to [resolvedAccess]. */
    fun begin(): ULong {
        generation += 1uL
        return generation
    }

    /** A live update (a purchase or a revocation) invalidates any scan in flight. */
    fun invalidate() {
        generation += 1uL
    }

    /**
     * `null` if [token] is not the current generation — a newer scan or a
     * live update superseded it, and its result must not be published.
     * Otherwise, whether [candidates] minus [revoked] is non-empty.
     */
    fun resolvedAccess(
        token: ULong,
        candidates: Set<ULong>,
        revoked: Set<ULong>,
    ): Boolean? {
        if (token != generation) return null
        return (candidates - revoked).isNotEmpty()
    }
}
