# Full Cloudmoji Launch Implementation Plan

**Status:** implemented locally; App Store Connect, physical-device sandbox,
and TestFlight launch gates remain

**Owner:** iOS / watchOS

**Target:** first monetized App Store release

**Related decision:** `docs/product/MONETIZATION.md`

## 1. Launch contract

There is one purchase and one entitlement. Do not introduce separate language,
mini-app, Watch, or voice-note purchases.

| Capability | Free | Full Cloudmoji |
|---|:---:|:---:|
| Words | Yes | Yes |
| Count | Yes | Yes |
| English | Yes | Yes |
| Music | No | Yes |
| Flash Cards | No | Yes |
| Animals | No | Yes |
| Photos and Camera | No | Yes |
| Sleepy Cloud | No | Yes |
| Mandarin Chinese | No | Yes |
| Bahasa Melayu | No | Yes |
| Japanese | No | Yes |
| Tagalog | No | Yes |
| Apple Watch emoji exchange | No | Yes |
| Apple Watch voice notes | No | Yes |

The product is a StoreKit 2 non-consumable:

```text
Product ID: app.cloudmoji.unlock.full
Reference name: Full Cloudmoji
US base price: USD 9.99
```

The interface always uses `Product.displayPrice`. `$9.99` appears in product
planning and App Store Connect configuration, never in shipping UI.

## 2. Experience rules

### Child-facing area

- A free child sees only Words and Count.
- The free launcher also shows one `For Grown-ups` doorway. It is not a
  mini-app, contains no price or upgrade copy, and opens the parental gate.
- Do not show prices, disabled premium tiles, upgrade buttons, paid-feature
  lists, or commercial prompts in the launcher or a mini-app.
- A Full child sees the existing seven-app launcher.
- Only a successfully completed Grown-ups gate reveals purchase information.

### Parent-facing area

- The purchase explanation appears only after the existing parental gate.
- State what Free includes before describing Full.
- Say “one-time purchase” and “no subscription.”
- Keep Restore Purchase visible in the Full Cloudmoji screen.
- Never enable a purchase action before a localized product and price load.

### Language behavior

- Free content renders and speaks only in English.
- Preserve the parent's last Full-language selection while access is locked.
  Do not overwrite it with English.
- Derive one `effectiveLanguage`: English when locked, otherwise the saved
  preference.
- Every child-facing label, spoken word, launcher caption, counting phrase,
  Watch message, and content query must use `effectiveLanguage`.
- When Full is revoked while another language is active, stop current speech
  and move immediately to English without deleting the preference.

### Apple Watch behavior

- The embedded watch app can still be installed by a free customer; it cannot
  be omitted from only that customer's download.
- When locked, the watch renders only the Full Cloudmoji explanation.
- The locked watch does not show the emoji pager or microphone control, request
  microphone permission, activate the Cloudmoji WatchConnectivity session,
  speak, send, receive, record, or retain a voice note.
- Both watch features are independently guarded in code even though they use
  the same Full entitlement. This prevents voice notes from accidentally
  becoming free if Watch scope changes later.
- The watch verifies StoreKit directly. A phone message can request a refresh
  but can never grant access.

## 3. Access architecture

### 3.1 StoreKit source of truth

Implement `StoreEntitlementStore` in
`ios/CloudmojiCore/Sources/CloudmojiCore/Entitlements.swift`.

Make it `@MainActor` and observable. It owns:

- the immutable Full product ID
- loaded `Product` and `displayPrice`
- `accessState`: checking, locked, or unlocked
- `operationState`: idle, purchasing, pending, or restoring
- parent-facing error / result state
- the `Transaction.updates` observation task

Launch order:

1. Start the `Transaction.updates` listener.
2. Resolve and finish relevant verified unfinished transactions.
3. scan `Transaction.currentEntitlements`
4. publish locked or unlocked
5. load the product separately so an unavailable price never relocks an
   already verified purchase

Only an expected, verified, non-revoked transaction grants Full. Finish a
verified transaction only after applying its access.

`cm_premium_unlocked` must not grant access in Release. Retain it only behind
the explicit `-cm_use_stub_entitlements YES` Debug/test switch. StoreKit already
maintains transaction information for offline entitlement reconciliation, so a
plain UserDefaults entitlement cache is unnecessary.

### 3.2 Capability policy

