# Cloudmoji — Product Requirements Document

## Problem Statement
Toddlers in multilingual households (especially in Singapore/APAC) lack engaging, low-friction tools for vocabulary building across their home languages. Existing language apps are designed for older children (5+), require reading ability, and focus on single languages.

## Core Insight
Toddlers already interact with emoji keyboards naturally. They recognize emojis as representations of real objects and can vocalize the words. Cloudmoji formalizes this into a learning tool.

## User Personas

### Persona 1: "Kevin & Cloud" (Power Parent)
- Parent: Tech-savvy, 30-40, cares about bilingual education
- Child: 18-36 months, pre-literate, curious about phones/tablets
- Need: A screen time activity that's actually educational
- Languages: English + Mandarin + possibly Malay

### Persona 2: "Sarah" (Expat Parent)
- Parent: Relocated to Singapore, wants child to learn local languages
- Child: 2-4 years, English-dominant
- Need: Exposure to Mandarin/Malay in a fun, pressure-free way
- Languages: English + one target language

### Persona 3: "Ms. Tan" (Preschool Teacher)
- Role: Teaches N1/N2 class (3-4 year olds)
- Need: Interactive vocabulary tool for circle time
- Languages: English + Mandarin (MOE bilingual policy)

## Functional Requirements

### MVP (Phase 1)

#### FR-1: Emoji Grid Display
- Display emojis in a scrollable grid
- Minimum touch target: 64x64 px (shipped at 72px)
- Smooth scrolling at 60fps
- Categories with horizontal tab navigation
- 200 emojis shipped (`src/data/emojis.ts`)

#### FR-2: Tap-to-Speak
- Tap emoji → add to typing row + speak word aloud
- Use the browser's built-in Web Speech API (`speechSynthesis`) with the device's
  installed voices — no TTS service, no audio files, no network call
  (`src/hooks/useTTS.ts`)
- Speech rate: 0.85x (slower for toddler comprehension), pitch 1.1
- Visual word label appears briefly (2.2s) after tap
- Rapid tapping cancels the previous utterance rather than queueing it up;
  Replay All speaks a sequence chained on `onend` and is cancellable

#### FR-3: Typing Row
- Horizontal scrollable row showing tapped emojis
- Auto-scroll to latest emoji
- Tap any emoji in row → re-speak that word
- Replay all button → speaks all words in sequence
- Delete last / Clear all controls
- Maximum 50 emojis in row before auto-clearing oldest

#### FR-4: Language Switching
- Dropdown/picker to select active language
- 5 languages at launch: EN (English), ZH (Simplified Chinese), MS (Malay),
  JA (Japanese), TL (Tagalog) — see `src/data/languages.ts`
- Language switch immediately affects all TTS output and cancels anything
  mid-utterance in the old language
- Persist language preference locally (localStorage)
- Picker shows the language's own short name plus its English name
  (中文 · Chinese, BM · Malay). No flags — a flag is a country, not a language,
  and several of these are spoken across many countries
- Voice availability varies by device. Where an engine ships no voice for a
  language, the app falls back down a related-language chain (e.g. Tagalog →
  Filipino → Malay/Indonesian) rather than letting an English voice mangle it

#### FR-5: Category Filtering
- Categories: Fruits, Food, Animals, Vehicles, Nature, Objects, People, Faces
- "All" category shows everything
- Horizontal scrollable category bar
- Active category visually highlighted
- Smooth transition when switching categories

### Phase 1.5 (Shipped after MVP)

#### FR-6: Count Mode
- Second mode, reached from a bottom tab bar (Words / Count)
- Shows N copies of one countable item; child taps each one and hears the
  running count spoken in the active language
- 84 countables (`src/data/countables.ts`), one random item per round. The count
  starts at 3 and climbs by one each completed round up to 9, then picks a random
  2–9 so the difficulty keeps varying
- Counting grammar is per-language: ZH and MS bake the measure word into the
  noun ("只狗", "ekor anjing"); JA and TL keep the noun bare because the counter
  lives in the number word or the linker
- Same no-failure rule as Words mode: every tap counts, nothing is wrong

### Phase 2 (Post-MVP — not built)

