package app.cloudmoji.android.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The real-money entitlement source: Full Cloudmoji is unlocked only by a
 * verified Google Play purchase of `app.cloudmoji.unlock.full`.
 *
 * **Phase 5 skeleton.** It deliberately starts — and, until the Billing wiring
 * in the TODO below is implemented, stays — **locked**, the exact opposite of
 * [StubEntitlementStore]'s unlocked-by-default read. A real store must never
 * grant access it has not verified, so a not-yet-implemented one grants
 * nothing. That is also what makes it safe to inject into a Release build
 * today (see [resolveEntitlementStore] and ANDROID_MONETIZATION.md §3): a
 * Release build cannot accidentally hand out Full for free — it simply has no
 * purchase path yet.
 *
 * The constructor takes no Android `Context` on purpose, so the store and the
 * factory that chooses it stay unit-testable off-device; the `BillingClient`
 * is built later, once a `Context` is available (the TODO below).
 *
 * TODO(Phase 5) — implement against Play Billing (ANDROID_MONETIZATION.md §2):
 *   - connect a `BillingClient` (with a `PurchasesUpdatedListener` and pending
 *     purchases enabled) and reconnect with backoff on disconnect;
 *   - `queryProductDetails("app.cloudmoji.unlock.full")` and expose the
 *     localized `formattedPrice` for the paywall (never hard-code the price);
 *   - `launchBillingFlow` to purchase;
 *   - scan ownership at launch AND on every foreground/resume via
 *     `queryPurchasesAsync(INAPP)`; for a PURCHASED entitlement: verify,
 *     `acknowledgePurchase` within Play's 3-day window (never `consumeAsync`),
 *     then publish unlocked;
 *   - reuse [EntitlementReconciliationGuard] so an in-flight scan cannot
 *     overwrite a newer purchase or relock;
 *   - restore = re-query; refund/revocation = relock on the next resume;
 *   - never delete local content (photos, settings) as a side effect of a relock.
 */
class PlayBillingEntitlementStore : EntitlementStore {
    private val state = MutableStateFlow(false)

    /** Locked until a verified Play purchase is reconciled. */
    override val isUnlocked: StateFlow<Boolean> = state.asStateFlow()
}
