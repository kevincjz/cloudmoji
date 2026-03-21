# CLAUDE.md — Cloudmoji

## What This Is
Cloudmoji is a PWA where toddlers tap emojis and hear the words spoken aloud in English or Mandarin Chinese. Named after Cloud (Kevin's 27-month-old son), who was typing emojis on a locked iPhone and saying the words aloud — unprompted.

The cloud mascot ("Cloudmoji") is a fluffy white cloud character with a face who reacts to the child's taps — bouncing, showing star-eyes when excited, opening its mouth when speaking, and beaming with joy at milestones.

This is a validation-stage product. No backend, no accounts, no payments. Ship fast, learn fast.

## Reference Implementation
**`reference/prototype.jsx`** contains a fully working React prototype with:
- All 121 emojis with EN + ZH translations
- Working TTS (Web Speech API with iOS workarounds)
- Cloudmoji mascot (CSS-drawn SVG cloud with mood states)
- Typing row with replay/delete/clear
- Category filtering (8 categories)
- Language toggle (EN ↔ 中文)
- Word bubble animation
- Milestone celebrations (mascot beams at 10/25/50/100 taps)
- Dark theme, 72px touch targets, Nunito + Lilita One fonts

**Use this prototype as the source of truth for the UI, interactions, data, and mascot design.** Port it to the Vite + React + Tailwind project structure below.

## Tech Stack
- **Vite + React 18 + TypeScript** (strict mode)
- **Tailwind CSS** (dark theme, utility-first)
- **Web Speech API** (speechSynthesis) for TTS — no external APIs
- **PWA** via vite-plugin-pwa (service worker, manifest, offline)
- **Hosted on Vercel** (free tier, auto-deploys from main)
- **Domain**: cloudmoji.app

## Commands
```bash
npm run dev          # Dev server (localhost:5173)
npm run build        # Production build (outputs to dist/)
npm run preview      # Preview production build locally
npx vercel           # Deploy to Vercel
```

## Project Structure
```
cloudmoji/
├── CLAUDE.md                    ← You are here. Read this first, every session.
├── reference/
│   └── prototype.jsx            ← Working prototype. Port this, don't rewrite from scratch.
├── docs/                        ← Product, design, content, engineering context
├── public/
│   ├── manifest.json            ← PWA manifest
│   ├── icons/                   ← App icons (192, 512) — cloud mascot on gradient bg
│   └── og-image.png             ← Social share image
├── src/
│   ├── main.tsx                 ← Entry point
│   ├── App.tsx                  ← Root component, TTS initialization
│   ├── components/
│   │   ├── CloudMascot.tsx      ← The cloud character (port from prototype SVG)
│   │   ├── EmojiGrid.tsx        ← Scrollable emoji grid with category filtering
│   │   ├── EmojiButton.tsx      ← Individual emoji (72×72px touch target)
│   │   ├── TypingRow.tsx        ← Horizontal row of tapped emojis + controls
│   │   ├── WordBubble.tsx       ← Floating word label animation (2.2s)
│   │   ├── CategoryBar.tsx      ← Horizontal scrollable category tabs
│   │   └── LangToggle.tsx       ← EN ↔ 中文 toggle button
│   ├── data/
│   │   └── emojis.ts            ← ALL emoji data. Single source of truth.
│   ├── hooks/
│   │   ├── useTTS.ts            ← Web Speech API wrapper with iOS fixes
│   │   └── useLocalStorage.ts   ← Persist language preference
│   ├── lib/
│   │   └── measurement.ts       ← Lightweight event logging (localStorage only)
│   └── types.ts                 ← TypeScript interfaces
├── index.html
├── tailwind.config.ts
├── tsconfig.json
├── vite.config.ts               ← PWA plugin config
└── package.json
```

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
- Refer to `reference/prototype.jsx` CloudMascot component for exact SVG paths

### Typography
- **Display / Logo**: Lilita One (chunky, playful, high-energy)
- **Body / UI**: Nunito (weights 700, 800, 900)
- Logo text uses gradient: `linear-gradient(135deg, #FF6B6B, #FFE66D, #4ECDC4)`

### Color Palette
```
Background:       #0F0E2A (deep indigo-black)
                   #1A1145 (mid gradient)
                   #0D2137 (dark blue edge)

Primary (coral):   #FF6B6B — tap feedback, mascot mouth, logo gradient start
Secondary (teal):  #4ECDC4 — active categories, replay button, mascot glow
Accent (gold):     #FFE66D — logo gradient mid, beaming glow, sparkles
Cloud white:       #FFFFFF — mascot body
Blush:             #FFB5B5 — mascot cheeks

Surface card:      rgba(255,255,255,0.04)
Surface border:    rgba(255,255,255,0.06)
Text primary:      #FFFFFF
Text secondary:    rgba(255,255,255,0.4)
Text muted:        rgba(255,255,255,0.2)
```

### Background Effects
Three subtle animated glow orbs (coral, teal, gold) that drift with `bgGlow` animation. Adds warmth to the dark background without distracting from emojis.

## Key Rules — Read Before Every Task

### Toddler UX (non-negotiable)
1. Touch targets: **minimum 64px, prefer 72px**
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
23. `src/data/emojis.ts` is the **single source of truth** — 121 emojis
24. Two languages only: `en` and `zh` (Simplified Chinese)
25. Schema: `{ emoji: string, cat: Category, en: string, zh: string }`

### What NOT to Build
- No user accounts or login
- No backend or database
- No payments or paywalls
- No animation libraries — CSS `@keyframes` only
- No state management libraries — React `useState` is sufficient
- No routing — single page app
- No confetti/particles — celebrations are mascot expressions only

## Emoji Data Schema
```typescript
type Category = "fruits" | "food" | "animals" | "vehicles" | "nature" | "objects" | "people" | "faces";

interface EmojiEntry {
  emoji: string;      // "🍎"
  cat: Category;
  en: string;         // "apple"
  zh: string;         // "苹果"
}
```

## Definition of Done (MVP)
- [ ] All 121 emojis render in scrollable grid with 8 category tabs + "All"
- [ ] Tapping any emoji: speaks word, adds to typing row, shows word bubble, mascot reacts
- [ ] Language toggle (EN ↔ 中文) persisted in localStorage
- [ ] Typing row: replay all, delete last, clear — tapping emoji in row re-speaks it
- [ ] Cloudmoji mascot: 5 mood states (happy, excited, speaking, beaming, love)
- [ ] Milestones at 10/25/50/100 taps: mascot beams for 3 seconds
- [ ] Works offline after first load (PWA service worker)
- [ ] "Add to Home Screen" works on iOS Safari and Android Chrome
- [ ] TTS clear for all 121 emojis in both EN and ZH on iPhone Safari
- [ ] Responsive 375px → 1024px
- [ ] Lighthouse PWA: 90+, Performance: 90+
- [ ] Page load <2s, tap-to-speech <200ms
- [ ] Social meta tags (og:image, twitter:card) for WhatsApp/LinkedIn sharing

## Knowledge Base Map
```
CLAUDE.md                           ← You are here
MASTER_PLAN.md                      ← Strategy, phases, decision points
reference/
└── prototype.jsx                   ← Working prototype — port this
docs/
├── product/
│   ├── HYPOTHESES.md               ← 10 testable hypotheses driving decisions
│   ├── PRD.md                      ← Product requirements (PWA MVP)
│   ├── USER_STORIES.md             ← Prioritized user stories
│   ├── COMPETITOR_ANALYSIS.md      ← Market landscape
│   └── MONETIZATION.md             ← Revenue strategy (Phase 2+)
├── design/
│   ├── DESIGN_SYSTEM.md            ← Colors, typography, spacing
│   ├── UX_PRINCIPLES.md            ← Toddler interaction rules
│   └── ANIMATIONS.md               ← Motion specs (CSS only)
├── content/
│   ├── EMOJI_DATABASE.md           ← Curation rules
│   ├── LANGUAGE_GUIDE.md           ← EN + ZH translation standards
│   └── CATEGORY_TAXONOMY.md        ← 8 categories defined
├── engineering/
│   ├── CLAUDE_CODE_GUIDE.md        ← Session patterns, prompts, code recipes
│   ├── WEB_TTS_GUIDE.md            ← Web Speech API + iOS workarounds
│   ├── PWA_GUIDE.md                ← Service worker, manifest, iOS meta tags
│   ├── DATA_MODEL.md               ← Emoji schema
│   └── TESTING.md                  ← Test strategy
├── growth/
│   ├── FULL_GROWTH_PLAN.md         ← Growth + marketing + monetization
│   ├── LAUNCH_PLAN.md              ← Week-by-week GTM
│   └── CONTENT_MARKETING.md        ← Content strategy
└── ops/
    ├── MEASUREMENT.md              ← How to measure each hypothesis
    ├── TODDLER_TESTING.md          ← Observation protocol
    └── COMPLIANCE.md               ← COPPA/PDPA
templates/
├── FEEDBACK_FORM.md                ← Google Form questions for parents
├── DECISION_LOG.md                 ← 8 decisions logged
└── SPRINT_TEMPLATE.md              ← Sprint planning format
```