Add a small, pure `AppAccessPolicy` in the iOS app target. It receives
`hasFullAccess` and answers:

```swift
func canUse(_ app: MiniApp) -> Bool
func effectiveLanguage(preferred: Language) -> Language
var canUseWatch: Bool
var canUseWatchVoiceNotes: Bool
```

Rename `MiniApp.isPremium` to `requiresFull` and pin this exact set in a test:

```text
Music, Flash Cards, Animals, Photos, Sleepy Cloud
```

Keep commerce state out of the individual mini-app views. Views ask `AppModel`
for permitted apps and the effective language; they do not query StoreKit.

### 3.3 Access-change coordinator

Add one `AppModel.handleAccessChange(isFull:)` transition path.

On unlock:

- recompute the launcher
- restore the saved preferred language
- allow the phone Watch link to activate

On relock or refund:

- stop current speech and audio
- return home if a Full mini-app is open
- force child-facing output to effective English
- deactivate or ignore phone/watch transport
- clear any in-memory incoming voice note
- stop and discard an in-progress watch recording
- preserve photos and all parent settings

Do not spread refund/relock side effects among individual screens.

## 4. iPhone and iPad implementation

### 4.1 Launcher and route enforcement

Files:

- `ios/Cloudmoji/Cloudmoji/Views/Launcher/MiniApp.swift`
- `ios/Cloudmoji/Cloudmoji/Views/Launcher/AppModel+MiniApps.swift`
- `ios/Cloudmoji/Cloudmoji/ContentView.swift`
- `ios/Cloudmoji/Cloudmoji/AppModel.swift`

Changes:

- mark the five Full mini-apps with `requiresFull`
- make `visibleMiniApps` return exactly Words and Count while locked
- guard every non-launcher entry path, including Debug deep links
- if an open app becomes unavailable, close it before the next rendered frame
- keep the launcher free of commercial marks

Acceptance:

- free launcher accessibility tree contains Words and Count and none of the
  five Full app labels
- Full launcher contains all seven
- a locked direct route cannot instantiate a Full screen

### 4.2 Effective-language enforcement

Files:

- `ios/Cloudmoji/Cloudmoji/AppModel.swift`
- every child view currently reading `model.settings.language`
- `ios/Cloudmoji/Cloudmoji/WatchLink.swift`

Changes:

- add `effectiveLanguage`
- replace direct preferred-language reads in child content, TTS, launcher
  labels, count grammar, Flash Cards, Animals, Photos, Sleepy Cloud, and Watch
  context
- keep the persisted preferred language and enabled-language set intact

Use a repository check during implementation:

```sh
rg -n 'settings\.language|settings\.enabledLanguages' ios/Cloudmoji/Cloudmoji
```

Every surviving direct read must be parent-settings UI or have an explicit
comment explaining why it is not child-facing access.

### 4.3 Grown-ups settings

File:

- `ios/Cloudmoji/Cloudmoji/Views/SettingsView.swift`

Add a `YOUR CLOUDMOJI PLAN` section at the top of Settings.

Locked summary:

```text
Cloudmoji Free
You’re using the free version.
Included: Words and Count in English.

See the paid Full version — {localized price}
Full Cloudmoji is the paid version. One purchase unlocks five more mini-apps,
four more languages and Apple Watch. There is no subscription.
```

The row navigates to a dedicated `FullCloudmojiView`.

Locked language section:

```text
LANGUAGE

English                                      Included
4 more languages with Full                         🔒
```

The second row opens `FullCloudmojiView`. Do not leave four disabled toggles on
screen. When unlocked, show the existing language toggles and starting-language
picker.

Unlocked plan summary:

```text
Full Cloudmoji
Everything is unlocked on this device.              ✓
```

Keep Manage Photos parent-accessible whenever local photos exist, including
after a refund. Relocking must never delete them.

### 4.4 Dedicated Full Cloudmoji screen

Add:

- `ios/Cloudmoji/Cloudmoji/Views/FullCloudmojiView.swift`
- focused view-model or presentation tests in `CloudmojiTests`

Content order:

```text
Upgrade to Full Cloudmoji
Full Cloudmoji is the paid version.
One purchase. No subscription.

Your free version includes Words and Count in English.
You can keep using the free version for as long as you like.

Full Cloudmoji adds:

Five more mini-apps
Music, Flash Cards, Animals, Photos and Sleepy Cloud

Four more languages
Mandarin Chinese, Bahasa Melayu, Japanese and Tagalog

Apple Watch
Share emoji moments and send a short voice note from your watch.

Unlock Full Cloudmoji — {localized price}
Restore Purchase

Purchases are handled by Apple. No Cloudmoji account is needed.
```

