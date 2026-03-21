# Cloudmoji — Master Plan

## Origin
Cloud (27 months) was typing emojis on a locked iPhone and saying the words aloud — unprompted. Cloudmoji formalizes that natural behavior into a multilingual vocabulary tool.

## Vision
The simplest way for toddlers to learn words in any language — by tapping the emojis they already love.

## Current Reality
- Solo founder (Kevin) with a day job (Epitome)
- Building with Claude Code, no team
- No mobile app experience
- $15 budget (domain only)
- Personal project — spin out if it works

## Strategy: Hypothesis-Driven Validation
We are NOT building an app. We are running experiments. See docs/product/HYPOTHESES.md for the full register. Every decision flows from what the hypotheses tell us.

**The three existential hypotheses** (if ANY fail, we stop):
1. H1: Toddlers engage with tap-emoji-hear-word for 5+ minutes unprompted
2. H2: Parents perceive this as valuable learning, not just a toy
3. H3: Web Speech API sounds good enough in EN + ZH on iOS/Android

## Phase 1: PWA MVP (This Week)

### What We Ship
A single-page PWA. No backend, no accounts, no payments.
- 120+ emojis across 8 categories
- Tap → hear word in English or 中文
- Typing row with replay/delete/clear
- Language toggle (EN ↔ 中文)
- PWA: offline, "Add to Home Screen"
- Lightweight measurement (see docs/ops/MEASUREMENT.md)

### Tech Stack
| Layer | Choice |
|-------|--------|
| Framework | Vite + React 18 + TypeScript |
| Styling | Tailwind CSS |
| TTS | Web Speech API (speechSynthesis) |
| PWA | vite-plugin-pwa |
| Hosting | Vercel (free tier) |
| Measurement | localStorage event log + Vercel Analytics |
| Domain | cloudmoji.app |

### 5-Day Sprint
| Day | Focus | Done When |
|-----|-------|-----------|
| 1 | Scaffold + core loop | Tap emoji → hear word works |
| 2 | Typing row + categories + language toggle | Full feature set |
| 3 | PWA + deploy + TTS quality testing (H3) | Live at cloudmoji.app |
| 4 | Test with Cloud + 4 other toddlers (H1) | Video recordings captured |
| 5 | Share with 20 parents, collect feedback (H2) | Feedback form responses in |

## Phase 2: Delight (Weeks 4-6, only if H1+H2 validated)
- Voice recording: kids record themselves saying words
- Celebration animations (confetti at 10, 25, 50, 100 words)
- Share clips to WhatsApp/Instagram
- Progress dashboard for parents
- "Word of the Day" push notification (if native app)

## Phase 3: Intelligence (Weeks 7-10, only if growth validated)
- Spaced repetition: surface less-tapped emojis
- Speech recognition: pronunciation feedback
- Sentence builder: 🐶 + 🍎 → "The dog eats an apple"
- Additional languages (JA, MS, ES)

## Phase 4: Platform (Months 4-6, only if monetization validated)
- Native app (React Native) for App Store distribution
- Preschool/classroom licensing (B2B)
- Community language packs
- Epitome integration for education vertical

## Decision Points
| After | If validated | If not |
|-------|-------------|--------|
| Week 2 | Proceed to parent testing | Observe older kids (4-6). If still fails, kill. |
| Week 5 | Invest in Phase 2 features | Interview engaged parents. Narrow audience. |
| Month 2 | Start monetization experiments | Reposition or open-source for brand value. |
| Month 4 | Build native app | Stay PWA, focus on B2B schools. |

## What This Project Is NOT
- Not an Epitome product (yet)
- Not a React Native app (yet)
- Not monetized (yet)
- Not analytics-heavy (yet)
- Not multi-language beyond EN + ZH (yet)

Everything in parentheses becomes real only when data says it should.

## Knowledge Base Map
```
CLAUDE.md                           ← Claude Code reads this every session
MASTER_PLAN.md                      ← You are here
docs/
├── product/
│   ├── HYPOTHESES.md               ← The 10 testable hypotheses driving all decisions
│   ├── PRD.md                      ← Product requirements (scoped to PWA MVP)
│   ├── USER_STORIES.md             ← Prioritized user stories
│   ├── COMPETITOR_ANALYSIS.md      ← Market landscape
│   └── MONETIZATION.md             ← Revenue strategy (Phase 2+)
├── design/
│   ├── DESIGN_SYSTEM.md            ← Colors, typography, spacing, touch targets
│   ├── UX_PRINCIPLES.md            ← Toddler interaction research & rules
│   └── ANIMATIONS.md               ← Motion specs (CSS only)
├── content/
│   ├── EMOJI_DATABASE.md           ← Curation rules, inclusion/exclusion criteria
│   ├── LANGUAGE_GUIDE.md           ← EN + ZH translation standards
│   └── CATEGORY_TAXONOMY.md        ← Category definitions and organization
├── engineering/
│   ├── DATA_MODEL.md               ← Emoji data schema
│   ├── WEB_TTS_GUIDE.md            ← Web Speech API implementation + iOS gotchas
│   ├── PWA_GUIDE.md                ← PWA setup, service worker, manifest
│   ├── TESTING.md                  ← Test strategy (unit + toddler)
│   └── OFFLINE_FIRST.md            ← Offline architecture
├── growth/
│   ├── FULL_GROWTH_PLAN.md         ← Complete growth + marketing + monetization
│   ├── LAUNCH_PLAN.md              ← Week-by-week GTM
│   └── CONTENT_MARKETING.md        ← Content strategy + templates
└── ops/
    ├── MEASUREMENT.md              ← How we measure each hypothesis (lightweight)
    ├── COMPLIANCE.md               ← COPPA/PDPA for children's product
    └── TODDLER_TESTING.md          ← Protocol for testing with real children
templates/
├── SPRINT_TEMPLATE.md
└── DECISION_LOG.md
```
