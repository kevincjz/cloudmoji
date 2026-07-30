# Cloudmoji Monetization

**Status:** implemented in the workspace; App Store Connect, physical-device
sandbox, and TestFlight launch gates remain

**Last updated:** 2026-07-30

**Supersedes:** the earlier subscription / RevenueCat proposal

## Decision

Cloudmoji is a free iPhone and iPad app with one optional lifetime unlock:

| Tier | Price | Included |
|---|---:|---|
| Free | Free forever | Words and Count in English, including the full bundled emoji and counting catalogue |
| Full Cloudmoji | USD 9.99 base price, localized by the App Store | Music, Flash Cards, Animals, Photos, Sleepy Cloud, four additional languages, and the complete Apple Watch experience |

Full Cloudmoji is one StoreKit 2 **non-consumable** In-App Purchase:

```text
Product ID: app.cloudmoji.unlock.full
Reference name: Full Cloudmoji
Type: Non-Consumable
US base price: USD 9.99
```

The product is a one-time purchase, not a subscription. StoreKit supplies the
customer-facing localized price through `Product.displayPrice`; the app must
never hard-code `$9.99` in its interface.

## Implementation verification

The iPhone, iPad, and watchOS code paths, parent purchase UI, local StoreKit
catalog, deterministic entitlement tests, and app-icon validation are
implemented. The local catalog marks the product as eligible for Family
Sharing; the irreversible production setting must still be enabled separately
in App Store Connect.

Xcode 26.6 with the iOS 26.5 simulator currently rejects `SKTestSession`
transaction mutations with `SKInternalErrorDomain Code 3`. The automated
purchase/refund/restore lifecycle test therefore skips only that runtime and
must be run on an iOS 18–26.4 or 26.6+ simulator before release. On iOS 26.5,
the local product, localized `$9.99` display, Apple test purchase sheet, and
verified Full unlock were exercised manually through Xcode.

Free remains a useful, complete core learning loop rather than a timed trial.
There are no ads, accounts, consumables, trials, recurring charges, external
payment links, or purchase nags in the child-facing area.

## Why this model

- It keeps the proposition simple: two foundational mini-apps in English are
  free, while one purchase unlocks every other experience.
- A one-time purchase is easier for a parent to understand and more credible
  for an offline, private children's app than a recurring subscription.
- StoreKit 2 can validate this single entitlement on-device. Cloudmoji does not
  need RevenueCat, a receipt server, App Store Server Notifications, accounts,
  or a backend for the first version.
- The purchase stays inside the gated Grown-ups area. Apple's Kids Category
  rules require purchasing opportunities to be behind a parental gate.

## Product contract

### Free

Free is the complete core learning loop, not a demo:

- Words
- Count
- English
- The complete bundled emoji and counting catalogue
- Parent controls that apply to the included experiences, such as sound,
  categories, and counting range
- A noncommercial `For Grown-ups` doorway that opens the parental gate and is
  not counted as a child mini-app

### Full Cloudmoji

One purchase unlocks:

- Music
- Flash Cards
- Animals
- Photos and Camera
- Sleepy Cloud
- Mandarin Chinese, Bahasa Melayu, Japanese, and Tagalog
- The complete Apple Watch experience, including wrist emoji exchange and
  short voice notes

The copy should promise the named features above. Do not promise that every
future Cloudmoji product or separately distributed app will be included.

### Family Sharing

Turn on Family Sharing for the non-consumable before launch. Cloudmoji is a
family product, so requiring a second purchase for another family member would
be surprising. Apple notes that enabling Family Sharing for an In-App Purchase
cannot be reversed; make this setting only after confirming the product ID and
scope are final.

Family Sharing and iPhone/watchOS universal availability are different things:

- Family Sharing makes the purchase eligible for other members of an App Store
  family.
- A companion iPhone/watchOS app can use the same In-App Purchase across both
  platform versions. The purchase is configured with the primary app in App
  Store Connect and both binaries read the same product entitlement.

## Apple Watch constraint

The watch binary is embedded in the app bundle, so Cloudmoji cannot literally
ship the watch app only to paying customers. A free customer may see or install
the watch companion.

The implementable promise is:

> The Apple Watch experience is part of Full Cloudmoji.

When locked, the watch shows a quiet parent-facing screen such as “Unlock Full
Cloudmoji in the iPhone app” and exposes no emoji pager, microphone, speech, or
WatchConnectivity actions. It must not request microphone permission while
locked.