If Family Sharing is enabled in App Store Connect, add:

```text
One-time purchase • Family Sharing
```

Do not show “Family Sharing” before that irreversible App Store Connect choice
has actually been enabled.

Accessibility:

- use a real `Button` for purchase, restore, and retry
- expose the complete localized price in the purchase button label
- keep Dynamic Type layouts readable without horizontal clipping
- group each benefit title and explanation semantically
- disable purchase and restore during an active operation

### 4.5 Purchase-state copy

| State | Exact UI |
|---|---|
| Product loading | `Checking price…` with purchase disabled |
| Product unavailable | `The Full Cloudmoji price could not be loaded. Check your connection and try again.` + `Retry` |
| Purchasing | `Completing purchase…` with both controls disabled |
| Pending / Ask to Buy | `Waiting for approval… Your purchase will unlock automatically when approved.` |
| User cancelled | Return to idle with no error alert |
| Purchase failed | `Cloudmoji could not complete the purchase. Your free version is still available. Please try again.` |
| Restore in progress | `Checking your Apple Account…` |
| Restore not found | `No Full Cloudmoji purchase was found for this Apple Account.` |
| Unlocked | `Full Cloudmoji is unlocked on this device.` |

Do not place StoreKit error codes in parent-facing UI. Log them locally in
Debug builds without adding analytics.

### 4.6 Tutorial, About, and App Store disclosure

Files:

- `ios/Cloudmoji/Cloudmoji/Views/TutorialView.swift`
- `ios/Cloudmoji/Cloudmoji/Views/AboutView.swift`

Update any claim that all seven apps or all five languages are free.

Child-facing tutorial copy should remain non-commercial:

```text
Start with Words and Count in English.
A grown-up can find plan details in Grown-ups.
```

Do not show a price or purchase button in the tutorial.

Parent-facing About copy:

```text
The free version includes Words and Count in English. Full Cloudmoji is a
one-time purchase that adds Music, Flash Cards, Animals, Photos, Sleepy Cloud,
four more languages and the Apple Watch experience, including short voice
notes. There is no subscription.
```

App Store description disclosure:

```text
The free version includes Words and Count in English. A one-time In-App
Purchase unlocks five more mini-apps, four additional languages and the
complete Apple Watch experience, including short voice notes.
```

App Store Connect IAP metadata:

```text
Display name: Full Cloudmoji
Description: Unlock apps, languages and Apple Watch
```

## 5. Apple Watch implementation

Files:

- `ios/Cloudmoji/CloudmojiWatch/CloudmojiWatchApp.swift`
- `ios/Cloudmoji/CloudmojiWatch/PocketCloudView.swift`
- `ios/Cloudmoji/CloudmojiWatch/RecordView.swift`
- `ios/Cloudmoji/CloudmojiWatch/VoiceRecorder.swift`
- `ios/Cloudmoji/CloudmojiWatch/WatchModel.swift`
- `ios/Cloudmoji/CloudmojiWatch/WatchRadio.swift`
- `ios/Cloudmoji/Cloudmoji/WatchLink.swift`

Changes:

1. Create and observe the same `StoreEntitlementStore` in the watch app.
2. Render `LockedWatchView` until Full is verified.
3. Move `WatchModel.activate()` behind verified access.
4. Add defense-in-depth guards to emoji tap, radio receive, speech, recording,
   and `sendVoice`.
5. Add a deactivation path or make every callback reject work while locked.
6. On revocation, stop recording and speech, delete the temporary clip, dismiss
   `RecordView`, and show the locked view.
7. On unlock through `Transaction.updates`, activate without requiring an app
   relaunch.

Locked watch copy:

```text
Full Cloudmoji required

Unlock Full Cloudmoji on your iPhone to use Cloudmoji on Apple Watch, share
emoji moments and send short voice notes.

On iPhone:
Cloudmoji → Grown-ups → Full Cloudmoji
```

There is no purchase button on the watch and no microphone authorization
request while locked.

## 6. App icons

### Audit result

