# Testing Strategy

## Philosophy
A children's product has zero tolerance for broken interactions. One garbled word or unresponsive tap and the parent (your real customer) loses trust. But we're validating, not scaling. Test proportional to risk.

## What We Test and How

### 1. TTS Quality Audit (Pre-Launch, Manual — Tests H3)
The single most important quality check. Every emoji word must sound clear.

Open the PWA on an iPhone Safari. Tap every emoji in EN mode. Then switch to ZH and repeat.

Flag any word that is mispronounced, robotic, or not the intended word. Fix by adjusting the text sent to TTS or replacing with a simpler synonym. Repeat on one Android device.

### 2. Interaction Testing (Manual, 15 minutes)
Walk through every path: tap → speak, typing row, replay, delete, clear, language switch, category tabs, rapid tapping (20 emojis in 10 seconds), word bubble timing, language persistence after refresh.

### 3. PWA Testing (Manual, 10 minutes)
Add to Home Screen on iOS and Android. Airplane mode. Refresh. TTS in offline. Everything must work with zero internet.

### 4. Responsive Testing (Manual, 5 minutes)
iPhone SE (375px), iPhone 15 (393px), iPad (1024px). Touch targets minimum 64px on all sizes.

### 5. Toddler Testing (See docs/ops/TODDLER_TESTING.md)
5 sessions with real children. This is the real test. Tests H1, H9, H10.

### 6. Unit Tests (Optional — Add If Time Permits)
Emoji data integrity (all languages present, no duplicates, valid categories). TTS hook behavior (cancel before speak, rate/pitch, voice fallback).

## Priority If Short on Time
1. TTS quality audit (30 min) — catches the worst bugs
2. Core interaction walkthrough (15 min) — catches broken features
3. PWA offline test (5 min) — ensures the "no download" promise works
4. Toddler test with Cloud (15 min) — validates everything

Ship when these four pass.
