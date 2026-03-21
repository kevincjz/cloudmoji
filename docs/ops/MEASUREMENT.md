# Measurement Plan

## Philosophy
We are NOT building an analytics platform. We are measuring 10 specific hypotheses with the minimum instrumentation needed to get clear answers. Most of our strongest signals come from observation and conversation, not dashboards.

## Three Layers of Measurement

### Layer 1: Observation (No Code Required)
The most valuable data comes from watching toddlers use the app and talking to parents.

**Toddler Testing Sessions** (see docs/ops/TODDLER_TESTING.md)
- Screen record + camera on child's face
- Stopwatch: time from first tap to disengagement
- Tally: how many emojis tapped per minute
- Note: which emojis get repeat taps
- Note: does the child vocalize after hearing a word (H9)
- Note: does the child explore new categories over time (H10)

**Parent Conversations**
- 5-question Google Form sent after 1 week of use
- Casual WhatsApp check-ins: "Is [child] still using it?"
- Track: did this parent share it with anyone? (H4)

### Layer 2: Vercel Analytics (Free, Zero-Config)
Vercel's built-in Web Analytics gives us:
- **Unique visitors** (daily/weekly/monthly)
- **Page views** — in a single-page app, this = sessions
- **Referrer** — where traffic comes from (WhatsApp, LinkedIn, direct)
- **Country** — geographic distribution
- **Device** — iOS vs Android vs Desktop

Enable by adding one line in Vercel dashboard. No code changes needed.

This answers:
- Are people coming? (total visitors)
- Are they coming back? (return visitors over time)
- Where from? (referrer = which growth channel works)
- How did Kevin's LinkedIn post do? (referrer spike on post day)

### Layer 3: Lightweight In-App Event Log
A tiny, privacy-safe event logger that writes to localStorage. No external service, no network calls, no PII. Events are stored on-device and can be manually extracted during toddler testing sessions for analysis.

```typescript
// src/lib/measurement.ts

interface AppEvent {
  t: number;       // timestamp (Date.now())
  e: string;       // event name
  d?: string;      // data (emoji, category, language)
}

const SESSION_KEY = 'es_events';
const MAX_EVENTS = 500; // cap to prevent storage bloat

export function logEvent(event: string, data?: string) {
  try {
    const events: AppEvent[] = JSON.parse(localStorage.getItem(SESSION_KEY) || '[]');
    events.push({ t: Date.now(), e: event, d: data });
    // Keep only last MAX_EVENTS
    if (events.length > MAX_EVENTS) events.splice(0, events.length - MAX_EVENTS);
    localStorage.setItem(SESSION_KEY, JSON.stringify(events));
  } catch {
    // Silent fail — measurement should never break the app
  }
}

export function getEvents(): AppEvent[] {
  try {
    return JSON.parse(localStorage.getItem(SESSION_KEY) || '[]');
  } catch {
    return [];
  }
}

export function getSessionStats() {
  const events = getEvents();
  if (events.length === 0) return null;
  
  const taps = events.filter(e => e.e === 'tap');
  const uniqueEmojis = new Set(taps.map(e => e.d));
  const langSwitches = events.filter(e => e.e === 'lang');
  const categories = events.filter(e => e.e === 'cat');
  const sessionStart = events[0].t;
  const sessionEnd = events[events.length - 1].t;
  
  return {
    totalTaps: taps.length,
    uniqueEmojis: uniqueEmojis.size,
    sessionDuration: Math.round((sessionEnd - sessionStart) / 1000),
    langSwitches: langSwitches.length,
    categoryChanges: categories.length,
    topEmojis: getTopN(taps.map(e => e.d || ''), 5),
    languageSplit: getLangSplit(taps),
  };
}

function getTopN(items: string[], n: number): string[] {
  const counts: Record<string, number> = {};
  items.forEach(i => { counts[i] = (counts[i] || 0) + 1; });
  return Object.entries(counts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, n)
    .map(([emoji]) => emoji);
}

function getLangSplit(taps: AppEvent[]): { en: number; zh: number } {
  // Approximation: look at lang events before each tap
  // Simplified: just count overall lang switches
  return { en: 0, zh: 0 }; // implement based on actual lang state tracking
}
```