The watch should read the universal StoreKit entitlement directly. It should
not treat a Boolean received through WatchConnectivity as the source of truth.
WatchConnectivity can trigger a refresh for responsiveness, but only a verified
StoreKit transaction grants access.

## Parent purchase experience

There are two routes to the same offer:

1. Grown-ups → parental gate → Settings → Full Cloudmoji.
2. The free launcher's `For Grown-ups` tile → parental gate → Full Cloudmoji.

The launcher doorway contains no price, purchase command, paid-feature list, or
upgrade language. It is a designated parent entry point, not a third free
mini-app. All commercial information remains behind the parental gate, in line
with the Kids Category requirement.

The locked section should show:

- “Cloudmoji Free”
- “You’re using the free version.”
- “Included: Words and Count in English.”
- “See the paid Full version — {localized price}”
- “Full Cloudmoji is the paid version.”
- “One purchase. No subscription.”
- Music, Flash Cards, Animals, Photos, Sleepy Cloud, four more languages, and
  Apple Watch
- A primary button: `Unlock Full Cloudmoji — \(product.displayPrice)`
- `Restore Purchase`

Do not enable the purchase button until StoreKit has loaded the product and its
localized price. While loading, show “Checking price…”. If product loading
fails, keep the free app usable and offer a parent-facing Retry action.

The purchase and restore buttons must disable while an operation is in flight
so a double tap cannot start overlapping StoreKit calls.

Expected states:

| State | Parent UI | Access |
|---|---|---|
| Checking | “Checking purchase…” | No new access until StoreKit reconciles |
| Locked | Price, feature list, Unlock, Restore | Free |
| Purchasing | Progress indicator; controls disabled | Free |
| Pending | “Waiting for approval…” | Free until a verified transaction arrives |
| Unlocked | “Full Cloudmoji unlocked ✓” | Full |
| Failed | Short explanation and Retry | Last verified access |

Cancellation is not an error and needs no alert. Ask to Buy is not a failure:
`Product.PurchaseResult.pending` remains locked, displays “Waiting for
approval…”, and is completed later through `Transaction.updates`.

## Entitlement architecture

### Source of truth

Only a verified StoreKit transaction for `app.cloudmoji.unlock.full` grants Full.

At launch:

1. Start the `Transaction.updates` listener.
2. Iterate `Transaction.unfinished`, verify relevant transactions, apply them,
   and finish them.
3. Iterate `Transaction.currentEntitlements`.
4. Set locked or unlocked after the entitlement scan completes.
5. Keep listening for purchases completed on another device, Ask to Buy
   approval, Family Sharing changes, refunds, and revocations.

Grant access only for `.verified` transactions with the expected product ID and
no `revocationDate`. Never grant access from an unverified transaction, a
successful `AppStore.sync()` call alone, a cached Boolean alone, or a
WatchConnectivity message.

When the live update stream reports a verified revocation, relock immediately
and remember that transaction ID while reconciling `currentEntitlements`.
StoreKit can publish the update just before its entitlement sequence drops the
previous snapshot; the remembered ID prevents that stale snapshot from
re-unlocking Full. A later verified granting update clears the remembered ID.

After delivering access for a verified purchase, call
`await transaction.finish()`.

### Restore

StoreKit normally keeps current entitlements synchronized automatically.
`Restore Purchase` exists for the exceptional case and for App Review:

1. Call `try await AppStore.sync()` only after the parent taps Restore. This can
   show an App Store authentication prompt.
2. Re-scan `Transaction.currentEntitlements`.
3. Unlock only if the scan finds the verified Full transaction.
4. Otherwise say “No Full Cloudmoji purchase was found.”

### Offline behavior

Once purchased and reconciled, Full remains usable offline. All premium content
is bundled locally and StoreKit keeps transaction information on-device.
Transient product-loading or network errors must not remove previously verified
access.

### Refunds and revoked Family Sharing

A refund or revoked shared purchase relocks the premium experiences when
StoreKit reports the revocation. It must not delete user-created data.

In particular:

- Hide the Photos child experience and prevent new capture while locked.
- Keep a parent-only route to Manage Photos whenever photos remain on the
  device, even when Full is locked, so the parent can export or delete them.
- Never delete photos, settings, or other local content as a side effect of an
  entitlement change.

## Code changes

### Shared StoreKit store

