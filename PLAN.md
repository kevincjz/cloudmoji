# PLAN.md — Cloudmoji Play Area: Launcher, Mini-Apps, Premium

## Context

Cloudmoji today is a two-mode toddler app (Words / Count), feature-complete and
on both of Kevin's iPhones. This expands it into a play area: an iPhone-style
launcher of seven mini-apps — Words and Count become tiles, plus Sleepy Cloud 🌙
and Instrument Pad 🎹 (free), Flash Cards ⚡, Animal Sounds 🔊, Photos+Camera 📷
(premium) — with haptics throughout and a stubbed entitlement. Kevin's decisions:
premium tiles **hidden until unlocked** (upsell is a Settings row behind the
gate); a **child-usable 64pt cloud home button** in every mini-app; **I source
CC0 animal sounds** (his ear-approval folds into the final device pass).

**Execution contract:** Steps 0–6 run end-to-end with no intervention between
steps. Anything only checkable on a physical device is NOT a step gate — it
accumulates into the single DEVICE PASS checklist at the bottom, done once at
the end. **Step 7 (StoreKit) is out of scope for this run** — sandbox purchase
testing needs Kevin; its section below records the fixed design so Steps 1–6
need no rework when it lands. Each step is one commit gate; copy and its
pinning tests always move in the same commit.

---

## §Conflicts (verified at HEAD fb17652 — goes into PLAN.md verbatim)

1. **CLAUDE.md forbids this** ("No payments or paywalls"); the design spec's
   non-goals repeat it. Step 1 amends both deliberately — otherwise every
   subagent reading CLAUDE.md fights the premium work.
2. **About's privacy copy is factual and test-pinned**; sentences become false
   at specific steps: "Seven small settings … That is the whole list" (Step 1
   adds an 8th key), CAMERA section owed (Step 6), "nothing to buy" (Step 7,
   excluded). "Never asks for the microphone" stays true throughout —
   capture is video-input-only. Pre-existing falsehood found in recon: "the
   one typeface we ship" (Nunito made it two) — fixed Step 0.
   `AboutViewTests.privacyTextIsTheIosOne` (case-insensitive bans incl. bare
   "http") and `AboutUITests.testNothingOnTheAboutScreenLeavesTheApp` go red
   on drift.
3. **Tutorial copy** says "Two modes, along the bottom";
   `TutorialViewTests.namesBothModes` iterates `AppMode.allCases`. The
   launcher retires `AppMode`; the test is rewritten to iterate free mini-apps
   only (premium is never advertised in the tour).
4. **Four of five UI suites hard-require landing in Words mode** (gate on
   `emoji-🍎`/`tab-count` in 30s). Step 0 lands `-cm_open` while it is a no-op
   so the launcher day breaks nothing.
5. **Audio session is three lines in `CloudmojiApp`**; TTS is the only audio.
   Step 3 extracts the one owner (`AudioDirector`) before the synth exists.
6. **The gate overlay attaches at `RootContent`'s root, outside the mode
   switch** — keep it outside the new switch so it covers grid, mini-apps and
   home button (also the Kids-Category commerce-gate requirement).
7. **Kids Category:** no third-party SDKs; camera imagery never leaves the
   device (app container, backup-excluded, never the photo library).
   `PrivacyInfo.xcprivacy` doesn't exist; zero usage-description keys — Step 6
   adds both.
8. **Web/iOS parity ends here** (launcher is iOS-only; scope note in
   CLAUDE.md). `src/data/` stays the single content source — Flash Cards and
   Animal Sounds consume `EmojiRepository`, never Swift-authored word lists.

---

## §Documentation alignment contract (Kevin's explicit requirement)

Every document that describes the app tracks the new direction, updated in the
same commit as the code that changes the truth — never later, never batched:

