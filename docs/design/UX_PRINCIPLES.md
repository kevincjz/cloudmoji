# UX Principles for Toddler Design

## Research Summary
Toddlers (18mo–3yr) interact with touchscreens differently from older children and adults. These principles are derived from developmental psychology research and observing Cloud's natural behavior.

## Core Principles

### 1. One Tap = One Action = One Reward
Every tap must produce an immediate, obvious result. No double-taps, long-presses, or swipe gestures for core interactions. Tap emoji → hear word + see animation + feel haptic. That's the complete loop.

### 2. No Reading Required
Zero text in the core experience. All navigation is visual (emojis, colors, shapes). The word label is for the PARENT looking over the shoulder, not the child.

### 3. No Failure States
There is no wrong answer. Tapping any emoji is a success. There are no quizzes, no scores, no red X marks in the core experience. Every interaction is positive reinforcement.

### 4. Forgiving Touch
Toddler fingers are imprecise and often use the whole palm. Design for:
- Large touch targets (72pt minimum for primary actions)
- Generous spacing between targets (8pt gap minimum)
- No penalty for accidental taps (everything is undoable)
- No swipe-to-delete (too easy to trigger accidentally)

### 5. Consistent Layout
The screen layout should never change unexpectedly. Categories filter the content, but the grid stays in the same place. The typing row is always at the top. The speaking always comes from the same interaction.

### 6. Repetition is Learning
Toddlers LOVE repetition. Let them tap the same emoji 50 times. Never say "you already learned this." Never hide emojis they've seen before. The replay button exists because repetition is the point.

### 7. Parent-Accessible, Child-Proof
Settings, language selection, and payments are behind a parental gate (simple math problem: "What is 12 + 7?"). This keeps the child in the safe play space and gives parents control.

## Parental Gate Design
```
┌────────────────────────┐
│                        │
│   To access settings,  │
│   solve this:          │
│                        │
│      12 + 7 = ?        │
│                        │
│   [  ] [  ] [  ] [  ]  │
│    15   19   21   17   │
│                        │
└────────────────────────┘
```
- Multiple choice (4 options), not text input
- Random math problem each time
- Difficulty appropriate for adults, not children
- No "close" button visible to child

## Screen Time Considerations
- No autoplay content (child must initiate every interaction)
- No infinite scroll / algorithmic engagement
- Optional timer (parent-set in settings)
- Natural stopping points (end of category)
- No badges/streaks that create FOMO (Phase 2 celebrations are purely positive)

## Accessibility Notes
- VoiceOver: each emoji button announces "tap to hear [word] in [language]"
- Reduce Motion: disable bounce/confetti animations
- Dynamic Type: word labels scale with system settings
- Color: no information conveyed by color alone
