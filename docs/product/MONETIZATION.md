# Monetization Strategy

## Principles
1. **Never ads** — children's product, trust is everything
2. **Free must be genuinely useful** — not a crippled demo
3. **Paid feels like a gift** — parents upgrade because they love what free gave them
4. **Institutional = recurring revenue engine** — B2B stabilizes cash flow

## Tier Structure

### Free (Forever)
- 30 curated emojis (best of each category)
- 2 languages (English + one other)
- Basic TTS (platform native)
- Typing row with replay
- Category filtering (limited categories)

### Pro ($4.99/mo or $29.99/yr)
- 200+ emojis
- All languages (5 at launch, growing)
- Premium TTS voices (where available)
- Progress tracking dashboard
- Voice recording & playback
- Offline mode
- Multiple child profiles
- Custom emoji packs (photo upload)
- No feature-gating on future additions

### Family ($7.99/mo or $49.99/yr)
- Everything in Pro
- Up to 6 family members
- Shared progress view
- Family language goals

### Institutional ($99/yr per classroom)
- Teacher dashboard
- Class progress analytics
- Curriculum alignment tools
- Custom emoji sets
- Bulk student management
- Priority support
- Invoice billing (no credit card)

## Conversion Strategy
1. **Day 1**: Full free experience, no nags
2. **Day 3**: "Cloud learned 25 words!" → soft prompt to unlock more
3. **Day 7**: "Unlock all animals?" → category-specific upgrade prompt
4. **Day 14**: Annual plan discount offer
5. **Ongoing**: New language/emoji packs drive "I want that" moments

## Revenue Channels
| Channel | Year 1 | Year 2 |
|---------|--------|--------|
| iOS subscriptions | 60% | 45% |
| Android subscriptions | 25% | 25% |
| Institutional licenses | 10% | 25% |
| Language pack one-time purchases | 5% | 5% |

## Pricing Research
- Studycat: $9.99/mo or $59.99/yr (we're cheaper)
- Dinolingo: $14.99/mo (we're much cheaper)
- Duolingo Super: $12.99/mo (different audience)
- Gus on the Go: $3.99 one-time per language

Our pricing is positioned as "impulse buy" for parents — less than a coffee.

## Implementation
- **RevenueCat** for subscription management (iOS + Android)
- **Paywall screen**: Friendly, shows value, no dark patterns
- **Parental gate**: Simple math problem before purchase screen
- **Receipt validation**: Server-side via RevenueCat webhook → Supabase
