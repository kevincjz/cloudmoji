# Cloudmoji Android Monetization (Google Play Billing)

**Status:** not implemented. Play Billing is **Phase 5** of the Android plan
(`docs/superpowers/plans/2026-07-30-android-app.md`) and deliberately deferred.
Today **every** Android build uses `StubEntitlementStore`, which defaults to
**unlocked** — it must never ship in a Release build. **No Google Play developer
account exists yet.**

**Supersedes:** the "Already have" Google Play line previously in
`docs/growth/LAUNCH_PLAN.md`.

**Companion doc:** iOS/StoreKit is in [`MONETIZATION.md`](MONETIZATION.md). The
two purchases are **independent** — a Google Play buyer and an App Store buyer
are different entitlements; there is no cross-platform transfer without an
account and backend, which Cloudmoji does not have. `src/data/*` stays the
single shared content source for both.

## Decision recap

Same commercial shape as iOS, one platform-specific store:

| | Value |
|---|---|
| Product | One optional lifetime **Full Cloudmoji** unlock |
| Type | Google Play **one-time product** (managed in-app product), acknowledged but **never consumed** — ownership *is* the entitlement. Not a subscription. |
| Price | Base **USD 9.99**, localized by Play (Kevin launches SG-first; confirm the SGD equivalent) |
| Free tier | Words + Count in English (unchanged) |
| Full unlocks | Music, Flash Cards, Animals, Photos, Sleepy Cloud + Mandarin Chinese, Bahasa Melayu, Japanese, Tagalog. **No Apple Watch on Android.** |
| Application ID | `app.cloudmoji.android` |
| Proposed product ID | `app.cloudmoji.unlock.full` (match iOS for sanity; it is a *separate* SKU in a separate store) |

Never hard-code `$9.99` in the UI — read Play's localized `formattedPrice`,
exactly as iOS reads `Product.displayPrice`.

---

## 0. Google Play account — do this first (you don't have one yet)

This is the real gating item, and the account **type** decision changes your
launch timeline by weeks. Decide it before registering.

- **Register a Play Console developer account — $25 one-time** (versus Apple's
  recurring $99/yr). Pay once, keep forever.

- **Choose the account type deliberately:**

  | | Personal | Organization (**recommended**) |
  |---|---|---|
  | Setup | Fastest | Needs a **D‑U‑N‑S number** (free, but can take up to ~2 weeks to issue) + org verification |
  | Pre-production testing gate | **Closed testing with ≥20 testers for ≥14 continuous days** before you may request production access (Google requirement for personal accounts registered after Nov 2023) | **Exempt** from the 20-tester gate |
  | Fit for a kids' app | Works | Reads as more credible; you already operate as a company (SproutLearn / Epitome) |

  For a solo founder shipping a children's app, the **organization** account is
  usually worth the D‑U‑N‑S wait: it removes the 20-tester/14-day gate and is a
  better footing for the Families program. Start the D‑U‑N‑S request early — it
  is the long pole.

- **Complete identity verification** (government ID + address).

- **Set up the payments/merchant profile** (banking + tax). This is the Play
  equivalent of Apple's **Paid Apps Agreement** — without it you cannot sell or
  even license-test the product. Do it up front.

---

## 1. Play Console product configuration (the App Store Connect analogue)

- [ ] Developer account active; **account type chosen**; identity verified
- [ ] **Payments/merchant profile** complete (banking + tax)
- [ ] App created with application ID `app.cloudmoji.android`
- [ ] Monetization → Products → **One-time products** → create the product:
  - [ ] Product ID `app.cloudmoji.unlock.full` — **immutable and non-reusable**
        once created (same rule as an App Store product ID; get it right)
  - [ ] Localized **name + description** (customer-facing)
  - [ ] Price: base **USD 9.99**; review the auto-localized storefronts (SGD)
  - [ ] Status: **Active**
- [ ] Add **license testers** (Play Console → Setup → License testing) so test
      accounts purchase without a real charge
- [ ] Upload a signed AAB to an **Internal testing** track that contains the
      product-loading paywall UI
- [ ] Families/compliance (cross-ref Phase 6): target audience = children,
      content rating, **Data safety** form, privacy policy URL
      (`https://cloudmoji.app/privacy`)
- [ ] Decision-log entry: **client-side verification vs backend** (see Phase 5
      note below)

> **Play gotcha:** `queryProductDetails` returns your product only when *all*
> of these are true — the app is on a test track with the **same applicationId
> and signing key**, the product is **Active**, and the querying account is a
> **licensed tester**. An empty result in dev is almost always one of those
> three, not a code bug.

---

## 2. BillingClient wiring

Implement **`PlayBillingEntitlementStore : EntitlementStore`**. The seam is the
one-property interface in
[`EntitlementStore.kt`](../../android/app/src/main/java/app/cloudmoji/android/platform/EntitlementStore.kt)
— `val isUnlocked: StateFlow<Boolean>` — so **no consumer changes** (its own
KDoc says exactly this). `AppAccessPolicy` still just reads a `Boolean`.

- [ ] Add the dependency in the version catalog `android/gradle/libs.versions.toml`
      (like every other dep): `com.android.billingclient:billing-ktx` at the
      **current major** — Google enforces a rolling minimum Billing Library
      version, so pin the latest and note the next deadline rather than an old
      pinned major.
- [ ] Build a `BillingClient` with a `PurchasesUpdatedListener` and pending
      purchases enabled; `startConnection` with reconnect/backoff on
      `onBillingServiceDisconnected`.
- [ ] `queryProductDetails` for `app.cloudmoji.unlock.full`; surface the
      localized `formattedPrice` to the paywall (no hard-coded price).