### Events to Log

| Event | Data | Hypothesis |
|-------|------|-----------|
| `tap` | emoji character | H1 (engagement), H10 (patterns) |
| `lang` | `en` or `zh` | H6 (bilingual wedge) |
| `cat` | category id | H1 (exploration) |
| `replay` | count of emojis replayed | H1 (engagement depth) |
| `clear` | — | Session boundary signal |
| `session_start` | — | H1 (return visits) |

That's 6 events. Nothing else. No user ID, no device fingerprint, no PII.

### Hidden Stats Panel (For Kevin Only)
Build a hidden panel accessible via a parental gate (tap the app title 5 times rapidly):

```
┌─────────────────────────────┐
│ 📊 Cloudmoji Stats         │
│                             │
│ This session:               │
│   Duration: 4m 23s          │
│   Taps: 47                  │
│   Unique emojis: 19         │
│   Language: EN 62% / ZH 38% │
│   Top: 🐶 🍎 🚀 🦁 🍕       │
│                             │
│ All time:                   │
│   Total sessions: 12        │
│   Total taps: 523           │
│   Unique emojis seen: 87    │
│   Most tapped: 🐶 (34×)     │
│                             │
│ [Export JSON] [Clear Data]  │
└─────────────────────────────┘
```

This gives Kevin a dashboard without building a backend. Export JSON for deeper analysis in a spreadsheet if needed.

---

## Hypothesis → Measurement Map

### H1: Toddler Engagement (5+ minutes unprompted)
**How to measure**:
- PRIMARY: Observation. Stopwatch during 5 toddler test sessions. Video record.
- SECONDARY: In-app event log. `session_start` → last `tap` event = session duration.
- Look at: taps per minute (sustained engagement vs front-loaded burst)

**What "validated" looks like in data**:
- 4/5 test kids: >5 min session, >30 taps, >10 unique emojis
- Event log shows taps distributed across the session (not just first 60 seconds)
- Child returns to the app within 24 hours unprompted

**What "invalidated" looks like**:
- 3/5 kids: <2 min session, <10 taps, only 3-4 unique emojis
- Tap rate drops to zero within first 90 seconds

---

### H2: Parent Value Perception
**How to measure**:
- PRIMARY: Google Form sent to 20 parents after 1 week
- Questions:
  1. "How many times did your child use Cloudmoji this week?" [0 / 1-2 / 3-5 / Daily]
  2. "Did your child say any new words they heard in the app?" [Y/N + which]
  3. "Would you choose this over YouTube for 10 minutes of screen time?" [Y/N]
  4. "In one sentence, what is this app to you?" [Free text]
  5. "Would you pay $4.99 for a full version with more languages?" [Y/N / Maybe]
- SECONDARY: Vercel Analytics — return visitor rate after week 1

**What "validated" looks like**:
- 12/20 parents: used 3+ times that week
- 10/20: report vocabulary transfer ("she said 苹果 at dinner!")
- 10/20: would choose over YouTube
- Free text uses words like "learning", "educational", "vocabulary" — not just "cute"
- Return visitor rate >30% at day 7

**What "invalidated" looks like**:
- <5/20 used more than twice
- <3/20 report any vocabulary effect
- Free text is uniformly "cute" / "fun" with no learning signal

---

### H3: TTS Quality
**How to measure**:
- PRIMARY: Kevin tests every word on iPhone Safari (EN + ZH) before shipping
- Create a checklist spreadsheet: emoji | EN sounds clear? | ZH sounds clear?
- SECONDARY: Ask 3 Mandarin-speaking parents "do the Chinese words sound right?"

**What "validated" looks like**:
- 95%+ of EN words clear and natural on iOS Safari
- 90%+ of ZH words clear and natural on iOS Safari
- No parent flags a mispronounced word in the first week

**What "invalidated" looks like**:
- >10 ZH words mispronounced on iOS (e.g., wrong tone, garbled syllable)
- A parent says "the Chinese sounds weird"

**Measurement is pre-launch** — test this before sharing with anyone.

---