#### FR-7: Voice Recording
- Record button per emoji: kid says the word
- Playback with side-by-side comparison to TTS
- Save recordings locally (IndexedDB / Origin Private File System — the app is a
  web PWA, so no native storage layer)
- Share recording as audio clip or video

#### FR-8: Progress Tracking
- Track which emojis tapped and how often — *partly shipped*: taps are already
  logged to localStorage and surfaced in the hidden stats panel (see NFR-6)
- Track which words spoken (via recording) — not built
- Simple parent dashboard: "Cloud learned 47 words this week" — not built
- Visual progress: fill in emoji outlines as words are learned — not built

#### FR-9: Celebrations
- Milestone reactions at 10 / 25 / 50 / 100 taps — *shipped in MVP*: the mascot
  beams (squinty eyes, wide grin, golden glow) for 3 seconds
- Audio celebrations (applause, cheering) — not built
- Streak tracking ("3 days in a row!") — not built
- Note: confetti and particle effects are explicitly out of scope. Celebrations
  are mascot expressions only (see CLAUDE.md)

#### FR-10: Word of the Day
- Push notification with a new emoji + word
- Opens directly to that emoji
- Rotates through languages
- Not built. Requires Web Push + notification permission, which is a meaningful
  ask for a no-account toddler app — validate demand before building

## Non-Functional Requirements

### NFR-1: Performance
- Page load to interactive: <2 seconds
- Tap to speech: <200ms latency
- Smooth 60fps scrolling with 200 emojis
- Total bundle size: <500KB (excluding fonts)
- Lighthouse Performance score: 90+

### NFR-2: Offline-First
- All emoji and countable data bundled in JavaScript (no fetch calls)
- TTS works offline (Web Speech API uses the device's own installed voices)
- Service worker caches all assets after first load (vite-plugin-pwa + Workbox)
- No network required for any core feature after the first load. First load is
  not fully offline: the fonts (Lilita One, Nunito) come from Google Fonts and
  are only cached by the service worker once fetched

### NFR-3: PWA Quality
- Lighthouse PWA score: 90+
- "Add to Home Screen" works on iOS Safari and Android Chrome
- Standalone display mode (no browser chrome)
- Correct viewport for notch/Dynamic Island
- No pinch-zoom (toddlers trigger accidentally)
- See /docs/engineering/PWA_GUIDE.md

### NFR-4: Privacy
- No accounts, no logins, no backend, no personal data collection
- No advertising, ever
- No Google Analytics, no Facebook pixel, no ad or social SDKs
- In-app event logging is localStorage only, never transmitted
  (`src/lib/measurement.ts`)
- Third-party requests that DO happen, and must stay disclosed:
  - **Vercel Analytics** — sends a pageview per visit plus request metadata
    (referrer, coarse geography, OS/browser, device type). Nothing a child taps
    or hears is sent
  - **Vercel Speed Insights** — sends real-user performance metrics (Core Web
    Vitals) for the same pageviews
  - **Google Fonts** — `index.html` loads the webfonts from
    fonts.googleapis.com / fonts.gstatic.com, so the browser contacts Google on
    first load. Self-hosting the two fonts would remove this
- See /docs/ops/COMPLIANCE.md

### NFR-5: Platform Support
- iOS Safari 16+ (primary — most SG parents use iPhones)
- Android Chrome 100+
- iPad Safari (responsive layout)
- Desktop Chrome/Safari (for testing, not primary target)

### NFR-6: Measurement
- Lightweight in-app event log (localStorage, no PII, capped at 500 events)
- Hidden stats panel behind a parental gate: 5 taps on the title within 2s
- Vercel Web Analytics (free, zero-config) — shipped
- Vercel Speed Insights (Core Web Vitals from real users) — shipped
- No product analytics SDK, no session recording, no backend event pipeline
- See /docs/ops/MEASUREMENT.md

### NFR-7: Stack Constraints
- Web only: Vite + React 19 + TypeScript (strict), Tailwind v4 via
  `@tailwindcss/vite`. Not React Native, not Expo — there is no native app
- Speech is the browser's Web Speech API. No speech SDK, no cloud TTS
- Persistence is browser localStorage. No database, no server, no sync
- Hosted on Vercel as a static PWA with a service worker
- Native app store distribution remains a possible Phase 3 direction, not a
  current requirement