Add `StoreEntitlementStore` beside
`ios/CloudmojiCore/Sources/CloudmojiCore/Entitlements.swift`. `CloudmojiCore`
already supports iOS 17 and watchOS 10, so the implementation can be shared by
the phone and watch targets.

Evolve `EntitlementProviding` to expose enough observable state for the table
above while retaining `isUnlocked` as the single content-gating answer. The
store owns:

- the product and `displayPrice`
- the current access state
- purchase / restore activity
- the transaction observer task

`StubEntitlementStore` remains for previews and deterministic tests. In a
Release build, `CloudmojiApp` must always inject `StoreEntitlementStore`.

In Debug, use a separate explicit launch argument such as
`-cm_use_stub_entitlements YES` before honoring
`-cm_premium_unlocked YES|NO`. This prevents a test switch from becoming a
shipping entitlement bypass. A plain UserDefaults Boolean must never grant
access in Release; StoreKit's locally maintained entitlements cover offline
reconciliation.

### Phone gating

The current central gate is correct:

```text
AppModel.visibleMiniApps
  ├─ Free: Words, Count
  └─ Full: + Music, Flash Cards, Animals, Photos, Sleepy Cloud
```

The free launcher draws `For Grown-ups` beside those two apps, but it remains a
separate parent doorway and never enters `visibleMiniApps`.

Keep premium views ignorant of commerce. Also add entitlement checks at
non-visual entry points:

- Resolve every child-facing language through one entitlement-aware effective
  language. When locked, it is always English without overwriting the parent's
  saved Full-language preference.
- Reject any Debug deep link to a premium mini-app unless the injected Debug
  entitlement is unlocked.
- Gate WatchLink sends and incoming watch content when Full is locked.
- Do not activate premium-only watch behavior solely because a stale view is
  still on screen during a refund update.

### Watch gating

Inject the shared entitlement store into `WatchModel`.

- Locked: render only the unlock explanation.
- Unlocked: render `PocketCloudView`.
- Guard `tap(_:)` and `sendVoice(_:)` as well as the view.
- Do not activate the radio or expose the microphone until unlocked.
- If access is revoked while recording, stop and discard the temporary
  recording, dismiss the recorder, stop speech, and return to the locked view.
- Start observing StoreKit on launch so an iPhone purchase or Ask to Buy
  approval unlocks an already-running watch app.

## Implementation sequence

1. Create a local `Cloudmoji.storekit` configuration with the non-consumable.
2. Extend the entitlement state model and test stub.
3. Implement `StoreEntitlementStore` with verified transactions, current
   entitlements, updates, restore, and finishing.
4. Inject the real store in the iOS Release path and explicit stubs in tests.
5. Finish the parent purchase UI and free-launcher parent doorway, including
   loading, pending, failure, retry, restore-not-found, and disabled in-flight
   controls.
6. Centralize the Free/Full mini-app and effective-language policy.
7. Gate phone/watch communication, voice notes, and the watch interface.
8. Preserve Manage Photos access independently of premium access.
9. Add automated StoreKit tests and update UI tests.
10. Add and validate iPhone, iPad, and watchOS app icons.
11. Configure the real product, pricing, localization, Family Sharing, and review
   metadata in App Store Connect.
12. Run sandbox, paired-device, TestFlight, and App Review checks.

## Test strategy

### 1. Fast unit and view-model tests

Use `StubEntitlementStore` or a purpose-built fake; do not open StoreKit dialogs.

- Locked exposes exactly two launcher apps: Words and Count.
- Locked also exposes one separate `For Grown-ups` doorway; it is not a
  mini-app and contains no commercial copy.
- Unlocked exposes all seven launcher apps.
- Locked resolves every preferred language to English; unlocked resolves all
  five language preferences unchanged.
- Relocking while a non-English language is selected immediately returns
  child-facing labels and speech to English without erasing that preference.
- Locking while a premium app is open returns safely to the launcher.
- A locked Debug deep link cannot open a premium app.
- Locked WatchLink sends/receives nothing.
- Locked WatchModel cannot tap, record, speak, or activate its radio.
- Price/loading/purchasing/pending/unlocked/failed states produce the correct
  parent UI and disable the correct controls.
- Cancellation produces no failure message.
- Restore with no entitlement remains locked and says no purchase was found.
- A refund relocks premium content without deleting stored photos.
- Manage Photos remains reachable when locked if photos exist.

### 2. Automated StoreKit tests