- [ ] Purchase via `launchBillingFlow`; resolve outcomes in the listener.
- [ ] **Ownership scan** at launch **and on every foreground/resume**:
      `queryPurchasesAsync(INAPP)` → for a `Purchase` with
      `productId == app.cloudmoji.unlock.full` and
      `purchaseState == PURCHASED`:
      - verify (at minimum client-side signature check; backend later),
      - **`acknowledgePurchase`** if not yet acknowledged —
        **⚠️ Play auto-refunds any purchase not acknowledged within 3 days**,
      - **never `consumeAsync`** (consuming re-enables buying; a non-consumable
        must stay owned),
      - publish `isUnlocked = true`.
- [ ] Map to the **same parent-UI states as iOS** (see `MONETIZATION.md` table):
      Checking / Locked / Purchasing / **Pending** (`Purchase.PENDING`, i.e. the
      Play "slow card"/family-approval path) / Unlocked / Failed.
      `USER_CANCELED` is **not** an error.
- [ ] **Restore** = re-run `queryPurchasesAsync` (Play keeps ownership; there is
      no `AppStore.sync` analogue). Expose a parent **Restore / Check status**
      button that re-queries and re-acknowledges.
- [ ] **Refund / revocation:** Play does **not** push revocations to a running
      client the way StoreKit does — a refunded one-time purchase simply stops
      appearing in `queryPurchasesAsync`. Re-query on resume → relock.
      (Real-time relock needs **Real-time Developer Notifications + a backend**,
      which is out of scope for a client-only v1 — log that tradeoff.)
- [ ] Reuse
      [`EntitlementReconciliationGuard`](../../android/app/src/main/java/app/cloudmoji/android/platform/EntitlementReconciliationGuard.kt)
      so an in-flight scan can't overwrite a newer purchase/relock. Its
      generation logic is store-agnostic; only the candidate-ID element type
      needs attention — iOS IDs are `UInt64`, Play purchase tokens are
      `String`, so widen the set element type or hash the token to a stable ID.
- [ ] **Refund must never delete local content** (photos, settings) — same rule
      as iOS. Keep a parent route to **Manage Photos** whenever photos exist,
      even when Full is locked.

**Phase 5 decision (from the plan):** Google recommends server-side
verification. v1 *may* stay client-only to preserve the no-account/no-backend
product, accepting greater tampering and delayed-refund risk — **but that
tradeoff must be explicitly approved before production**, and recorded here.

---

## 3. The Release guardrail — the thing that must not leak

**Today's risk:** `CloudmojiApplication` unconditionally exposes
`entitlementStore: StubEntitlementStore` that **defaults to unlocked**
([CloudmojiApplication.kt:103](../../android/app/src/main/java/app/cloudmoji/android/CloudmojiApplication.kt#L103)).
Ship that as-is and **every Play user gets Full for free.** Before any public or
closed track:

- [ ] Change the property **type** to the interface `EntitlementStore`, not the
      concrete `StubEntitlementStore`.
- [ ] Select the implementation by build type:
  - **Release → always `PlayBillingEntitlementStore`. Never the stub.**
  - **Debug →** stub permitted, and even then only behind an **explicit opt-in**
    (a `BuildConfig` flag / Gradle `-P` property / instrumentation argument) so a
    debug default can't silently unlock. This mirrors iOS, where
    `StubEntitlementStore` is limited to Debug launches carrying
    `XCTestConfigurationFilePath` or the explicit `cm_use_stub_entitlements`
    flag.
- [ ] Add a **guard test that fails CI** if a non-debug build resolves a
      `StubEntitlementStore` — the Android analogue of iOS's "Release always
      injects `StoreEntitlementStore`". Assert
      `application.entitlementStore !is StubEntitlementStore` under a
      Release-shaped config.
- [ ] Keep the **non-visual** gates wired to the *real* store (the plan already
      lists these): resolve the effective child-facing language to English when
      locked **without erasing** the saved Full-language preference, and reject
      any Debug deep link into a premium mini-app while locked.

---

## 4. Testing (Play-specific)

- **License testers** buy with Google's test cards (no real charge). Exercise:
  purchase, cancel, **pending** (slow-test instrument), restore after
  reinstall, offline relaunch stays unlocked, and a **Play Console refund** →
  relock on next resume.
- **Internal testing** track for fast iteration; **Closed testing** (≥20
  testers / 14 days) only applies if you registered a **personal** account.
- Run the **Pre-launch report** on the signed AAB (Phase 6 hardening).
- Every manually found regression becomes the narrowest practical automated
  test, per the Android plan's test strategy.

---

## 5. iOS ↔ Android parity

| Rule | iOS | Android |
|---|---|---|
| Price | USD 9.99, `displayPrice` | USD 9.99, `formattedPrice` |
| Type | StoreKit non-consumable | Play one-time product, acknowledged, never consumed |
| Feature set | Full + Apple Watch | Full (no Watch) |
| Purchase identity | App Store entitlement | Play entitlement — **separate**, no transfer |
| Offline | Usable after StoreKit reconciles | Usable after Play reconciles |
| No-failure / no child-facing price | ✓ | ✓ (behind the existing arithmetic parental gate) |
| Refund preserves local data | ✓ | ✓ |

---

## References

- Play Billing Library — integrate one-time products, acknowledge vs consume,
  `queryPurchasesAsync`
- Play Console — license testing, testing tracks, and the new-personal-account
  20-tester/14-day production requirement
- Google Play **Families** policy & Designed for Families (child-directed apps)
- **D‑U‑N‑S** number for an organization developer account
- Real-time Developer Notifications (if/when a backend is added for revocations)
