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
- Minimum touch target: 64x64 points
- Smooth scrolling at 60fps
- Categories with horizontal tab navigation
- Support 120+ emojis at launch

#### FR-2: Tap-to-Speak
- Tap emoji → add to typing row + speak word aloud
- Use platform-native TTS (expo-speech)
- Speech rate: 0.85x (slower for toddler comprehension)
- Visual word label appears briefly (2s) after tap
- Support queued speech (rapid tapping doesn't break)

#### FR-3: Typing Row
- Horizontal scrollable row showing tapped emojis
- Auto-scroll to latest emoji
- Tap any emoji in row → re-speak that word
- Replay all button → speaks all words in sequence
- Delete last / Clear all controls
- Maximum 50 emojis in row before auto-clearing oldest

#### FR-4: Language Switching
- Dropdown/picker to select active language
- 5 languages at launch: EN, ZH, MS, JA, ES
- Language switch immediately affects all TTS output
- Persist language preference locally
- Flag + native name display (🇬🇧 English, 🇨🇳 中文)

#### FR-5: Category Filtering
- Categories: Fruits, Food, Animals, Vehicles, Nature, Things, People, Faces
- "All" category shows everything
- Horizontal scrollable category bar
- Active category visually highlighted
- Smooth transition when switching categories

### Phase 2 (Post-MVP)

#### FR-6: Voice Recording
- Record button per emoji: kid says the word
- Playback with side-by-side comparison to TTS
- Save recordings locally (MMKV or filesystem)
- Share recording as audio clip or video

#### FR-7: Progress Tracking
- Track which emojis tapped and how often
- Track which words spoken (via recording)
- Simple parent dashboard: "Cloud learned 47 words this week"
- Visual progress: fill in emoji outlines as words are learned

#### FR-8: Celebrations
- Milestone animations (10 words, 50 words, 100 words)
- Confetti, stars, character reactions
- Audio celebrations (applause, cheering)
- Streak tracking ("3 days in a row!")

#### FR-9: Word of the Day
- Push notification with a new emoji + word
- Opens directly to that emoji
- Rotates through languages

## Non-Functional Requirements

### NFR-1: Performance
- Page load to interactive: <2 seconds
- Tap to speech: <200ms latency
- Smooth 60fps scrolling with 120+ emojis
- Total bundle size: <500KB (excluding fonts)
- Lighthouse Performance score: 90+

### NFR-2: Offline-First
- All emoji data bundled in JavaScript (no fetch calls)
- TTS works offline (Web Speech API uses platform-native voices)
- Service worker caches all assets after first load
- No network required for ANY core feature

### NFR-3: PWA Quality
- Lighthouse PWA score: 90+
- "Add to Home Screen" works on iOS Safari and Android Chrome
- Standalone display mode (no browser chrome)
- Correct viewport for notch/Dynamic Island
- No pinch-zoom (toddlers trigger accidentally)
- See /docs/engineering/PWA_GUIDE.md

### NFR-4: Privacy
- No personal data collection whatsoever
- No advertising, ever
- No third-party scripts (no Google Analytics, no Facebook pixel)
- Event logging is localStorage only (never transmitted)
- See /docs/ops/COMPLIANCE.md

### NFR-5: Platform Support
- iOS Safari 16+ (primary — most SG parents use iPhones)
- Android Chrome 100+
- iPad Safari (responsive layout)
- Desktop Chrome/Safari (for testing, not primary target)

### NFR-6: Measurement
- Lightweight in-app event log (localStorage, no PII)
- Hidden stats panel behind 5-tap easter egg
- Vercel Web Analytics (free, zero-config)
- See /docs/ops/MEASUREMENT.md
