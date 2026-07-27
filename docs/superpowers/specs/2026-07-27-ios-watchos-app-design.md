# Cloudmoji for iOS and watchOS — Design

**Date:** 27 July 2026
**Status:** Approved design, not yet planned
**Supersedes:** `MASTER_PLAN.md` Phase 4, which specified React Native

---

## Context

Cloudmoji ships today as a PWA at cloudmoji.app: 200 emojis across 8 categories, 5
languages (en, zh, ms, ja, tl), Words and Count modes, a cloud mascot with four moods,
and device text-to-speech. It is validation-stage — no backend, no accounts, no payments.

`MASTER_PLAN.md` placed a native app at Phase 4 (months 4–6), built in React Native, and
gated it on monetization being validated. This design changes two of those decisions
deliberately:

- **Native Swift rather than React Native.** Kevin's call. A Swift package also lets the
  same core serve a watchOS target, which React Native does not.
- **Built now rather than after monetization validates.** The purpose is to *open* the
  App Store channel so monetization becomes possible, rather than to follow it.

`MASTER_PLAN.md` should be updated to match, and its "Not a React Native app (yet)"
non-goal reworded.

### Why native at all

The primary driver is distribution: the App Store is a discovery surface a PWA has no
access to, and it is a precondition for the subscription model in
`docs/product/MONETIZATION.md` (which projects iOS at 60% of revenue). Secondary
benefits, all observed while building the web app:

- Safari's chrome consumes roughly a quarter of the screen in landscape, which forced a
  dedicated rail layout to make the app usable at all.
- Service-worker caching repeatedly served stale builds during testing.
- Add-to-Home-Screen is a friction step most parents will not complete.

---

## Goals

1. Ship an App Store listing in the **Kids Category** with feature parity to the web app.
2. Add a parent **Settings** screen — a surface the web app lacks.
3. Add a **watchOS** app suited to a toddler fiddling with a parent's watch.
4. Keep content in **one** source of truth so the five languages cannot drift.
5. Leave a clean seam for in-app purchases without building them yet.

## Non-goals for v1

- In-app purchases or subscriptions. Architecture must not block them; nothing ships.
- Recorded audio. Device TTS only (see *Speech*).
- A backend. `CLAUDE.md` forbids one and nothing here needs it.
- Android. Separate decision, separate spec.
- Sharing code with the web app beyond generated data (see *Content pipeline*).

---

## Platform decisions

| Decision | Value | Reason |
|---|---|---|
| Language | Swift, SwiftUI | User preference; enables a shared watchOS core |
| Minimum iOS | 17.0 | `@Observable`, modern SwiftUI; near-universal by 2026 |
| Minimum watchOS | 10.0 | Matches the iOS 17 generation |
| Devices | Universal iPhone + iPad | Size-class layout makes iPad nearly free |
| App Store category | Kids, 0–5 age band | The actual audience |
| Repo | Same repo, under `ios/` | The content generator reads `src/data/*.ts` |

---

## Architecture

Three units. The split exists so the language rules — the part most expensive to get
right and easiest to break — can be tested without a simulator.

### `CloudmojiCore` (Swift package, no UI)

Imports Foundation and AVFoundation only. Never SwiftUI.

| Type | Responsibility |
|---|---|
| `EmojiEntry`, `Countable`, `Language`, `Category` | Data models, mirroring `src/types.ts` |
| `EmojiRepository` | Decodes bundled `EmojiData.json`; the only type that knows the file format |
| `CountingGrammar` | Per-language phrase construction (see *Counting grammar*) |
| `VoiceResolver` | Prefix-chain voice selection, ported from `src/lib/voices.ts` |
| `SpeechController` | Cancellable speech queue |
| `SettingsStore` | `@Observable`, persisted, validated on read |
| `UsageLog` | On-device tap statistics, ported from `src/lib/measurement.ts` |

### `Cloudmoji` (iOS app target)

SwiftUI. Adapts through size classes and available height rather than branching into
two parallel view trees.

```
RootView                      mode + gate ownership, AppModel injection
 └─ AdaptiveShell             chooses rail vs bars from size class + height
     ├─ WordsView             mascot, typing row, word bubble, grid
     └─ CountView             progress dots, readout, countable grid, controls
Shared leaves: CloudMascot, EmojiGrid, TypingRow, CategorySource,
               ParentalGate, SettingsView, AboutView
```

