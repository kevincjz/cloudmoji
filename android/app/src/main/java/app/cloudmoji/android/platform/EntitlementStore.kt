package app.cloudmoji.android.platform

import kotlinx.coroutines.flow.StateFlow

/**
 * The one commerce seam every mini-app and [app.cloudmoji.android.model.AppAccessPolicy]
 * consumer read from.
 *
 * Mirrors iOS CloudmojiCore's `EntitlementProviding`, narrowed to what the
 * Android product needs before a Play Billing product exists: whether Full
 * Cloudmoji is unlocked, as a hot [StateFlow] so Compose screens recompose
 * the instant it changes rather than polling. [StubEntitlementStore] is the
 * only implementation today. A future Play Billing-backed store — the
 * `Play Billing entitlement store` box in the plan's architecture diagram —
 * implements this same one-property interface, so no consumer (including
 * [app.cloudmoji.android.model.AppAccessPolicy], which only ever reads a
 * plain `Boolean`) changes when it replaces the stub.
 */
interface EntitlementStore {
    val isUnlocked: StateFlow<Boolean>
}