### H4: Organic Sharing
**How to measure**:
- PRIMARY: Count mentions. Search WhatsApp groups, social media for "cloudmoji"
- Google Form Q: "Did you share this with anyone?" [Y/N / How many people?]
- SECONDARY: Vercel Analytics referrer data — new traffic from wa.me, t.me, instagram.com, linkedin.com
- Track: new unique visitors that Kevin did NOT personally send the link to

**What "validated" looks like**:
- 4+ parents share unprompted in week 1
- At least 1 social media post (TikTok, IG story, LinkedIn) from a non-Kevin parent
- Vercel shows referral traffic from social/messaging apps Kevin didn't post to

**What "invalidated" looks like**:
- Zero shares unless Kevin explicitly asks
- All traffic is direct (Kevin's personal link sends)

---

### H5: LinkedIn Content Traction
**How to measure**:
- LinkedIn native analytics: impressions, clicks, engagement rate
- Vercel referrer data: visitors from linkedin.com on the day of each post
- Track across 4 posts over 4 weeks

**What "validated" looks like**:
- Origin story post: 5K+ impressions, 100+ link clicks
- Subsequent posts maintain >50% of initial performance
- linkedin.com appears as top 3 referrer in Vercel

---

### H6: Bilingual Wedge (Mandarin Matters)
**How to measure**:
- In-app event log: `lang` events — how often do users switch to ZH?
- Google Form Q: "How important is the Mandarin feature?" [Essential / Nice to have / Don't care]
- Observation: during toddler tests, does the parent switch to ZH?

**What "validated" looks like**:
- 70%+ SG parents say Mandarin is "Essential"
- Event log shows 30%+ of taps happen in ZH mode
- Parents specifically mention bilingual benefit when sharing

---

### H7: Willingness to Pay ($4.99)
**DO NOT MEASURE until H1 + H2 + H4 are validated (Month 3+)**

**How to measure when ready**:
- Add a "Get more languages" button → links to Gumroad page
- Track: click-through rate, Gumroad conversion rate
- A/B test pricing: $2.99 vs $4.99 vs $6.99

---

### H8: Preschool B2B
**DO NOT MEASURE until Month 4+**

**How to measure when ready**:
- Pilot with 5 preschools. Free for 1 month. Then offer $99/yr.
- Track: did teachers use it? How often? Did they convert?

---

### H9: Speech Imitation
**How to measure**:
- Observation only. During H1 toddler testing, tally:
  - How many times does the child attempt to say a word after hearing it?
  - Which words do they attempt? (simple vs complex)
  - Do they get close to the correct pronunciation?
- Ask parents in week 1 form: "Did your child repeat any words from the app?"

---

### H10: Repetition Patterns
**How to measure**:
- In-app event log: analyze tap sequences
- Export JSON from the hidden stats panel, load in spreadsheet
- Look for: clustering (same emojis tapped repeatedly), then expansion to new ones over sessions
- Compare first session tap distribution vs fifth session

---

## Measurement Timeline

| When | What | Tools |
|------|------|-------|
| Day 3 (pre-launch) | H3: TTS quality audit | Kevin's iPhone, checklist |
| Day 4 | H1: Toddler engagement test (5 kids) | Stopwatch, screen record, video camera |
| Day 4 | H9: Speech imitation (observe during H1) | Same recording |
| Day 5 | H4: First sharing signal | WhatsApp monitoring, Vercel referrers |
| Day 5 | H5: LinkedIn post performance | LinkedIn analytics |
| Week 2 | H2: Parent value survey | Google Forms (20 responses) |
| Week 2 | H6: Bilingual usage split | In-app event log export |
| Week 2 | H10: Repetition patterns | In-app event log export |
| Week 4 | H4: Organic sharing at scale | Vercel referrers, social search |
| Month 3 | H7: Willingness to pay | Gumroad page + conversion tracking |
| Month 4+ | H8: Preschool B2B | Pilot conversion rate |

## What We Are NOT Measuring
- Session replays (creepy for a kids' app, COPPA concern)
- User demographics (no accounts, no PII)
- Funnel conversion (no funnel to optimize yet)
- A/B tests (not enough traffic to be statistically significant)
- Retention curves (meaningful only at 1000+ users)
- Revenue metrics (no revenue yet)

All of these become relevant in Phase 2. For now, they are noise.