- iOS: `icon-1024.png` is present, 1024 × 1024, RGB, and has no alpha.
- A no-signing device build generated both `AppIcon60x60@2x.png` for iPhone and
  `AppIcon76x76@2x~ipad.png` for iPad.
- watchOS: the AppIcon catalog contains a 1024 × 1024 slot with no filename,
  and no icon image exists in the watch asset catalog.
- The built watch app contains no watch icon. This is a launch blocker even
  though the build currently emits no warning.

### Required fix

1. Export a dedicated 1024 × 1024 RGB, no-alpha watch icon from the existing
   Cloudmoji icon source. Keep it square and unmasked; watchOS applies the
   circular mask.
2. Keep the cloud and stars inside the circular safe area. The current navy
   background is suitable; do not replace it with black.
3. Add it as:
   `CloudmojiWatch/Assets.xcassets/AppIcon.appiconset/icon-watch-1024.png`.
4. Add the filename to the watch `Contents.json`.
5. Verify at actual small watch sizes and on 40/41, 44/45/46, and 49 mm
   simulator families or the available equivalents.

The existing iOS universal icon can remain the source for iPhone and iPad. Dark
and tinted variants are optional for this release, not blockers.

### Automated validation

Add `ios/Cloudmoji/scripts/validate-app-icons.sh` that fails when:

- an AppIcon entry has no filename
- a referenced image is missing
- the universal source is not the declared dimensions
- an app icon contains alpha
- a Release build does not contain iPhone, iPad, and watch icon outputs

Run it in the pre-archive checklist and CI.

## 7. Automated test plan

### Unit and presentation tests

Add or update:

- `LauncherViewTests`
- `AppModelTests`
- `WatchLinkTests`
- `AboutViewTests`
- `TutorialViewTests`
- new `AccessPolicyTests`
- new `FullCloudmojiViewTests`
- watch-model tests in a watch-test target or a shared pure-model test target

Required cases:

- locked app set is exactly Words and Count
- Full app set is exactly all seven
- all five possible saved languages resolve to English while locked
- all five resolve unchanged while unlocked
- relock preserves preferred-language storage
- a Full deep link is rejected while locked
- relock closes an open Full app and stops speech
- locked WatchLink rejects emoji and voice traffic in both directions
- locked WatchModel cannot activate, speak, record, or send
- voice-note permission is never requested while locked
- parent UI renders every purchase state and exact message
- Manage Photos survives relock without deleting data

### StoreKitTest

Create a synchronized local `Cloudmoji.storekit` containing the exact
non-consumable product. Use `SKTestSession` and reset it before every test.

Required cases:

- localized product and price load
- successful purchase unlocks and transaction is finished
- relaunch recovers the entitlement
- offline relaunch after an earlier verified purchase
- Ask to Buy remains locked, then unlocks after approval
- purchase created outside the app arrives through transaction updates
- cancellation produces no error
- product-load and purchase failures preserve Free
- restore finds an existing purchase
- restore-not-found remains locked
- refund and revocation relock without deleting data
- unrelated and unverified transactions never unlock

### XCUI

Pin every UI test to explicit Debug stub mode.

Required flows:

- Free launcher has two mini-app tiles plus a noncommercial `For Grown-ups`
  doorway
- the grown-up doorway has no price, buy, or upgrade copy
- the grown-up doorway routes through the real gate directly to the Full screen
- purchase content is absent before the parental gate
- gated plan summary states the Free limitation
- paywall shows fake localized price, benefits, purchase, and restore
- purchase changes two tiles to seven
- Free language UI exposes English plus the Full-languages row
- Full language UI exposes all five settings
- pending and failure remain on two tiles
- refund from an open Full screen safely returns home
- iPad layout supports Dynamic Type without clipped paywall copy

## 8. Device, sandbox, and TestFlight matrix

### Local StoreKit / simulator

- Select the app beneath the exact running simulator or device in Xcode's
  transaction-manager sidebar before purchasing, refunding, or revoking.
- iPhone portrait and landscape
- iPad portrait, landscape, and split view
- smallest supported watch
- largest supported watch
- Dynamic Type through accessibility sizes
- VoiceOver purchase and restore order
- Reduce Motion

### Sandbox on physical devices

