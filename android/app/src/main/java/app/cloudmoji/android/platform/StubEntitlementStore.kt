package app.cloudmoji.android.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deterministic entitlement for previews, tests, and every build before a
 * Play Billing product exists.
 *
 * Defaults to **unlocked**: there is no Play Store product yet, so a default
 * of locked would hide five finished mini-apps and four languages behind a
 * Grown-ups button that cannot do anything — the same reasoning iOS's
 * `StubEntitlementStore` documents for its own unlocked-by-default read.
 *
 * Unlike the iOS stub, this holds no persisted purchase/restore outcome
 * configuration (`cm_stub_purchase_outcome` and friends) — those exist on
 * iOS to drive deterministic paywall UI tests, and the paywall itself is
 * explicitly out of scope until Play Billing is a separately-approved task.
 * [setUnlocked] is the whole surface a future Grown-ups panel or test needs
 * in the meantime; it is in-memory only; a process restart returns to
 * [initiallyUnlocked] rather than remembering a toggle made mid-session.
 */
class StubEntitlementStore(initiallyUnlocked: Boolean = true) : EntitlementStore {
    private val state = MutableStateFlow(initiallyUnlocked)

    override val isUnlocked: StateFlow<Boolean> = state.asStateFlow()

    fun setUnlocked(unlocked: Boolean) {
        state.value = unlocked
    }
}
