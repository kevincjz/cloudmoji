# Decision Log

### DEC-001: PWA over React Native
**Date**: 2026-03-22 | **Status**: Accepted

Kevin has no mobile app shipping experience. App Store pipeline would consume the entire validation week. PWA gets a shareable URL instantly. Cloud won't know the difference between a PWA on the home screen and a native app.

Revisit when: consumer validation passes and 10K+ MAU justifies native investment.

---

### DEC-002: Two languages (EN + ZH) over five
**Date**: 2026-03-22 | **Status**: Accepted

Cloud hears English and Mandarin at home. These two have the best platform TTS quality. Cuts content work by 60%. Test H6 (bilingual wedge) with the languages that actually matter to the first user.

Revisit when: H6 validated and there's demand signal for JA, MS, or ES.

---

### DEC-003: Ship all 120 emojis, no artificial gating
**Date**: 2026-03-22 | **Status**: Accepted

Cloud was scrolling the full 3,600 emoji keyboard and wasn't overwhelmed. Going to 120 is already 97% curation. Artificial limits would confound H1 testing — if a kid gets bored in 2 minutes, we need to know it's the concept, not the content volume. Categories handle discovery without restricting content.

---

### DEC-004: Dark theme as default
**Date**: 2026-03-22 | **Status**: Accepted

Cloud discovered emojis on the iPhone lock screen (dark). Emojis are designed to pop on dark backgrounds. Easier on eyes for bedtime use. OLED battery savings. Premium aesthetic for parents.

---

### DEC-005: No monetization in validation phase
**Date**: 2026-03-22 | **Status**: Accepted

Every free user in months 1-3 is worth more than $5 in revenue. They're testers, advocates, and content creators. Premature monetization kills PWA sharing velocity. Validate first, charge later.

---

### DEC-006: Hypothesis-driven development
**Date**: 2026-03-22 | **Status**: Accepted

10 hypotheses ranked by "what kills the project if wrong." No feature gets built unless a hypothesis requires it. Test sequence: H3 (pre-launch) → H1 (week 1) → H2 (week 2) → H4/H5/H6 (weeks 3-5) → H7 (month 3+) → H8 (month 4+).

---

### DEC-007: Solo Claude Code, no multi-agent
**Date**: 2026-03-22 | **Status**: Accepted

Kevin is solo. Multi-agent orchestration is over-engineering. One Claude Code session at a time, CLAUDE.md provides context. Three sessions build the whole MVP. Revisit if the project grows to need a team.

---

### DEC-008: localStorage measurement, no external analytics
**Date**: 2026-03-22 | **Status**: Accepted

PostHog, Mixpanel, etc. are premature and add compliance risk for a kids' product. Vercel Web Analytics (free, zero-config) for traffic. localStorage event log for engagement data. Hidden stats panel for Kevin to check manually. This tests all hypotheses without shipping third-party scripts.

---

### Template for Future Decisions

### DEC-XXX: [Title]
**Date**: YYYY-MM-DD | **Status**: Proposed / Accepted / Superseded

[Context, options considered, rationale, consequences. Keep it short.]