**`CategorySource` is a single component** rendered either as a horizontal strip
(portrait) or a vertical rail (landscape). The web app has two copies of this list and
of the mode bodies; three separate edits during the landscape work landed on the dead
copy before being noticed. The iOS app must not repeat that shape.

### `CloudmojiWatch` (watchOS app target)

Depends on `CloudmojiCore`. Shares no views with iOS — the interaction is different by
design (see *watchOS*).

---

## Content pipeline

`src/data/emojis.ts` and `src/data/countables.ts` remain the single source of truth.

```
src/data/*.ts  →  tools/generate-ios-data  →  ios/Cloudmoji/Resources/EmojiData.json
                                              (bundled; app works offline on first launch)
```

The generator is a Node script in the web repo. CI runs it and fails if the committed
JSON differs from freshly generated output, so the two can never silently diverge.

`EmojiData.json` carries the emoji records, countables, category definitions with their
five label translations, and the number words per language. It does **not** carry
grammar rules — those are code in `CountingGrammar`, ported and independently tested on
both sides.

**Rationale.** Word mode and Count mode drifted apart within a single codebase (🚗 was
`汽车` when tapped and `三辆车` when counted). Two codebases would drift faster. A
generated artifact plus a CI check makes drift a build failure rather than a discovery.

---

## Counting grammar

Ported from `src/components/CountMode.tsx`, preserving the rules established with
native-speaker review:

| Language | Rule |
|---|---|
| en | Regular pluralisation, with an explicit irregular map (`tooth→teeth`, `mouse→mice`) |
| zh | Measure word baked into the countable noun (`只狗`); `两` not `二` for 2 |
| ms | Penjodoh bilangan baked into the noun (`ekor anjing`) |
| ja | Universal ～つ counter, **noun first**: `りんご みっつ`, never `みっつのりんご` |
| tl | Linker attaches to the numeral: vowel-final `-ng` (`tatlong aso`), `n`-final `-g`, other consonants a separate `na` (`apat na aso`) |

Two deliberate exceptions carry over and must not be "corrected":

- 🦷 in Japanese is katakana **ハ**, not hiragana は. A standalone は is parsed as the
  topic particle and voiced "wa" by ja-JP engines, teaching the wrong sound.
- ja and tl countables stay **bare nouns**; only zh and ms bake in the classifier.

---

## iOS feature scope

Full parity with the web app:

- Words mode: 200 emojis, 8 categories plus All, typing row capped at 50 with
  replay/delete/clear, floating word bubble, milestone celebrations at 10/25/50/100.
- Count mode: 84 countables, counts 2–9, progress dots, spoken running count, shuffle
  and next.
- Five languages with a picker; selection persisted.
- Cloud mascot: happy, excited, speaking, beaming.
- Parental gate (arithmetic) protecting Settings, stats and any external link.
- About: FAQ, privacy disclosure, version history.

### New: parent Settings

Behind the parental gate.

- **Language control** — which of the five languages are enabled, and which is default.
  A family using only EN and 中文 gets a two-item picker instead of five, so Cloud
  cannot land in Tagalog by accident.
- **Content control** — which categories appear, and the Count mode range. Narrows the
  app for a younger child or extends it for an older one.

Settings filter what `AppModel` publishes, so views consume an already-narrowed list and
never branch on settings themselves.

Speech rate/pitch control and a data/privacy screen were considered and cut from v1.

### Touch targets

`CLAUDE.md` rule 1 as scoped during the web work: **64pt minimum, 72pt preferred, for
anything a child taps** — emoji tiles, typed emojis, replay/delete/clear, category
chips, count tiles, shuffle/next, tab bar. Parent-only chrome (About, mute, language,
Settings) follows the 44pt iOS HIG minimum instead.

---

## watchOS

**The grid is not ported.** A 41mm Watch offers ~162×197pt; a 72pt grid would fit two
targets across and a toddler's fingertip covers most of the display.

Instead the interaction inverts: **one emoji fills the screen, and the whole screen is
the target.**

```
╭──────────────╮   ● ← Digital Crown
│              │     spin = next emoji,
│     🍎       │     one haptic detent each
│              │
│    apple     │
╰──────────────╯
   tap anywhere → speaks the word + haptic
```

