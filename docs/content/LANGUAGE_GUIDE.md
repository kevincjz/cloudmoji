# Language & Translation Guide

## Supported Languages (MVP)

### English (en)
- **Variant**: American English (en-US)
- **Style**: Simple, colloquial. Use the word a parent would say.
- **Examples**: "french fries" not "chips", "cookie" not "biscuit"
- **TTS**: Excellent on all platforms

### 中文 / Mandarin Chinese (zh)
- **Variant**: Simplified Chinese (zh-CN)
- **Style**: Standard Mandarin, colloquial. Not formal/literary.
- **Characters**: Simplified only (no Traditional for MVP)
- **Phonetic**: Include pinyin with tone marks in phonetic field
- **Examples**: "苹果" (píng guǒ), not "蘋果" (Traditional)
- **TTS**: Excellent on iOS (Ting-Ting), good on Android
- **Notes**: Some food items have Singapore-specific terms. Use standard Mandarin first, add SG variants later.

### Bahasa Melayu (ms)
- **Variant**: Standard Malay (ms-MY), not Indonesian
- **Style**: Colloquial, as used in Singapore/Malaysia
- **Where they differ from Indonesian**: Use Malay (e.g., "kereta" not "mobil" for car)
- **TTS**: ⚠️ Poor on iOS. Use Indonesian (id-ID) as fallback. See TTS_GUIDE.md.
- **Notes**: Some words borrowed from English are acceptable (e.g., "pizza")

### 日本語 / Japanese (ja)
- **Variant**: Standard Japanese (ja-JP)
- **Script**: Hiragana for native Japanese words, Katakana for loanwords
- **Examples**: "りんご" (ringo, apple — native), "ピザ" (piza, pizza — loanword)
- **Phonetic**: Romaji in phonetic field
- **TTS**: Excellent on all platforms
- **Notes**: Use the simplest/most common word. Toddler-speak where it differs.

### Español / Spanish (es)
- **Variant**: Latin American Spanish (es-419), with es-ES TTS
- **Style**: Neutral Latin American. Avoid country-specific slang.
- **Gender**: Include grammatical gender in data (for future sentence building)
- **Examples**: "carro" (not "coche" — LatAm), "papas fritas" (not "patatas fritas")
- **TTS**: Good on all platforms

## Translation Quality Checklist

For each word, verify:
- [ ] Is this the word a parent would actually use with a toddler?
- [ ] Is the spelling correct in standard form?
- [ ] Does the TTS engine pronounce it correctly?
- [ ] Is it unambiguous? (e.g., "mouse" → is it the animal or computer device?)
- [ ] Is it culturally appropriate?

## Problematic Emojis by Language

| Emoji | Issue | Resolution |
|-------|-------|------------|
| 🥑 | "Avocado" not common word in all languages | Use the most common local term |
| 🌮 | "Taco" may not be recognized in Asia | Keep it — emojis teach new things too |
| 🧁 | "Cupcake" is English loanword in many languages | Use loanword if that's what parents say |
| 🍱 | "Bento" is Japanese — other languages? | "Lunch box" equivalent in each language |

## Adding a New Language — Checklist
1. [ ] Identify BCP 47 code and TTS availability
2. [ ] Translate all 120+ words
3. [ ] Native speaker review
4. [ ] TTS pronunciation test (all words, both platforms)
5. [ ] Add phonetic/romanization hints
6. [ ] Add UI strings (settings, categories)
7. [ ] Test parental gate in new language
8. [ ] Update languages.json
9. [ ] PR review with language lead
10. [ ] Add to App Store metadata
