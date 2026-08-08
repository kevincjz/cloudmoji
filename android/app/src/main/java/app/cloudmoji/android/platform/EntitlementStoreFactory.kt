package app.cloudmoji.android.platform

/**
 * Chooses which [EntitlementStore] a build runs on.
 *
 * The one invariant that must never regress: a **non-debug build never
 * resolves the unlocked [StubEntitlementStore]**, whatever the flags say — the
 * stub grants Full Cloudmoji for free, so shipping it would give every user the
 * paid tier. See ANDROID_MONETIZATION.md §3.
 *
 * @param isDebug   `BuildConfig.DEBUG` at the call site.
 * @param allowStub whether a *debug* build may use the unlocked stub (`true`
 *   preserves today's "every mini-app visible in development" behaviour).
 *   Ignored entirely when [isDebug] is `false`.
 */
fun resolveEntitlementStore(isDebug: Boolean, allowStub: Boolean): EntitlementStore =
    if (isDebug && allowStub) StubEntitlementStore() else PlayBillingEntitlementStore()