- purchase on iPhone
- kill / relaunch and delete / reinstall
- explicit restore
- Ask to Buy approval and decline
- refund / revocation
- US and Singapore storefront price display
- second device using the same Apple Account
- Family Sharing test family, if enabled
- paired Watch locked before purchase and live-unlocked afterward
- locked Watch never requests microphone access
- unlocked emoji and voice-note round trip
- revocation while recording a voice note
- temporary phone disconnection after a verified Watch entitlement

### TestFlight

- repeat purchase, restore, pending, refund, and paired-Watch flows using the
  production App Store Connect product
- inspect the installed icons on iPhone, iPad, Watch app grid, Watch list view,
  notifications where applicable, and App Store surfaces
- confirm the final privacy copy matches the binary's actual device-to-device,
  memory-only voice-note behavior

## 9. App Store Connect and review

Before the first monetized submission:

1. Confirm Paid Apps Agreement, tax, and banking status.
2. Create `app.cloudmoji.unlock.full` as Non-Consumable.
3. Set the US base price to USD 9.99 and review automatic storefront prices.
4. Add localized display name and description.
5. Upload an IAP review screenshot from the gated Full Cloudmoji screen.
6. Enable Family Sharing only after the product ID and lifetime scope are
   final; Apple does not let this choice be reversed.
7. Attach the IAP to the app-version submission.
8. Update App Store description and screenshots so paid functionality is
   clearly identified.
9. Set the App Store Support URL to `https://cloudmoji.app/support` and the
   TestFlight feedback email to `kevin.chan@sproutlearn.co`.
10. Reconfirm the Kids Category age band, privacy policy, and App Privacy answers.

Review Notes:

```text
Full Cloudmoji is a one-time, non-consumable In-App Purchase.

To reach it:
1. Open Cloudmoji.
2. Tap Grown-ups.
3. Complete the parental gate.
4. Tap Full Cloudmoji.

Free includes Words and Count in English. Full adds Music, Flash Cards, Animals,
Photos, Sleepy Cloud, Mandarin Chinese, Bahasa Melayu, Japanese, Tagalog, and
the Apple Watch experience including short watch-to-phone voice notes.

Voice notes are transferred directly between the paired Watch and iPhone, kept
only in memory for the current session, and are not sent to Cloudmoji servers.
The locked Watch does not request microphone permission.

Restore Purchase is available on the same Full Cloudmoji screen.
```

## 10. Implementation order and release gates

### Batch A — Policy first

- add access policy and effective language
- update mini-app set
- add unit tests

Gate: Free is exactly Words + Count + English with no StoreKit implementation.

### Batch B — Parent UI

- build plan summary and `FullCloudmojiView`
- update Settings, Tutorial, and About copy
- add state/presentation tests and XCUI flows

Gate: every purchase state is usable with the Debug stub and remains behind the
parental gate.

### Batch C — StoreKit

- implement verified transaction lifecycle
- add local StoreKit configuration and StoreKitTest coverage
- remove Release entitlement bypasses

Gate: automated purchase, pending, restore, relaunch, refund, and failure cases
all pass.

### Batch D — Watch

- add direct watch entitlement observation
- gate WatchConnectivity and voice notes
- implement live unlock and revocation cleanup

Gate: a locked Watch cannot activate any child or microphone behavior.

### Batch E — Icons and archive

- add watch icon
- add icon validator
- inspect phone, tablet, and watch products from a Release archive

Gate: all three platforms show the Cloudmoji icon in installed builds.

### Batch F — Commerce configuration and launch rehearsal

- configure App Store Connect and Family Sharing decision
- complete sandbox, physical-device, TestFlight, privacy, and App Review passes

Gate: every item below is true.

## 11. Definition of launch-ready

- [ ] Free exposes only Words and Count in English
- [ ] Full exposes the other five apps and four languages
- [ ] Full gates all Watch behavior, including voice notes
- [ ] no purchase content exists before the parental gate
- [ ] localized App Store price is used everywhere
- [ ] purchase, pending, restore, failure, refund, and offline paths pass
- [ ] Release has no stub or cached-Boolean entitlement bypass
- [ ] relocking never deletes photos or preferences
- [ ] locked Watch never requests microphone permission
- [ ] iPhone, iPad, and watchOS icons are present in the Release archive
- [ ] App Store copy clearly discloses Free and Full scope
- [ ] privacy copy accurately describes Watch voice notes
- [ ] physical-device sandbox and TestFlight passes are signed off
- [ ] App Review can reach, understand, purchase, and restore Full Cloudmoji