Add `Cloudmoji.storekit` to the project and use `StoreKitTest.SKTestSession`
from the iOS test target.

- The product loads and returns a localized `displayPrice`.
- A successful non-consumable purchase unlocks and is finished.
- Relaunch / store recreation recovers the purchased entitlement.
- Ask to Buy remains pending, then unlocks only after approval.
- A transaction created outside the purchase button arrives through
  `Transaction.updates`.
- A refund or revocation relocks access from `Transaction.updates` while the app
  remains open, without requiring an explicit refresh or relaunch.
- Restore finds an existing purchase.
- Restore with no purchase remains locked.
- StoreKit product-load and purchase failures preserve the free experience and
  do not grant access.
- An unrelated product ID never unlocks Full.

Reset the `SKTestSession` before each test so a non-consumable bought in one case
does not make later cases pass accidentally.

### 3. UI tests with the Debug stub

UI tests should not depend on an App Store account or a system purchase sheet.
Every UI test launch pins both the store mode and entitlement explicitly.

- Locked launcher: Words and Count plus the noncommercial `For Grown-ups`
  doorway, with no price, buy, or upgrade copy.
- Tapping the grown-up doorway reveals neither price nor purchase control until
  the parental gate is passed.
- Grown-ups gate must be passed before any price or purchase control exists.
- Locked Full section shows the configured fake localized price and Restore.
- Stub success changes the launcher from two to seven tiles.
- Pending shows “Waiting for approval…” and remains at two.
- Failed purchase shows Retry and remains at two.
- Unlocked state shows seven tiles and no Unlock button.
- Locked language settings show English as included and route the “4 more
  languages” row to the Full explanation.
- Refund simulation while a premium screen is open returns home safely.

Keep one manual UI pass for the real Apple purchase sheet; XCUI tests should not
try to automate App Store credentials.

### 4. Xcode transaction-manager pass

Using the local StoreKit configuration:

- In the transaction manager sidebar, select the Cloudmoji app beneath the
  exact simulator or physical device whose running UI you are observing.
  Transactions belong to that selected test environment; changing a transaction
  beneath another listed device does not change the app on the phone in hand.
- Successful purchase
- User cancellation
- Ask to Buy: pending, approve, and decline
- Interrupted purchase, then Resolve
- Refund
- Purchase created outside the app while it is running
- Relaunch after purchase
- Product unavailable / simulated StoreKit error
- Offline relaunch after a completed purchase

### 5. Sandbox and physical-device pass

The Paid Apps Agreement must be active before sandbox testing.

- Purchase with a Sandbox Apple Account on a real iPhone.
- Kill and relaunch; Full remains unlocked.
- Delete and reinstall; entitlement is recovered automatically.
- Tap Restore explicitly and confirm the authentication path.
- Confirm the real localized price in at least the US and Singapore storefronts.
- Purchase on one device and verify another device on the same Apple Account.
- Test a Sandbox Test Family after Family Sharing is enabled.
- Pair a real Apple Watch: locked before purchase, unlock after purchase,
  relock after a sandbox refund/revocation.
- Confirm the locked watch never asks for microphone permission.
- Confirm the locked watch neither activates WatchConnectivity nor accepts a
  stale emoji or voice-note delivery.
- Confirm an unlocked watch still works with the phone temporarily offline.

### 6. TestFlight and App Review pass

- Test the production App Store Connect product through TestFlight.
- Verify purchase, restore, Ask to Buy, and paired watch behavior once more.
- Provide App Review with exact instructions for reaching the gated purchase.
- State clearly in Review Notes that it is a one-time non-consumable and list
  the five unlocked mini-apps, four languages, Apple Watch, and voice notes.
- The first non-consumable must be submitted with a new app version.

## App Store Connect checklist

- [ ] Paid Apps Agreement active
- [ ] Banking and tax information complete
- [ ] In-App Purchase capability enabled for the app
- [ ] `app.cloudmoji.unlock.full` created as Non-Consumable
- [ ] US base price set to USD 9.99; automatic storefront equivalents reviewed
- [ ] Display name and description localized
- [ ] Review screenshot uploaded from the gated Full Cloudmoji section
- [ ] Family Sharing turned on after final product-ID confirmation
- [ ] Local StoreKit configuration synchronized with the final product
- [ ] iPhone and iPad icon variants verified in a Release archive
- [ ] watchOS icon assigned and verified in the embedded watch app
- [ ] Support URL set to `https://cloudmoji.app/support`
- [ ] TestFlight feedback email set to `kevin.chan@sproutlearn.co`
- [ ] Privacy Policy URL and App Privacy answers reviewed
- [ ] App Review notes include parental-gate and restore instructions
- [ ] IAP added to the first app-version submission

