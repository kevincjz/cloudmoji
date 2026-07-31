# Cloudmoji Android App — Implementation Plan

**Date:** 2026-07-30

**Status:** Phase 0 scaffold implemented in this workspace

## Outcome

Ship a native Android phone and tablet version of Cloudmoji that preserves the
same toddler interaction contract, local-first privacy posture, free/full split,
five-language content, and seven-mini-app product shape as the Apple app.

The Android app is a separate native client:

- Kotlin and Jetpack Compose
- no React Native, Flutter, WebView shell, or Kotlin Multiplatform migration
- no backend, account, ads, analytics, or tracking SDK
- content generated from the existing `src/data/*.ts` source of truth
- a separate Google Play one-time product for Full Cloudmoji
- phone and tablet first; Wear OS is a later, separately approved project

## Fixed product decisions

| Decision | Choice |
|---|---|
| Application ID | `app.cloudmoji.android` |
| UI | Jetpack Compose |
| Minimum Android | API 26 (Android 8.0) |
| Compile SDK | API 37 |
| Target SDK | API 36 until the next Play target deadline |
| Free tier | Words and Count in English |
| Full tier | Music, Flash Cards, Animals, Photos, Sleepy Cloud, and four additional languages |
| Purchase ownership | Google Play purchase is separate from the Apple purchase |
| Offline behavior | All learning content bundled; Full remains useful offline after Play reconciliation |
| Portable boundary | Generated `EmojiData.json`, fonts, icons, copy, design tokens, and behavioral test cases |

The toolchain snapshot is intentionally pinned rather than dynamic:

- Android Gradle Plugin 9.2.1
- Gradle 9.4.1
- AGP built-in Kotlin 2.3.10
- Compose compiler plugin 2.3.10
- Compose BOM 2026.06.00
- JDK 17

Revisit these versions only as an explicit maintenance change. Relevant primary
documentation:

- https://developer.android.com/build/releases/agp-9-2-0-release-notes
- https://developer.android.com/build/migrate-to-built-in-kotlin
- https://developer.android.com/develop/ui/compose/setup-compose-dependencies-and-compiler

## Architecture

```text
src/data/*.ts
    │
    ├── web React app
    ├── iOS EmojiData.json
    └── Android app/src/main/assets/EmojiData.json

Android Compose UI
    │
    ├── AppModel / access policy
    ├── catalog + counting grammar
    ├── settings (DataStore)
    ├── speech + audio coordinator
    ├── camera/photo store
    └── Play Billing entitlement store
```

The Android source tree should settle into:

```text
android/app/src/main/java/app/cloudmoji/android/
├── MainActivity.kt
├── CloudmojiApp.kt
├── data/          # generated catalog reader and local stores
├── model/         # platform-neutral Kotlin models and rules
├── platform/      # TTS, audio, haptics, camera, billing
└── ui/
    ├── launcher/
    ├── words/
    ├── count/
    ├── flashcards/
    ├── instrument/
    ├── animals/
    ├── photos/
    ├── sleepy/
    ├── parents/
    └── theme/
```

Views consume already-filtered state. They do not decide whether a language,
category, or paid mini-app is accessible. That remains one central access policy,
matching the iOS architecture.

## Delivery phases

### Phase 0 — Buildable scaffold (implemented)

- Gradle wrapper and pinned version catalog
- one Android application module
- Compose theme, launcher, simple route state, and home control
- all seven mini-app identities and central free/full filtering
- generated Android copy of `EmojiData.json`
- local unit tests and Compose instrumentation smoke tests
- Android-specific setup and command-line README

**Gate:** `generate:android` is deterministic, pure model tests pass, and the
Gradle project is ready to sync once Android Studio/SDK/JDK are installed.

### Phase 1 — Core platform and data (3–5 engineering days)

- decode and validate `EmojiData.json`
- port `EmojiRepository`, `CountingGrammar`, settings invariants, and access policy
- DataStore-backed settings with a safe migration/version strategy
- Android `TextToSpeech` adapter with cancellation and utterance callbacks
- BCP-47 voice resolution for EN, ZH, MS, JA, and TL
- audio-focus owner and haptic abstraction
- unit tests ported from `CloudmojiCore`

**Gate:** all 200 emojis, 84 countables, five languages, nine category records,
and 20 animal-sound mappings load; grammar fixtures match iOS.

### Phase 2 — Free learning loop (5–7 engineering days)

