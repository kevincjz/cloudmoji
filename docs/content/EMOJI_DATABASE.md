# Emoji Database Curation

## Selection Philosophy
Every emoji in Cloudmoji must pass the "Cloud test": would a toddler point at this object in real life and want to know its name?

## Curation Process

### Step 1: Candidate Pool
Start from the full Unicode emoji set (~3,600 emojis). Filter by:
1. Is it a concrete, visible thing? (Not abstract symbols)
2. Would a toddler encounter or recognize it?
3. Is it safe and age-appropriate?
4. Does it render well at 40pt on both iOS and Android?

### Step 2: Word Assignment
For each emoji, assign the word a parent would naturally use:
- 🐶 → "dog" (not "dog face" — that's the Unicode name, not natural speech)
- 🍚 → "rice" (not "cooked rice" or "bowl of rice")
- 🚗 → "car" (not "automobile" or "sedan")

### Step 3: Category Assignment
Each emoji belongs to exactly one category. When ambiguous, choose the category a toddler would associate it with:
- 🍅 → Food (not Nature, even though it's technically a fruit)
- 🐔 → Animals (not Food)
- ⭐ → Nature (not Objects)

### Step 4: Tier Assignment
Free tier (30 emojis): the most universally recognizable, engaging subset.
Pro tier: everything else that passes the Cloud test.

## Category Definitions

| Category | Criteria | Examples | Count Target |
|----------|----------|----------|-------------|
| Fruits | Edible fruits | 🍎🍌🍇🍓🍉🥝🍊🍑🍋🍍🥭🫐🥥🥑🍒 | 15 |
| Food | Prepared food, drinks, meals | 🍕🍔🍟🍚🍞🧀🥚🍗🍰🍦🍪🍩🍫🧁🥐🍜🍱🥪🌮🍿🧃🍼🥛🍤🌽🥕🥦🍅🥒 | 28 |
| Animals | Living creatures | 🐶🐱🐰🐻🐸🐔🐷🐮🐵🐍🐢🐘🦁🐟🦋🐝🐛🐧🦆🐙🦀🐊🦒🐳🐬🐞🦈 | 28 |
| Vehicles | Transportation | 🚗🚌🚂✈️🚀🚲🚁⛵🚒🚑 | 10 |
| Nature | Weather, sky, plants, elements | 🌈⭐🌙☀️🌊🌸🌲🍄🔥❄️ | 10 |
| Things | Objects, toys, sports, places | ⚽🏀🎈🎸📚✏️🎨🔑🎁🏠👟👒🧸🎵💎🎪🎢 | 17 |
| People | Body parts, gestures | 👶👀👃👄👋👏❤️ | 7 |
| Faces | Emotions | 😀😢😡😴🤩😂 | 6 |

## Emoji Rendering Differences
Some emojis look significantly different across platforms. Flag for QA:
- 🥑 Avocado: cut open (Apple) vs whole (some Android)
- 🔫 Water gun (Apple) vs pistol (some older Android)
- 🍑 Peach: varies in suggestiveness by platform

## Future Expansion
Languages to add (priority order based on APAC market):
1. Tamil (ta) — Singapore's 4th official language
2. Hindi (hi) — large Indian diaspora
3. Tagalog (tl) — large Filipino community in SG
4. Thai (th) — ASEAN expansion
5. Korean (ko) — K-culture popularity
6. Vietnamese (vi) — ASEAN expansion
7. Indonesian (id) — overlap with Malay
8. Arabic (ar) — Middle East expansion
9. French (fr) — global demand
10. Portuguese (pt) — Brazil market

## Data Quality Automation
Build a validation script (run in CI):
```bash
# Checks:
# 1. Every emoji has words for all supported languages
# 2. No duplicate emoji entries
# 3. All categories referenced exist in categories.json
# 4. Free tier has exactly 30 emojis
# 5. Sort orders are unique within categories
# 6. No excluded content (weapons, alcohol, etc.)
node scripts/validate-emoji-data.js
```