- Tap target is the full screen, far exceeding the 72pt rule.
- The Digital Crown browses; toddlers repeat spinning readily and each detent gives a
  haptic, which is a stronger feedback loop on a wrist than a tap on a phone.
- Word label uses the language chosen on the phone; category filtering follows the
  phone's content settings.
- No typing row, no Count mode, no milestones. Short sessions by nature.

Settings sync from iPhone via `WatchConnectivity` (application context, not messages —
it is small, latest-value-wins state). The watch app falls back to its own last-known
settings when the phone is unreachable, and to English with all categories otherwise.

### Known limitation

watchOS has no Guided Access equivalent. Cloud can press the crown or swipe and leave
the app, and this cannot be prevented. Documented rather than solved.

---

## Speech

`AVSpeechSynthesizer`, rate 0.85, pitch 1.1 — matching the web app.

`VoiceResolver` ports the prefix chain from `src/lib/voices.ts`:

| Language | Chain |
|---|---|
| en | `en` |
| zh | `zh` |
| ms | `ms`, `id` |
| ja | `ja` |
| tl | `fil`, `tl`, `ms`, `id`, `es` |

Tagalog falls back to Malay because most devices ship no Filipino voice, and the two are
Austronesian — same five vowels, same Latin orthography, both writing /ŋ/ as `ng`, which
Tagalog's number linker uses constantly. An English voice mangles Tagalog badly.

**v1 does not prompt the parent to install a Filipino voice.** Native could deep-link to
Settings → Accessibility → Spoken Content → Voices; this was considered and deferred.
`VoiceResolver` keeps the seam so it can be added without rework.

### Cancellable queue

`SpeechController` ports the generation-token design built for the web app: every
cancellation bumps a token, and queued work compares against it before proceeding.
Sequences chain on `didFinish` rather than fixed timers, with a watchdog so a silently
dropped utterance cannot stall the queue.

Cancels on: mute, language change, clear, delete, shuffle, mode change, and disappearance.

This exists because the web app's timer-based replay kept speaking after mute, after a
language change and after the round was replaced — each callback held stale state and
nothing could call it back.

### Audio session

**Recommendation:** `.playback`, so Cloudmoji speaks even with the ringer switch off.
That matches what a parent expects when handing over a phone, but it is a deliberate
override of a system setting and is called out here as a decision rather than an
accident. Interruptions (an incoming call) pause and then restore the session.

---

## Kids Category compliance

| Requirement | How it is met |
|---|---|
| No third-party analytics or ad SDKs | None linked. See *Telemetry* |
| Parental gate on external links | Existing arithmetic gate, ported |
| Parental gate on purchases | Gate is in place before IAP is added |
| No PII collected from children | Nothing is collected; usage stats stay on device |
| Privacy nutrition label | "Data Not Collected" for the iOS build |
| Age band | 0–5 |

The web app's Vercel Analytics, Speed Insights and Google Fonts requests are **not**
carried into the iOS build. Fonts (Lilita One, Nunito) ship bundled, which also removes
the first-launch network dependency the web app has.

---

## Telemetry

| Source | Provides |
|---|---|
| App Store Connect | Installs, sessions, retention, crashes — no code |
| MetricKit | Crash, hang and launch diagnostics, on device |
| `UsageLog` | Per-emoji tap counts, on device, parent-exportable |

**Accepted gap:** there is no cross-user emoji data on iOS. Answering "which emoji do
children tap most" would need a first-party backend, which `CLAUDE.md` rules out. The
web app continues to answer content questions; iOS answers distribution questions.

---

## Error handling

No failure state reaches the child.

| Condition | Behaviour |
|---|---|
| Corrupt persisted settings | Validate on read, recover to defaults |
| No voice for a language | Fall back down the chain, then silence — mascot still reacts, so the tap is still rewarded |
| Speech error or interruption | Swallow, return mascot to happy, restore session |
| Watch cannot reach phone | Use last-known settings, then defaults |
| Corrupt bundled JSON | Build failure via CI parity check; never a runtime path |

Settings validation is explicitly called out because the web app crashed on exactly this:
a stale language value reached `NUMBER_WORDS[lang][num - 1]` and threw on the first tap
in Count mode.

---

## Testing

**`CloudmojiCore` unit tests** (Swift Testing, no simulator):