- mascot and its happy/speaking/excited/beaming state priority
- Words: continuous categorized grid, category jumps, word bubble, typing row
- Count: generated rounds, localized grammar, replay, shuffle, and count range
- adaptive phone/tablet and portrait/landscape layouts
- TalkBack labels, 64dp child targets, and minimum 8dp spacing
- Compose UI tests for the high-value interaction paths

**Gate:** Words and Count work fully offline in English on a phone and tablet;
TTS and interaction behavior pass representative physical-device testing.

### Phase 3 — Launcher and grown-up controls (3–4 engineering days)

- production launcher artwork and motion
- arithmetic parental gate
- Settings, tutorial, About, privacy, and support routes
- language/category/range/sound controls
- deep-link or launch-argument hooks for deterministic tests

**Gate:** a child cannot reach settings, links, permissions, or commercial copy
without passing the gate.

### Phase 4 — Full mini-apps (8–11 engineering days)

- Music with generated tones and coordinated audio focus
- Flash Cards with no-failure round generation
- Animals with localized animal noises and synthesized fallback sound
- Sleepy Cloud with screen-awake/dimming lifecycle handling
- Photos with CameraX, app-private originals, backup exclusion, parent export,
  and parent deletion

**Gate:** all seven mini-apps are functional on phone and tablet; camera and
audio lifecycle tests pass on physical hardware.

### Phase 5 — Google Play Billing (3–5 engineering days)

- one non-consumable/one-time Full Cloudmoji product
- localized product details and parent-only purchase UI
- purchased, pending, cancelled, restricted, unavailable, and failed states
- acknowledge completed purchases and query ownership at launch/resume
- license-tester flows and internal-track testing
- explicit decision log for client-side verification versus a future backend

Google recommends backend verification. The first release may remain client-only
to preserve the no-account/no-backend product, accepting greater tampering and
delayed-refund risk. That tradeoff must be approved before production.

**Gate:** license-tester success, pending, cancellation, restore, offline restart,
and refund/revocation paths are documented and exercised.

### Phase 6 — Release hardening (5–7 engineering days plus store elapsed time)

- Pixel phone, Pixel tablet, Samsung phone, and one budget-device pass
- Android 8/API 26 and current-API emulator coverage
- TTS voice audit in all five languages
- accessibility, rotation, split-screen, font scale, and process-death checks
- Families policy, Data safety, content rating, privacy policy, screenshots,
  listing copy, signing, and internal/closed test tracks

**Gate:** signed AAB passes Play pre-launch reports and the release checklist.

## Test strategy

| Layer | Purpose | Command |
|---|---|---|
| Generator parity | Android asset matches `src/data` | `npm run verify:android` |
| Kotlin host tests | rules, grammar, access, round generation | `./gradlew test` |
| Compose/device tests | semantics, targets, routes, permissions | `./gradlew connectedAndroidTest` |
| Static checks | Android lint and build checks | `./gradlew check` |
| Manual hardware | TTS, emoji font, haptics, audio, camera | device checklist |
| Billing | actual Play purchase lifecycle | Play license testers |

Every regression found manually should become the narrowest practical automated
test. Android tests should validate behavior rather than compare pixels across
OEM emoji fonts.

## Key risks and early proofs

1. **TTS fragmentation:** test the five language codes on Google and Samsung
   engines before completing UI parity.
2. **Emoji rendering:** verify multi-code-point emoji and skin-tone/family glyphs
   on Google, Samsung, and a budget device.
3. **Purchase ownership:** Apple and Google purchases do not transfer. Adding
   transfer later requires an account/backend and a new privacy review.
4. **Camera/storage differences:** use CameraX and scoped storage; never request
   broad storage access.
5. **Children's policy:** keep networking and third-party SDKs absent by default.
6. **Maintenance:** all product behavior is implemented twice; keep data and
   pure behavioral fixtures shared to reduce drift.

## Estimate

The expected total for phone/tablet feature parity is **25–35 focused
engineering days**, excluding unpredictable store review time:

| Work | Days |
|---|---:|
| Scaffold and core | 4–6 |
| Free learning loop | 5–7 |
| Launcher and parent area | 3–4 |
| Full mini-apps | 8–11 |
| Billing | 3–5 |
| Release hardening | 5–7 |

Some tasks overlap, so the range is not the arithmetic maximum. A credible
closed-test build of the free tier should be available after roughly 8–12 days.

## Deliberately deferred

- Wear OS companion and phone/watch Data Layer messaging
- cross-platform purchase entitlement
- accounts, backend, cloud sync, remote analytics, crash SDKs, and ads
- shared Swift/Kotlin runtime or a rewrite of the existing Apple app
- TV, Automotive, ChromeOS-specific, and foldable-specific product experiences

