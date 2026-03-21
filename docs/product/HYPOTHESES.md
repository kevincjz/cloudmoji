# Cloudmoji — Hypothesis Register

> **Measurement Plan**: See docs/ops/MEASUREMENT.md for implementation details on how each hypothesis is instrumented.
> **Testing Protocol**: See docs/ops/TODDLER_TESTING.md for the structured observation guide used in H1/H9/H10.

## How to Read This Document

Each hypothesis follows this structure:
- **Hypothesis**: The belief stated as a testable claim
- **Risk if wrong**: What breaks if this assumption is false
- **Test**: How to validate with minimal effort
- **Signal**: What "true" looks like in data/behavior
- **Kill/Pivot threshold**: The line where we stop believing and change course
- **Status**: Untested → Testing → Validated → Invalidated → Pivoted

Hypotheses are ranked by **consequence of being wrong** — the ones at the top kill the entire project if they fail.

---

## TIER 1: EXISTENTIAL — If any of these fail, the project doesn't work

### H1: Toddlers will engage with tap-emoji-hear-word repeatedly
**Hypothesis**: Children aged 18-36 months will voluntarily and repeatedly tap emojis to hear words, sustaining engagement for 5+ minutes per session without adult prompting.

**Risk if wrong**: There is no product. The entire concept fails.

**Test**: Give 5 toddlers (including Cloud) the PWA on an iPad/iPhone. No instructions. Parent nearby but not guiding. Screen record + record the child's face. Time the session from first tap to when they walk away or ask for something else.

**Signal**:
- ✅ Strong: 4/5 kids engage 5+ minutes, return to it unprompted within 24 hours
- ⚠️ Weak: Kids engage 2-4 minutes, need parent encouragement to continue
- ❌ Failed: Kids tap a few times, lose interest under 2 minutes, don't return

**Kill threshold**: If 3/5 toddlers lose interest in under 2 minutes across two separate testing sessions (ruling out mood/context), the core mechanic doesn't work for this age group.

**Pivot if failed**: Test with older kids (4-6). If they engage, the product works but for a different age. If nobody engages, kill it.

**Status**: Untested

---

### H2: Parents perceive this as valuable, not just cute
**Hypothesis**: Parents will view Cloudmoji as a genuinely useful vocabulary/language tool — not just a novelty — and would choose it over handing their child YouTube or a random game.

**Risk if wrong**: Without parent buy-in, there's no distribution, no retention, and no monetization. Kids don't download apps — parents do.

**Test**: After 1 week of use, send a 5-question Google Form to 20 test parents:
1. How many times did your child use Cloudmoji this week? (0 / 1-2 / 3-5 / daily)
2. Did your child say any new words they heard in the app? (Y/N + which words)
3. Would you choose this over YouTube for 10 minutes of screen time? (Y/N)
4. Did you show or mention this to another parent? (Y/N)
5. One sentence: what is this app to you? (free text — looking for "learning" not "toy")

**Signal**:
- ✅ Strong: 60%+ used it 3+ times/week, 50%+ report vocabulary transfer, 50%+ shared unprompted
- ⚠️ Weak: Used 1-2 times then forgotten, parents say "cute" not "useful"
- ❌ Failed: <20% used more than once, parents describe it as a novelty

**Kill threshold**: If fewer than 5/20 parents use it more than twice in a week AND fewer than 3/20 report any word learning, the value proposition isn't landing.

**Pivot if failed**: Interview the parents who DID find it valuable — what was different about their context? Narrow the audience.

**Status**: Untested

---

### H3: Web Speech API delivers acceptable TTS quality for a children's product
**Hypothesis**: The browser's built-in text-to-speech produces clear, correctly pronounced words in both English and Mandarin on common iOS and Android devices — good enough that toddlers hear and repeat the words correctly.

**Risk if wrong**: The core interaction is broken. Kids hear garbled speech, parents lose trust, the "learning" claim collapses.

**Test**: Test every word in the emoji database on:
- iPhone (Safari) — EN and ZH
- Android mid-range (Chrome) — EN and ZH
Record any words that sound wrong, mispronounced, or robotic to the point of being unclear.

**Signal**:
- ✅ Strong: 95%+ words sound clear and natural on iOS, 90%+ on Android
- ⚠️ Weak: 80-90% clear, some words garbled but core vocabulary works
- ❌ Failed: Mandarin is consistently mispronounced or unintelligible on either platform

**Kill threshold**: If more than 20% of Mandarin words are mispronounced on iOS (the primary device for SG parents), the bilingual value prop is undermined. Pivot to pre-recorded audio or native app.

**Pivot if failed**: Record native speaker audio for all Mandarin words (a few hours of work + a Fiverr hire). Fall back to audio files instead of TTS for ZH.

**Status**: Untested — can be tested before shipping to users

---

## TIER 2: GROWTH — If these fail, the product works but doesn't spread

### H4: Parents will organically share this via WhatsApp/social when their child engages
**Hypothesis**: When a toddler does something delightful with Cloudmoji (says a new word, taps excitedly, says something in Mandarin), the parent's natural instinct is to capture and share the moment — and that share includes the app link.