- Counting grammar for 1–10 in all five languages, including the Tagalog `na` cases
  (`apat`, `anim`, `siyam`) and Japanese noun-first order.
- Irregular plurals across all 84 countables against an expected-plural table.
- `VoiceResolver` against synthetic voice lists: no Filipino installed must resolve to
  Malay and never to English; a real Filipino voice wins; `tl-PH` and `fil-PH` both
  accepted; an English-only device resolves to nothing rather than mislabelling.
- `SettingsStore` validation and recovery from corrupt values.

**CI checks:** bundled `EmojiData.json` matches freshly generated output; Word mode and
Count mode name the same thing in zh and ms.

**XCUITest**, covering the behaviours regression-tested on web:

- Mute cancels a replay already in flight; so do language change, clear and mode change.
- Shuffle stops the previous round's completion phrase.
- The parental gate blocks a wrong answer.
- Every child-facing control measures ≥64pt.
- Landscape shows the rail; portrait shows the bars.
- Controls stay on screen at every Count round size.

**Accessibility:** VoiceOver labels on emoji buttons, Dynamic Type on parent-facing text.
Both are near-free in SwiftUI and both are looked at in Kids Category review.

---

## Project setup

```
cloudmoji/
├── src/                          existing web app
├── tools/generate-ios-data/      TS → JSON generator
└── ios/
    ├── Cloudmoji.xcodeproj
    ├── CloudmojiCore/            local Swift package
    │   ├── Package.swift
    │   ├── Sources/CloudmojiCore/
    │   └── Tests/CloudmojiCoreTests/
    ├── Cloudmoji/                iOS app target
    │   └── Resources/EmojiData.json
    └── CloudmojiWatch/           watchOS app target
```

Steps:

1. `mkdir ios && cd ios`, then Xcode → New Project → iOS App, product name `Cloudmoji`,
   interface SwiftUI, language Swift, saved into `ios/`.
2. Set the iOS deployment target to 17.0 and enable iPhone + iPad.
3. File → New → Target → watchOS → App, named `CloudmojiWatch`, bundling with the iOS
   app so both share one App Store listing. Deployment target watchOS 10.0.
4. File → New → Package → Library, named `CloudmojiCore`, saved inside `ios/`. Add it to
   both targets under General → Frameworks and Libraries.
5. Bundle identifiers: `app.cloudmoji.ios` and `app.cloudmoji.ios.watchkitapp`.
6. Add `EmojiData.json` to the iOS target's Copy Bundle Resources, and to the watch
   target's as well so the watch works standalone.
7. Add fonts (Lilita One, Nunito) to both targets and declare them in each Info.plist.
8. Add a Run Script build phase invoking the generator so a stale JSON fails the build
   locally as well as in CI.

---

## Delivery sequence

This design covers three deliverables. They share `CloudmojiCore`, so they belong in one
spec, but each is a separate stage with its own exit criterion. The implementation plan
should treat them as three phases, not one push.

| Stage | Contents | Done when |
|---|---|---|
| 1. Core + pipeline | `CloudmojiCore`, generator, CI parity check | Grammar, plural and voice tests pass; generated JSON matches `src/data/*.ts` |
| 2. iOS app | Full parity, Settings, parental gate, adaptive layout | XCUITest suite green; builds for App Store submission |
| 3. watchOS app | One-emoji screen, crown browsing, settings sync | Runs standalone with last-known settings; syncs from the phone |

Stage 1 is the only stage the other two depend on. Stage 3 can be dropped or deferred
without affecting stage 2, and should be if stage 2 runs long.

## Risks

| Risk | Mitigation |
|---|---|
| App Store rejection on Kids Category rules | No third-party SDKs from the start; gate already built; nutrition label is "Data Not Collected" |
| Content drifts between web and iOS | Generated artifact plus CI parity check |
| Counting grammar regresses silently in the port | Unit tests are the reason `CloudmojiCore` is a separate package |
| Watch app is a novelty Cloud ignores | Small scope — one screen, one interaction. Cheap to drop |
| Effort competes with web iteration | Sequence: core and generator first, then iOS parity, then watch |

---

## Open decision

**Audio session category.** `.playback` (recommended) speaks with the ringer off;
`.ambient` respects the silent switch. Recorded here so it is chosen rather than
defaulted.
