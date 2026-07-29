# CLAUDE.md — Cloudmoji

## What This Is
Cloudmoji is a PWA where toddlers tap emojis and hear the words spoken aloud in one of five languages. Named after Cloud (Kevin's 27-month-old son), who was typing emojis on a locked iPhone and saying the words aloud — unprompted.

The cloud mascot ("Cloudmoji") is a fluffy white cloud character with a face who reacts to the child's taps — bouncing, showing star-eyes when excited, opening its mouth when speaking, and beaming with joy at milestones.

This is a validation-stage product. No backend, no accounts. One optional
purchase (StoreKit 2 only, behind the parental gate) unlocks the premium
mini-apps. Ship fast, learn fast.

Hosted on Vercel (free tier, auto-deploys from `main`) at **cloudmoji.app**.

### The play area (iOS)
The iOS app opens on a **launcher**: a four-column iPhone-style grid of seven
layered app icons, with one labelled, gated Grown-ups control above it. Sound
and language choices live in that parent panel. Four are always included — Words 🗣️,
Count 🧮, Music 🎹 and Sleepy Cloud 🌙 — and three are behind a single unlock:
Flash Cards ⚡, Animals 🔊 and Photos 📷. Available child-facing apps carry no
purchase badge; `AppModel.visibleMiniApps` hides the premium three entirely
while the entitlement is locked. Inside a mini-app the only navigation control
is the **cloud home button**: 84pt, centred along the bottom, in every one of
the seven.

`StubEntitlementStore` is the entitlement today and it defaults to **unlocked** —
there is no App Store Connect product yet, so a default of locked would hide
three finished mini-apps behind a button that cannot do anything. StoreKit is
Step 7 of `PLAN.md` and is not built.

**Scope:** the launcher and the mini-apps are iOS-only. The web app remains the
two-mode product and is no longer the reference for iOS structure —
`src/data/` remains the single content source for both.

**Apple Watch (Phase 1):** a `CloudmojiWatch` companion target exists. The parent
wears the watch, Cloud holds the iPhone. It is emoji-only — the parent browses
the catalogue with the Digital Crown and taps to send an emoji to the phone
(shown + spoken there); the child's Words-mode taps flow back to the wrist
(haptic + emoji + word, spoken on the watch). Transport is **WatchConnectivity**
(`WatchLink` / `WCSessionTransport` on the phone, `WatchRadio` on the watch) —
device-to-device, no backend, no internet; the Kids-Category greps stay clean.
The parent can also **record a short voice message** on the watch (the mic is
watch-only — the iPhone app still never records) that plays on the phone and is
held only in memory for the session, never on disk. `SystemSpeechEngine` now
lives in `CloudmojiCore` so both targets share it. Still no watch settings UI or
mascot. **Note:** a microphone in a Kids-Category app carries an unresolved App
Review risk — check the guidelines before submission.

## Where Context Lives
- `MASTER_PLAN.md` — strategy, phases, decision points
- `docs/` — product, design, content, engineering, growth, ops
- `templates/` — decision log, feedback form, sprint template
- `reference/prototype.jsx` — the original prototype the app was ported from (historical reference only; `src/` is now authoritative)

## Brand Identity

### Name & Tagline
- **Name**: Cloudmoji
- **Tagline**: "Tap. Listen. Learn!"

### Mascot: Cloudmoji the Cloud
A fluffy white cloud character with a kawaii face. Key traits:
- **Shape**: Distinct rounded bumps on top (like ☁️ emoji), flat rounded base
- **Face**: Located in the lower-center of the cloud body
- **Moods**: happy (gentle smile), excited (star eyes, open grin), speaking (round open mouth), beaming (squinty happy eyes, wide grin, golden glow + sparkles)
- **Animation**: Gentle float when idle, bounce when speaking, bigger bounce when beaming
- **Blush**: Soft pink oval cheeks, rosier when beaming
- Refer to `src/components/CloudMascot.tsx` for exact SVG paths

### Colors & Typography
See `docs/design/DESIGN_SYSTEM.md` for the full palette (with the semantic role of each color), type scale, and font stack.

### Background Effects
Three subtle animated glow orbs (coral, teal, gold) that drift with `bgGlow` animation. Adds warmth to the dark background without distracting from emojis.

## Key Rules — Read Before Every Task

### Toddler UX (non-negotiable)
1. Touch targets: **minimum 64px, prefer 72px** — this governs anything a CHILD
   taps: emoji tiles, typed emojis, replay/delete/clear, category chips, count
   tiles, shuffle/next, and on iOS the launcher tiles, the cloud home button,
   instrument pads, flash-card choices, Sleepy Cloud's duration buttons, photo
   thumbnails and the camera shutter. (The tab bar is retired along with the two
   modes.) Parent-only chrome (About, mute, language, Manage Photos) follows the
   iOS HIG 44px minimum instead; forcing 64px there swallows the header on a
   375px screen. `tests/review-fixes.spec.ts` asserts the web half of that list;
   `LauncherUITests` and `CountModeUITests` assert the iOS half.
2. Gap between targets: **minimum 8px**
3. One tap = one action = one reward
4. **No failure states** — every tap is a success
5. No text-heavy UI — emojis first
6. Dark theme — emojis pop on dark backgrounds

### Mascot Behavior
7. **Idle**: gentle float animation (3s ease-in-out)
8. **On tap**: star-eyes + excited grin for ~600ms
9. **While speaking**: round open mouth + gentle bounce
10. **Milestone (10/25/50/100 taps)**: beaming face (squinty eyes, wide grin, golden glow, stars) for 3 seconds
11. **Beaming state takes priority** — TTS and tap events don't override beaming mood

### TTS (read docs/engineering/WEB_TTS_GUIDE.md)
12. Cancel previous speech on every new tap — `speechSynthesis.cancel()` before `.speak()`
13. Rate: `0.85`, Pitch: `1.1`
14. iOS Safari: initialize with silent utterance on first user interaction
15. iOS Safari: `getVoices()` is async — listen for `voiceschanged` event
16. TTS callbacks update mascot mood (speaking → happy, but never override beaming)

### Code Quality
17. TypeScript strict mode — no `any`
18. Tailwind CSS utilities — no separate CSS files except `@keyframes`
19. Functional components only
20. Responsive: 375px (iPhone SE) to 1024px (iPad)
21. `data-testid` on all interactive elements
22. No external API calls — everything is local/offline

### Data
23. `src/data/emojis.ts` is the **single source of truth** for emoji data
24. Five languages: `en`, `zh` (Simplified), `ms` (Malay), `ja` (Japanese), `tl` (Tagalog).
    Adding one means: a column on every `EmojiEntry` and `Countable`, a `CATEGORIES`
    label, a `NUMBER_WORDS` row, and an entry in `src/data/languages.ts`.
25. Japanese uses hiragana for native words and katakana for loanwords — **no kanji**.
    Counting uses the universal ～つ counter (ひとつ…ここのつ, とお), noun first:
    `りんご みっつ`, never `みっつのりんご`.
26. Tagalog nouns stay **bare** in `countables.ts` — the linker attaches to the number
    (`tatlong aso`, but `apat na aso` after a consonant), unlike `zh`/`ms` which bake
    the measure word into the noun.

### What NOT to Build
- No user accounts or login
- No backend or database
- No commerce except one StoreKit 2 non-consumable behind the parental gate — no
  subscriptions, no consumables, no third-party payment SDKs
- No animation libraries — CSS `@keyframes` only
- No state management libraries — React `useState` is sufficient
- No routing — single page app
- No confetti/particles — celebrations are mascot expressions only