**Risk if wrong**: Growth stalls at Kevin's warm network. The product is good but invisible.

**Test**: Track in two ways:
1. Ask in the feedback form: "Did you share Cloudmoji with anyone? How?"
2. Monitor Vercel analytics for referral patterns (new visitors from WhatsApp/social)
3. Search social media for mentions (TikTok, IG, LinkedIn)

**Signal**:
- ✅ Strong: 20%+ of test users share unprompted within first week. At least 2 parents post videos of their kid using it on social media.
- ⚠️ Weak: 10-20% share when explicitly asked, but don't share spontaneously
- ❌ Failed: <10% share even when asked. No organic social posts.

**Kill threshold**: If after 4 weeks and 100 users, zero parents have shared unprompted, the viral loop doesn't exist. Growth will require paid channels, which doesn't work at this scale.

**Pivot if failed**: Build an in-app "share moment" feature — screen-record the last 10 seconds of the child tapping + audio, auto-generate a shareable clip. Make sharing effortless, not just possible.

**Status**: Untested

---

### H5: Kevin's LinkedIn content about Cloudmoji will drive meaningful traffic
**Hypothesis**: Posts about building Cloudmoji (origin story, toddler UX insights, build-in-public updates) will generate 5,000+ impressions and 200+ link clicks within the first month, because the topic sits at the intersection of Kevin's existing audience interests (AI, founder life, Singapore).

**Risk if wrong**: The primary owned distribution channel underperforms, slowing initial traction.

**Test**: Publish 4 LinkedIn posts over 4 weeks (origin story, UX lessons, Cloud video, milestone update). Track impressions, engagement rate, and link clicks via Vercel referrer data.

**Signal**:
- ✅ Strong: Origin story post gets 10K+ impressions, 300+ link clicks. Subsequent posts maintain momentum.
- ⚠️ Weak: Posts get normal engagement for Kevin's account but don't break out. 50-100 clicks.
- ❌ Failed: Posts underperform Kevin's baseline. Audience isn't interested in kids/parenting content.

**Kill threshold**: Not a kill — just means LinkedIn isn't the channel. Shift effort to TikTok/IG Reels or parent communities instead.

**Status**: Untested

---

### H6: Singapore's bilingual culture creates a stronger wedge than English-only
**Hypothesis**: The EN + ZH bilingual feature is a primary driver of parent interest in Singapore — not just a nice-to-have. Parents specifically value that their child hears Mandarin, because it addresses a real anxiety about English-dominant children losing their mother tongue.

**Risk if wrong**: The multilingual angle isn't a differentiator. We're competing with every kids' vocabulary app on just UX alone.

