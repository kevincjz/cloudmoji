# Android device QA checklist

This branch was built and unit-tested (484 JVM tests, mutation-proofed) on a
machine with **no emulator or device**. Everything below is the accumulated
list of surfaces that compile but have never executed on real hardware. Run
these on a physical phone **and** tablet before any installable build ships.

## Run the compiled-but-never-executed test suites

```
./gradlew :app:connectedDebugAndroidTest
```

Covers: `LauncherSmokeTest`, `WordsChildTargetsTest`, `CountChildTargetsTest`,
`ParentalGateSmokeTest` (incl. the two process-recreate gate regressions),
`CloudMascotSemanticsTest`, `MiniAppScaffoldSoundRecoveryTest`,
`FlashCardsChildTargetsTest`, `SleepyChildTargetsTest`, `PhotosChildTargetsTest`.

## Manual passes

**Speech (all five languages: en, zh, ms, ja, tl)**
- Voice quality/availability on the Google TTS engine and (Samsung device) the
  Samsung engine; the fallback chain when a language pack is missing.
- Cancel-before-speak under rapid tapping; rate/pitch feel (0.85 / 1.1).

**Audio focus**
- Notification sound during Music and during a Sleepy Cloud session — sleep
  ambience must resume after the blip (Words/Count speech correctly does not).
- Known gap: a Sleepy session **started** during a phone call stays silent
  until some other focus event (no `setAcceptsDelayedFocusGain`).

**Camera / Photos**
- CameraX bind/unbind across rotation and backgrounding; shutter latency;
  the 260 ms flash overlay; capture → gallery thumbnail.
- Permission flows: first ask, refusal (calm parent-facing card, no child-side
  error), final refusal → recovery via Android Settings.
- Parent export to the gallery (Cloudmoji album on API 29+; default location
  on API ≤ 28) and permanent delete, both behind the gate.
- Verify an exported photo carries no EXIF (re-encode strips it; undecodable
  captures are dropped).

**Emoji rendering**
- Multi-code-point and skin-tone/family glyphs on Google, Samsung, and one
  budget device (OEM emoji fonts differ).

**Lifecycle / entry semantics**
- Rotation: Words keeps its typed row and mood; Count/Flash Cards keep their
  round; a mid-celebration rotation may drop the beaming face (accepted).
- Fresh re-entry from the launcher resets each mini-app (typed row cleared,
  milestones re-armed, fresh rounds).
- Process death inside the Grown-ups area: relaunch must land on the launcher
  and re-ask the gate.

**Toddler-UX spot checks**
- 64 dp child targets and 8 dp gaps at the smallest supported width (360 dp)
  and in landscape; tablet (`isExpandedPad`) scaling on every mini-app.
- TalkBack: mascot announces once as "Cloudmoji"; gate scrim blocks focus on
  content beneath it; every interactive element has a label.

## Deferred by design (not QA failures)

- Play Billing: `StubEntitlementStore` defaults unlocked; reconciliation guard
  ported but unwired until a Play product exists.
- Reduce-motion support is absent app-wide (tracked separately).
- `cloudmoji.app/privacy` must be deployed before any build ships — the About
  screen links to it.