**CLAUDE.md (Step 1, exact edits):**
- "What This Is": *"No backend, no accounts, no payments. Ship fast, learn
  fast."* → *"No backend, no accounts. One optional purchase (StoreKit 2 only,
  behind the parental gate) unlocks the premium mini-apps. Ship fast, learn
  fast."* Add one paragraph describing the launcher: seven mini-apps, free
  four visible always, premium three hidden until unlocked, cloud home button.
- "What NOT to Build": *"No payments or paywalls"* → *"No commerce except one
  StoreKit 2 non-consumable behind the parental gate — no subscriptions, no
  consumables, no third-party payment SDKs"*. Keep "No user accounts or
  login" and "No backend or database" verbatim.
- Touch-target rule 1's child list gains: launcher tiles, the cloud home
  button, instrument pads, flash-card choices, the camera shutter. (The "tab
  bar" entry is retired with the tab bar.)
- New scope note: the launcher and mini-apps are iOS-only; the web app remains
  the two-mode product and is no longer the reference for iOS structure —
  `src/data/` remains the single content source for both.
- `tests/review-fixes.spec.ts` note re-checked (it asserts the child list).

**Privacy policy / About (per-step, truth-preserving):**
- Step 0: "one typeface" → "two typefaces" (already false today).
- Step 1: settings enumeration → **eight** keys (`cm_premium_unlocked` named
  as "whether the extra mini-apps are unlocked"); `how-to-use` FAQ rewritten
  to launcher framing. *"Nothing to buy" stays* — the stub moves no money, so
  the sentence remains true until real StoreKit (Step 7, excluded).
- Step 6: CAMERA section (in-app storage, backup-excluded, parent-deletable);
  "never asks for the microphone" retained and still true (video-only input).
- Step 7 (excluded, pre-drafted in PLAN.md): purchase paragraph + "makes no
  connections of its own" softening.
- The sweep tests (`privacyTextIsTheIosOne`, `testNothingOnTheAboutScreen…`)
  are the enforcement mechanism and update with each copy change.

**Tutorial (Step 1):** step list becomes tap → launcher (free four only) →
home button → typing-row → mute → settings(+unlock mention); `namesBothModes`
successor iterates `MiniApp.allCases.filter { !$0.isPremium }`.

**Design docs:** spec non-goals amended (Step 1); `docs/design/DESIGN_SYSTEM.md`
gains the launcher tile + home button + moonlight/lavender tokens (Steps 1–2)
— it is the source of truth every implementer reads, and stale entries there
have already bitten this project twice.

---

## §Step 0 — Stabilise + deep-link shim

**Modify**
- `ios/Cloudmoji/Cloudmoji/ContentView.swift` — in `RootContent.init`,
  `#if DEBUG`: read `UserDefaults.standard.string(forKey: "cm_open")`
  (NSArgumentDomain; same documented pattern as `-cm_reset_persisted_settings`)
  → preselect `AppMode(rawValue:)`.
- All five UI-suite `launch()` helpers (`AboutUITests.swift:41`,
  `ParentalGateUITests.swift:23`, `WordsModeUITests.swift:65`,
  `CountModeUITests.swift:65`, `TutorialUITests.swift:70`) — append
  `"-cm_open", "words"` (Count suite: `"count"`, and its helper stops tapping
  `tab-count` to arrive). No-op today; load-bearing at Step 1.
- `ios/Cloudmoji/Cloudmoji/Views/AboutView.swift` — "the one typeface we
  ship" → "the two typefaces we ship"; `AboutViewTests` updated.
- `src/data/languages.ts` — `webHidden?: true` on `tl`; web pickers filter on
  it (`src/components/` language UI); generator + iOS untouched.
- Repair whatever the (unrun-since-rewrite) UI suites turn up; diagnose the
  fresh-install "Animals selected after tutorial" anomaly on the current root.
- `git add reference/breathing-cloud.jsx` (Kevin's Sleepy Cloud prototype).

**Done when (all programmatic)**
- [ ] All 5 UI suites green run individually; unit suite ≥197, package 72.
- [ ] `grep -c "cm_open" ios/Cloudmoji/CloudmojiUITests/*.swift` ≥ 5.
- [ ] `grep "two typefaces" ios/.../AboutView.swift` hits; "one typeface" gone.
- [ ] Playwright suite green; `tl` absent from web language picker
  (`tests/` assertion added), still 5 languages in `EmojiData.json` parity.
- [ ] `git log --oneline -1` shows the Step 0 commit; tree clean.

## §Step 1 — Launcher shell, home button, stubbed entitlement

**Create**
- `ios/Cloudmoji/Cloudmoji/Views/Launcher/MiniApp.swift` — `enum MiniApp:
  String, CaseIterable, Identifiable { case words, count, sleepy, instrument,
  flashCards = "flashcards", animalSounds = "animalsounds", photos }` +
  `icon` (🗣️🧮🌙🎹⚡🔊📷), `label`, `isPremium`. Raw values are the
  accessibility-id/deep-link contract.
- `ios/Cloudmoji/Cloudmoji/Views/Launcher/LauncherView.swift` — `ModeHeader`
  (now the canonical ⚙️/mute/language home) over an eager tile grid; pure
  `static func rows(for: [MiniApp], columns: Int) -> [[MiniApp]]` chunks
  4→2×2, 7→2/2/2/1-centred (portrait), 4-per-row compact. Not `LazyVGrid`
  (cannot centre a partial row).
- `ios/Cloudmoji/Cloudmoji/Views/Launcher/LauncherTile.swift` — `Button`,
  `Theme.surface` plate, `PressScale(scale: 0.9)`, `.contentShape(Rectangle())`,
  `Haptics.tap()`, id `launcher-tile-<raw>`, ~150pt square portrait.
- `ios/Cloudmoji/Cloudmoji/Views/CloudHomeButton.swift` —
  `HomeButtonMetrics.side = 64`; circle, `CloudMascot(mood: .happy, size: 44)`
  as glyph, `PressScale(0.9)`, `.contentShape(Circle())`, id `home-btn`,
  label "Home", `Haptics.tap()` then action.
- `ios/CloudmojiCore/Sources/CloudmojiCore/Entitlements.swift` —
  `public enum PurchaseOutcome`; `@MainActor public protocol
  EntitlementProviding: AnyObject, Observable { isUnlocked, priceText,
  purchase(), restore(), startObserving() }`; `StubEntitlementStore`
  (`@Observable`, injected `UserDefaults`, key `cm_premium_unlocked`, read in
  init / written in didSet — `SettingsStore`'s exact pattern, so
  `-cm_premium_unlocked YES` pins it and the reset argument wipes it).
- `ios/Cloudmoji/Cloudmoji/Views/Launcher/AppModel+MiniApps.swift` —
  `var visibleMiniApps: [MiniApp]` filtering on `entitlements.isUnlocked`
  (app-target extension; `MiniApp` must not leak into Core).

**Wiring** (`ContentView.swift`)
- `RootContent` state: `@State private var active: MiniApp?` (nil = launcher);
  deep-link shim now parses `MiniApp` (words/count raw values unchanged →
  Step 0 suite edits keep working).
- `switch active`: nil → `LauncherView(apps: model.visibleMiniApps, onOpen:
  open, onParent: openParentDoor)`; each case → `hosted { <MiniAppView> }`.
  `hosted{}` overlays `CloudHomeButton(action: goHome)` bottom-leading
  (12pt padding) and applies 88pt bottom `safeAreaPadding` to content.
  Teardown-on-switch semantics preserved. Gate overlay stays OUTSIDE the
  switch. `open(_:)` = `model.speech.cancelAll()` then set; `goHome()` =
  cancel + nil (gains `audio.detach()` in Step 3).
- Delete `Views/ModeTabBar.swift` (+`AppMode`); `SideRail` loses tab footer
  and `mode/onSelectMode`; `WordsView`/`CountView` lose those params.
- `AppModel.swift`: `let entitlements: any EntitlementProviding`, init default
  `StubEntitlementStore()`.
- `SettingsView.swift`: "More mini-apps" section — locked: names the three
  premium apps + `settings-unlock-btn` + `settings-restore-btn` (44pt);
  unlocked: `settings-unlocked-row` "Full Cloudmoji unlocked ✓".
- `TutorialView.steps`: `tap` → `launcher` (replaces `modes`; names free four
  ONLY) → `home` (new) → `typing-row` → `mute` → `settings` (+ "and unlock
  more mini-apps"). `AboutView` `how-to-use` rewritten; settings enumeration →
  eight keys; CLAUDE.md "No payments or paywalls" → "Commerce only via
  StoreKit 2, only behind the parental gate; no accounts ever" + iOS-only
  scope note; spec non-goals updated.

**Done when (all programmatic)**
- [ ] Build green, zero warnings on new files.
- [ ] New `LauncherViewTests`: `rows(for:columns:)` 4→[[2],[2]],
  7→[[2],[2],[2],[1]]; `visibleMiniApps` = 4 stub-locked / 7 stub-unlocked —
  each mutation-tested.
- [ ] New `LauncherUITests` green: bare launch → exactly 4 `launcher-tile-*`,
  each ≥64pt both axes, ≥8pt gaps, `assertMeetsChildMinimum`-style non-empty
  guard; with `-cm_premium_unlocked YES` → exactly 7; tap `launcher-tile-words`
  → `emoji-🍎` exists; `home-btn` exists in every mini-app and returns to a
  visible `launcher-tile-words`; gate opened from launcher header covers tiles
  (tile tap while gated does nothing).
- [ ] All prior suites green with only their `launch()` lines changed;
  `CountModeUITests` tab cases now live in `LauncherUITests` (navigation
  promise preserved, mechanism changed).
- [ ] `grep -r "ModeTabBar\|AppMode" ios/Cloudmoji/Cloudmoji/` → 0 hits.
- [ ] Tutorial/About tests updated and green in the same commit
  (`namesBothModes` successor iterates `MiniApp.allCases.filter { !$0.isPremium }`).

## §Step 2 — Sleepy Cloud 🌙 (free) — port of `reference/breathing-cloud.jsx`

**Create**
- `ios/Cloudmoji/Cloudmoji/Views/Sleepy/SleepyCloudView.swift` — duration
  picker first (2/5/10 min, "Grown-up picks the time", buttons at 64pt — the
  prototype's 56 loses to the child-tap rule), then session: breathing cloud,
  "breathe in"/"breathe out" labels (SILENT by design — no TTS), 14 twinkling
  stars, progressive dim across the session, thin progress line, end state =
  asleep (zzz), loop stops.
- `ios/Cloudmoji/Cloudmoji/Views/Sleepy/BreathingCloud.swift` — own view
  reusing mascot body geometry + Theme mascot colours; sleepy faces (closed
  arcs, o-mouth inhale, zzz) do NOT enter `MascotMood` (raw values are a
  UI-test contract; `arbitrate` is product law). Prototype timings verbatim:
  4s in / 2s hold / 6s out, cosine ease, scale 0.75→1.1.
- Pure `static func breathState(at t: TimeInterval) -> (scale: Double,
  phase: BreathPhase)` driven by `TimelineView(.animation)` (rAF analogue —
  no timer drift, no `didSet` re-entry).

**Modify**
- `Theme.swift`: `moonlight` #A8D6FF, `lavender` #C4B5FD (comment-scoped to
  Sleepy Cloud).
- `ContentView.swift`: `case .sleepy: hosted { SleepyCloudView() }`.
- Idle timer: `UIApplication.isIdleTimerDisabled = true` during a session,
  restored on `onDisappear`, on backgrounding, AND on reaching asleep (locking
  then is desired — the room goes dark). The leak is the step's hardest bug.

**Done when (all programmatic)**
- [ ] `SleepyCloudTests`: `breathState` at t=0 → (0.75, inhale); t=4 → (1.1,
  hold); t=6 → (1.1, exhale start); t=12 → (0.75, cycle); t≥duration → asleep.
  Idle-timer pairing tested via an injected flag-writer spy (never the real
  UIApplication in tests). Mutation-tested.
- [ ] `LauncherUITests` smoke: `-cm_open sleepy` → duration buttons ≥64pt
  (non-empty guard), tap "2 min" → session view appears, `home-btn` returns.
- [ ] Full unit suite green; screenshot artifact captured via `simctl` for the
  device-pass review (dimming feel judged there, not here).

## §Step 3 — AudioDirector + Instrument Pad 🎹 (free)

**Create**
- `ios/Cloudmoji/Cloudmoji/AudioDirector.swift` — `@MainActor final class`,
  owned by `AppModel` (`let audio`). Sole `AVAudioSession` toucher: the three
  lines MOVE here from `CloudmojiApp.init`. API: `activateSession()`,
  `handleScenePhase(_:)` (.active → setActive + restart engine iff client
  attached; .background → stop), `attach(_ client: EngineClient)`/`detach()`,
  `playTone(_ pad: Int)`, `playSound(_ file: AVAudioFile)`. Observes
  `AVAudioSession.interruptionNotification`; ended-interruption re-runs the
  same recovery as foregrounding. TTS keeps its synthesizer; no API needed.
- `ios/Cloudmoji/Cloudmoji/ToneBuffer.swift` — pure
  `static func make(frequency: Double, sampleRate: Double) -> AVAudioPCMBuffer`
  (triangle wave, fast attack, ~1.2s exponential decay). Engine = 8
  pre-rendered buffers, one `AVAudioPlayerNode` per pad (free polyphony,
  one-`scheduleBuffer` latency). Rejected `AVAudioSourceNode` (real-time
  callback under MainActor-default Swift 6 = priority-inversion trap) and
  `AVAudioUnitSampler` (needs assets for no gain).
- `ios/Cloudmoji/Cloudmoji/Views/Instrument/InstrumentPadView.swift` +
  `InstrumentPad.swift` — 2×4 portrait / 4×2 compact, pads ≥72pt/≥8pt,
  Theme accent rotation, `PressScale(0.85)`, sound on touch-DOWN
  (`DragGesture(minimumDistance: 0)` onChanged-once — finger-up instruments
  feel broken); separate views get separate touches → chords free.
  `Haptics.tap()` per pad. Pitches (C-major pentatonic): C4 261.63, D4 293.66,
  E4 329.63, G4 392.00, A4 440.00, C5 523.25, D5 587.33, E5 659.26.

**Wiring**
- `AppModel.swift`: `let audio = AudioDirector()`. `CloudmojiApp.init` drops
  its session lines → `model.audio.activateSession()`; scenePhase handler →
  `model.audio.handleScenePhase(phase)` (keeps `invalidateVoiceCache()`).
- `ContentView.swift`: `case .instrument: hosted { InstrumentPadView() }`;
  `goHome()` gains `model.audio.detach()`. Pad view: `attach` in `.onAppear`,
  `detach` in `.onDisappear` (belt-and-braces with goHome).

**Done when (all programmatic)**
- [ ] `ToneBufferTests`: peak amplitude in (0, 1]; strictly monotone decay
  envelope after attack; zero DC offset (mean ≈ 0); frequency spot-check via
  zero-crossing count for A4. Mutation-tested.
- [ ] `AudioDirectorTests` (engine faked behind a seam): attach→detach
  lifecycle; scenePhase .background stops; .active restarts only-if-attached.
- [ ] `grep -c "AVAudioSession" ios/Cloudmoji/Cloudmoji/CloudmojiApp.swift`
  = 0; `grep -rc "AVAudioSession" ios/Cloudmoji/Cloudmoji/ | non-zero only in
  AudioDirector.swift`.
- [ ] `-cm_open instrument` UI smoke: 8 pads ≥72pt (non-empty guard), tap
  crashes nothing, `home-btn` returns. Latency/chords/by-ear → device pass.
- [ ] Full suites green; TTS still works in Words (existing suite is the guard).

## §Step 4 — Flash Cards ⚡ (premium)

**Create**
- `ios/Cloudmoji/Cloudmoji/Views/FlashCards/FlashRound.swift` — pure value
  type (`CountRound` shape): `init(pool: [EmojiEntry], choices: Int = 3,
  language: Language, using: inout some RandomNumberGenerator)`; target + 2
  distractors; distractors filtered to DISTINCT words in the current language
  (catalogue has cross-category near-synonyms).
- `ios/Cloudmoji/Cloudmoji/Views/FlashCards/FlashCardsView.swift` — speak
  `model.word(for: target)` on round start; three ≥100pt tiles
  (`PressScale(0.85)`, ids `flash-choice-<glyph>`); correct →
  `Haptics.reward()` + `arbitrate`d `.beaming` + next; wrong → the tapped
  emoji's OWN word is spoken + gentle bounce, then target repeated (rule 4:
  a wrong tap is a detour that names the thing tapped — sound and motion,
  never an error); 64pt replay button `flash-replay`.

**Wiring**
- `ContentView.swift`: `case .flashCards: hosted { FlashCardsView() }`.
  Pool = `model.emojis(in: nil)` — settings-narrowed, repository-sourced,
  zero Swift-authored content. Tile only visible when
  `entitlements.isUnlocked` (via `visibleMiniApps`, already done in Step 1 —
  no per-view entitlement branch).

**Done when (all programmatic)**
- [ ] `FlashRoundTests`: seeded RNG → deterministic round; target ∈ choices;
  all words distinct in-language; pool of 3 with two identical words → round
  still valid (falls back to 2 choices, never crashes); pool < 2 → nil round.
  Each mutation-tested.
- [ ] UI smoke (`-cm_open flashcards -cm_premium_unlocked YES`): 3 choices
  ≥100pt (non-empty guard); tapping the target advances (choices change);
  tapping a distractor does NOT advance; `flash-replay` ≥64pt; `home-btn`
  returns. With stub locked: `launcher-tile-flashcards` absent.
- [ ] Full suites green.

## §Step 5 — Animal Sounds 🔊 (premium)

**Create**
- `ios/Cloudmoji/Cloudmoji/Views/AnimalSounds/AnimalSoundCatalog.swift` —
  `static let files: [String: String]` glyph→resource ("🐶"→"dog").
- `ios/Cloudmoji/Cloudmoji/Views/AnimalSounds/AnimalSoundsView.swift` —
  content = `model.emojis(in: .animals)` ∩ catalogue (no dead taps; orphaned
  files unreachable); 72pt `EmojiTile` metrics; tap: `Haptics.tap()` →
  `audio.playSound` → word via TTS after; both respect `muted`.
- `ios/Cloudmoji/Cloudmoji/Resources/AnimalSounds/*.caf` — ~15 one-second
  CC0 recordings I source and convert (`afconvert -f caff -d ima4`), plus
  `LICENSES.txt` (per-file source + CC0 statement; the fonts' OFL pattern).
  Kevin's ear-approval happens in the device pass; swaps are file
  replacements, no code change.

**Wiring**
- `ContentView.swift`: `case .animalSounds: hosted { AnimalSoundsView() }`.
  Playback through `AudioDirector.playSound` (engine client `.animalSounds`,
  attach/detach in appear/disappear + goHome).

**Done when (all programmatic)**
- [ ] `AnimalSoundCatalogTests`: every catalogue glyph exists in
  `EmojiRepository` under `.animals`; every mapped resource exists in the
  bundle; intersection non-empty (assert-non-empty-first doctrine); every
  bundled .caf decodes via `AVAudioFile` (rejects a corrupt download).
  Mutation-tested.
- [ ] `ls ios/.../Resources/AnimalSounds/*.caf | wc -l` ≥ 12 and
  `LICENSES.txt` present with one entry per file.
- [ ] UI smoke (`-cm_open animalsounds -cm_premium_unlocked YES`): ≥12 tiles
  ≥72pt (non-empty guard); tap crashes nothing; `home-btn` returns. Actual
  sound quality/mixing-by-ear → device pass.
- [ ] Full suites green.

## §Step 6 — Photos + Camera 📷 (premium)

**Create**
- `ios/Cloudmoji/Cloudmoji/Views/Photos/PhotoStore.swift` —
  `Application Support/Photos/<UUID>.jpg`; `.completeFileProtection`;
  `isExcludedFromBackup = true` (the load-bearing half of "never leaves the
  device"); `save(_:) throws -> URL`, `photos: [URL]` newest-first,
  `delete(_:)`, `deleteAll()`; directory injectable for tests.
- `ios/Cloudmoji/Cloudmoji/Views/Photos/CameraController.swift` — `@MainActor`
  facade over `AVCaptureSession` (config on its own serial queue via
  nonisolated helpers), `AVCapturePhotoOutput`, auth state. **Video input
  only — never audio** (keeps "never asks for the microphone" true).
  `stop()` called from `onDisappear`, `goHome`, AND backgrounding —
  deliberately redundant: a green camera dot outliving the mini-app is a
  trust catastrophe in a kids app. Capture debounce (~1s) against a toddler
  holding the shutter.
- `ios/Cloudmoji/Cloudmoji/Views/Photos/PhotosView.swift` — child gallery
  (≥72pt thumbnails, full-screen on tap, NO delete affordance) + giant camera
  tile (absent if auth denied — absence, not a dead tap).
- `ios/Cloudmoji/Cloudmoji/Views/Photos/CameraView.swift` — full-screen
  preview (`AVCaptureVideoPreviewLayer` in a `UIViewRepresentable`), one 88pt
  shutter (`PressScale`, `Haptics.reward()` on capture), home button via
  `hosted{}` as everywhere.
- `ios/Cloudmoji/Cloudmoji/Views/Photos/ManagePhotosView.swift` — parent-
  facing, reached from the Settings premium section (behind the gate): count,
  per-photo delete, delete-all-with-confirmation.
- `ios/Cloudmoji/Cloudmoji/PrivacyInfo.xcprivacy` — tracking false, collected
  data types [], accessed APIs: UserDefaults/CA92.1, FileTimestamp/C617.1.
  (Technically already owed for UserDefaults.)

**Wiring**
- `ContentView.swift`: `case .photos: hosted { PhotosView() }`.
- `project.pbxproj` (both configs): `INFOPLIST_KEY_NSCameraUsageDescription =
  "Cloudmoji uses the camera so your child can photograph things around them.
  Photos stay inside the app, on this device."` No photo-library keys.
- `SettingsView.swift`: "Manage photos" row in the premium section.
- `AboutView.swift`: CAMERA section added ("photos are stored inside the app
  on this device, are excluded from iCloud backup, and can be deleted in the
  grown-ups screen"); `AboutViewTests` updated same commit.

**Done when (all programmatic)**
- [ ] `PhotoStoreTests` (temp dir): save→listed newest-first; delete removes;
  deleteAll empties; saved file has backup-exclusion resource value set; file
  protection attribute present. Mutation-tested.
- [ ] `plutil -lint ios/Cloudmoji/Cloudmoji/PrivacyInfo.xcprivacy` passes;
  `grep NSCameraUsageDescription project.pbxproj` = 2 (Debug+Release);
  `grep -c "NSMicrophone\|NSPhotoLibrary" project.pbxproj` = 0.
- [ ] `grep -rn "AVCaptureDeviceInput" ios/ | grep -v video` → no audio input
  anywhere.
- [ ] UI smoke (`-cm_open photos -cm_premium_unlocked YES`, simulator = auth
  denied path): gallery renders, camera tile absent, no crash; Manage Photos
  reachable behind gate; About camera section present; banned-term sweeps
  still green. Real capture/permission prompt/green-dot → device pass.
- [ ] Full suites green.

## §Step 7 — StoreKit — **EXCLUDED from this run**

Documented in PLAN.md as the final section, marked "requires Kevin: sandbox
purchase testing". Design fixed so Steps 1–6 need no rework:
`StoreEntitlementStore` (app target) implements the same `EntitlementProviding`;
one non-consumable `app.cloudmoji.unlock.full`; `cm_premium_unlocked` demotes
to fast-boot cache with truth from `Transaction.currentEntitlements`;
`Transaction.updates` observer from `CloudmojiApp.init`; Ask-to-Buy `.pending`
→ "Waiting for approval…" in Settings; DEBUG `-cm_premium_unlocked` keeps the
stub for UI tests; About copy softened in the same commit ("makes no
connections of its own…"). Manual for Kevin: App Store Connect IAP + price +
privacy label stays "Data Not Collected".

---

## §VERIFICATION

### Programmatic (every step's gate; the run halts on failure)
- `xcodebuild … -only-testing:CloudmojiTests -parallel-testing-enabled NO`
  green; test COUNT read, not just the checkmark (baseline 197+, growing per
  step). Package suite 72 green. UI suites run individually by suite name
  (600s cap), each green.
- Every new test mutation-verified: name the app-code line whose deletion
  breaks it, run the mutation, confirm red, restore. (Eleven spec-supplied
  tests have failed this bar in this project; it stays mandatory.)
- Zero-warning builds on new files; `git status` clean at each commit gate.
- Kids-Category greps stay clean all run: `URLSession|NWConnection|WebKit|
  sockets` → 0 hits outside comments; `Package.swift` dependencies = 0.
- File-existence gates as listed per step (`.caf` count, `LICENSES.txt`,
  `PrivacyInfo.xcprivacy` lint, pbxproj key greps).
- Accessibility-tree gates via XCUITest with non-empty-collection guards:
  tile counts (4 locked / 7 unlocked), 64/72pt floors both axes, ≥8pt gaps,
  `home-btn` present in all seven mini-apps, gate coverage over the launcher.
- Simulator screenshots captured per step (`xcrun simctl io … screenshot`)
  and attached to the run log for the device-pass review.

### Physical device (batched — ONE pass by Kevin at the end; nothing blocks on it)
- **Haptics**: tile knock and reward pattern in every new mini-app (Simulator
  has no Taptic Engine; already tuned 3× by hand — judge, don't assume).
- **Instrument Pad**: touch-down latency (~30ms budget), chords with two
  fingers, sound after a phone-call interruption, by ear.
- **Animal Sounds**: recording quality and volume balance vs TTS — Kevin's
  ear-approval of my CC0 picks; rejects are file swaps, no code.
- **Camera**: permission prompt wording on first entry; capture; the green
  indicator dies with the mini-app (background it, check the dot); photos
  visible in gallery; Manage Photos delete; photos absent from iCloud backup
  (Settings → storage check if desired).
- **Sleepy Cloud**: dimming feel across a real 2-min session in a dark room;
  screen does not auto-lock mid-session; DOES auto-lock after asleep.
- **Speech + audio mixing**: TTS over pad tones, duck behaviour with a
  podcast playing, mute button silencing TTS+sounds but not haptics.
- **The whole launcher with Cloud** — the only reviewer whose verdict on
  tile size, home-button discoverability, and Flash Cards' wrong-tap feel
  actually counts.

After Step 6 + device pass: one whole-branch review (Kids-Category sweep,
privacy copy true sentence-by-sentence), then Step 7 scheduled with Kevin.