**Test**: In the feedback form, ask:
- "Which language does your child use more in the app?" (EN / ZH / equal)
- "How important is the Mandarin feature to you?" (Essential / Nice to have / Don't care)
- Track language toggle usage in analytics

**Signal**:
- ✅ Strong: 70%+ of SG parents say Mandarin is "essential." Usage is roughly 50/50 EN/ZH. Parents specifically mention bilingual benefit when sharing.
- ⚠️ Weak: Parents use it but stick to English. Mandarin is a checkbox, not a reason to use the app.
- ❌ Failed: <20% ever toggle to Mandarin. Parents don't mention it in feedback.

**Kill threshold**: Not a kill — but if the bilingual angle doesn't land, the positioning needs to shift from "multilingual learning" to "the simplest vocabulary app" and compete on UX alone.

**Pivot if failed**: Double down on UX differentiation. Test other language pairs (EN + Japanese for expats, EN + Malay for national identity angle).

**Status**: Untested

---

## TIER 3: MONETIZATION — If these fail, the product works and grows but doesn't make money

### H7: Parents will pay $4.99 for a one-time unlock when the free version proves value
**Hypothesis**: After 2+ weeks of free use, 5% or more of active users will pay $4.99 (via Gumroad) to unlock the full emoji set and additional languages.

**Risk if wrong**: The product is a beloved free tool but not a business. Which is fine for now — but limits long-term viability as a standalone venture.

**Test**: After 2 months and 500+ active users, introduce a soft gate:
- Free: all 120 emojis + EN/ZH (unchanged)
- Paid: 3 new language packs ($2.99 each) or full unlock ($4.99)
- Track conversion rate on the Gumroad page

**Signal**:
- ✅ Strong: 5%+ conversion rate. Parents buy within 48 hours of seeing the offer.
- ⚠️ Weak: 1-3% conversion. Some buy but most don't. Price sensitivity.
- ❌ Failed: <1% conversion even with engaged users.

**Kill threshold**: If after 1,000 users and clear engagement, fewer than 10 people pay, the willingness-to-pay isn't there at this price.

**Pivot if failed**: Test lower price ($1.99), test donation model, test institutional sales (preschools may pay even if individual parents won't).

**Status**: Untested — DO NOT TEST until H1, H2, and H4 are validated

---

### H8: Preschools will pay for a classroom license
**Hypothesis**: Singapore preschools will pay $99/year for a classroom version of Cloudmoji, especially when positioned as a bilingual vocabulary tool aligned with MOE's bilingual education policy.

**Risk if wrong**: The B2B revenue path (and the Epitome synergy) doesn't materialise.

**Test**: After consumer validation, approach 5 preschools with a free 1-month pilot. At the end, offer a $99/year license. Track:
- Do teachers actually use it in class?
- Do they see vocabulary improvement?
- Will the principal approve $99?

**Signal**:
- ✅ Strong: 3/5 schools convert to paid after pilot. Teachers ask for features (class view, curriculum alignment).
- ⚠️ Weak: Schools love it but can't justify budget. "Can we keep using the free version?"
- ❌ Failed: Teachers don't integrate it into their routine. It sits unused.

**Kill threshold**: If no school converts after 5 pilots, B2B isn't viable at this stage. Focus on consumer monetization.

**Status**: Untested — DO NOT TEST until Month 4+

---

## TIER 4: PRODUCT EXPANSION — Tests what to build next

### H9: Toddlers will attempt to say words after hearing them (speech imitation)
**Hypothesis**: After hearing a word via TTS, toddlers will spontaneously attempt to say the word aloud — and this behavior is visible to parents, reinforcing the "learning" perception.

**Risk if wrong**: Low. The product still works as passive exposure. But if kids DO repeat words, it unlocks voice recording features and parent delight.

**Test**: Observational. During the 5 toddler test sessions (H1), count instances of the child vocalizing after hearing a word. Ask parents in the feedback form if their child repeated any words.

**Signal**:
- ✅ Strong: 4/5 kids attempt to say at least some words. Parents report new words at home.
- ⚠️ Weak: 1-2 kids vocalize occasionally. Others are passive listeners.
- ❌ Failed: Kids tap but never vocalize.

**Implication**: If strong, prioritize voice recording feature (Phase 2). If weak, deprioritize — focus on tap-and-listen mechanics instead.

**Status**: Untested — observe during H1 testing

---

### H10: Repetition patterns reveal learning — kids tap favorites then explore new emojis
**Hypothesis**: Toddlers will show a pattern of repeatedly tapping familiar emojis (reinforcement), then gradually exploring new ones (discovery). This mirrors natural vocabulary acquisition: master a word, then seek a new one.

**Risk if wrong**: Low. But if true, it validates a spaced repetition feature and gives us a "learning score" metric to show parents.

**Test**: Log emoji tap sequences during test sessions. Analyze:
- Do kids return to the same 5-10 emojis repeatedly?
- Do they eventually venture into new categories?
- Is there a pattern of mastery → exploration?

**Signal**:
- ✅ Strong: Clear clustering around favorites with gradual category expansion over multiple sessions
- ⚠️ Weak: Random tapping with no discernible pattern
- ❌ Failed: Kids only tap 3-4 emojis and never explore

**Implication**: If validated, build a "words learned" tracker for parents and a spaced repetition system that surfaces less-tapped emojis. This becomes a core premium feature.

**Status**: Untested — analyze tap data from first 2 weeks

---

## TESTING SEQUENCE

The hypotheses have dependencies. Test in this order:

```
Week 1: Ship PWA
         ↓
Week 1-2: H3 (TTS quality) — test before sharing widely
         ↓
Week 2-3: H1 (toddler engagement) — test with 5 kids including Cloud
         ├── Also observe: H9 (speech imitation) and H10 (repetition patterns)
         ↓
Week 3-5: H2 (parent value perception) — feedback from 20 parents
         ↓
Week 4-6: H4 (organic sharing) and H5 (LinkedIn traffic) — growth signals
         ├── Also test: H6 (bilingual wedge)
         ↓
Month 3-4: H7 (willingness to pay) — only if H1+H2+H4 validated
         ↓
Month 4-6: H8 (preschool B2B) — only if consumer traction proven
```

## DECISION FRAMEWORK

After 6 weeks, you'll be in one of these positions:

### Scenario A: H1 + H2 + H4 all validated ✅
**Action**: Go all in. Build native app. Pursue monetization. Spin out from personal project.

### Scenario B: H1 validated, H2 weak, H4 failed
**Action**: Kids love it but parents see it as a toy, not a tool. Reposition: lean into "fun screen time" not "learning." Explore licensing content to larger platforms.

### Scenario C: H1 failed
**Action**: Kill the toddler angle. Test with 4-6 year olds. If that fails too, the emoji-as-vocabulary mechanic doesn't work. Move on.

### Scenario D: H1 + H2 validated, H4 failed
**Action**: Great product, no viral loop. Growth requires active distribution. Double down on preschool partnerships (H8) and Kevin's content marketing. Slower growth but potentially more sustainable.

### Scenario E: Everything validates except H7
**Action**: The product is loved and spreads but nobody will pay. Consider: open source it for goodwill + personal brand, use it as a lead gen for Epitome's education vertical, or explore alternative monetization (sponsorships from education brands, government grants for bilingual education tools).