The App Privacy answer can remain “Data Not Collected” only if the final binary
continues to send no personal or device data to the developer or third parties.
StoreKit's transaction processing by Apple does not justify adding unrelated
analytics or account collection.

## Release acceptance criteria

- A new customer can use Words and Count in English indefinitely without seeing
  a purchase prompt in the child area.
- The free launcher makes the grown-up route discoverable without showing a
  price, purchase action, or paid-feature list before the parental gate.
- A parent can see the localized one-time price only after passing the gate.
- One verified purchase unlocks five additional mini-apps, four additional
  languages, the watch, and watch voice notes.
- Purchase approval on another device unlocks through transaction updates.
- Restore works after reinstall and never grants access without an entitlement.
- Pending, cancellation, failure, refund, and offline states are all safe.
- Refunds never delete local content, and stored photos remain parent-manageable.
- No Release launch argument, cached preference, or WatchConnectivity message
  can grant Full without a verified StoreKit entitlement.

## Upgrade-experience plan

### Phase 1 — Clear discovery and comprehension

Implemented:

- one free-only `For Grown-ups` launcher doorway
- direct gated routing from that doorway to the Full explanation
- the plan section moved to the top of Settings
- explicit “free version,” “paid version,” “one purchase,” and “no
  subscription” language
- named benefits and a localized-price call to action
- reassurance that Free remains available indefinitely

### Phase 2 — Prelaunch parent validation

Run five to eight short sessions with parents who have not seen the product.
Give them the free launcher and ask:

1. What is included for free?
2. Is there another version?
3. What does it add?
4. Is the payment recurring?
5. Where would you go to buy or restore it?

The pass condition is that parents answer all five without coaching and reach
the offer in under 20 seconds. Record confusion and wording, not personal or
device data.

### Phase 3 — TestFlight rehearsal

- verify the doorway on small iPhone and large iPad layouts
- verify VoiceOver announces it only as a grown-up route
- confirm no price or purchase control exists before the gate
- test successful, pending, cancelled, failed, restored, and refunded states
- ask internal testers whether the value is clear before they reach the button

### Phase 4 — Postlaunch iteration

Review App Store Connect sales, refunds, reviews, and parent support feedback.
Cloudmoji intentionally has no third-party analytics, so copy experiments
should use sequential TestFlight/release cohorts and direct parent research,
not child-level behavioral tracking.

Potential later improvements belong only in the gated parent area:

- a compact side-by-side Free versus Full comparison
- one parent-only reminder after a parent taps a locked language row
- App Store screenshots that clearly label Free and Full

Do not add child-facing prices, countdowns, repeated popups, artificial
discounts, locked premium tiles, or prompts that pressure a child to involve a
parent.

## Primary Apple references

- [App Review Guidelines](https://developer.apple.com/app-store/review/guidelines/)
- [Kids Category and parental gates](https://developer.apple.com/kids/)
- [Configure In-App Purchases](https://developer.apple.com/help/app-store-connect/configure-in-app-purchase-settings/overview-for-configuring-in-app-purchases/)
- [Create a non-consumable](https://developer.apple.com/help/app-store-connect/manage-in-app-purchases/create-consumable-or-non-consumable-in-app-purchases)
- [Set In-App Purchase pricing](https://developer.apple.com/help/app-store-connect/manage-in-app-purchases/set-a-price-for-an-in-app-purchase/)
- [Family Sharing for In-App Purchases](https://developer.apple.com/help/app-store-connect/configure-in-app-purchase-settings/turn-on-family-sharing-for-in-app-purchases)
- [StoreKit current entitlements](https://developer.apple.com/documentation/storekit/transaction/currententitlements)
- [Restore with AppStore.sync](https://developer.apple.com/documentation/storekit/appstore/sync())
- [StoreKit Testing in Xcode](https://developer.apple.com/documentation/xcode/setting-up-storekit-testing-in-xcode)
- [Sandbox testing](https://developer.apple.com/help/app-store-connect/test-in-app-purchases/overview-of-testing-in-sandbox)
- [Companion watchOS apps and universal purchases](https://developer.apple.com/documentation/watchos-apps/creating-independent-watchos-apps/)
