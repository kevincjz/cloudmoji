# Language & Translation Guide

Five languages ship today: `en`, `zh`, `ms`, `ja`, `tl`. That list is defined in
`src/types.ts` (`type Language`) and `src/data/languages.ts` (`LANGUAGES`). Adding a
sixth means touching both, plus every row of `src/data/emojis.ts` (200 entries) and
`src/data/countables.ts` (84 entries) — the schema is a flat record with one field per
language, so a missing translation is a TypeScript error, not a runtime blank.

Speech is the browser's Web Speech API (`speechSynthesis`), wrapped in
`src/hooks/useTTS.ts`. Voice selection walks a per-language prefix chain defined in
`src/data/languages.ts` and implemented in `src/lib/voices.ts`. See
`docs/engineering/WEB_TTS_GUIDE.md`.

## Supported Languages (MVP)

### English (en)
- **Variant**: American English (en-US)
- **Style**: Simple, colloquial. Use the word a parent would say.
- **Examples**: "french fries" not "chips", "cookie" not "biscuit"
- **TTS**: Excellent on all platforms. Voice prefix chain: `en`.
- **Counting**: English is the only language with a plural rule in code. `buildPhrase()`
  in `src/components/CountMode.tsx` pluralises the noun (`fish` → `fish`, `-y` → `-ies`,
  `s/sh/ch/x/z` → `-es`, else `-s`). New irregular nouns need a case added there.

### 中文 / Mandarin Chinese (zh)
- **Variant**: Simplified Chinese (zh-CN)
- **Style**: Standard Mandarin, colloquial. Not formal/literary.
- **Characters**: Simplified only (no Traditional)
- **Examples**: "苹果" not "蘋果" (Traditional)
- **TTS**: Excellent on iOS (Ting-Ting), good on Android. Voice prefix chain: `zh`.
- **Counting**: the measure word is **baked into the countable noun** — see
  "Classifiers, counters and linkers" below. Note that `NUMBER_WORDS.zh` uses 两 for
  "two", not 二, because 两 is the form used before a measure word.
- **Notes**: Some food items have Singapore-specific terms. Use standard Mandarin first;
  SG variants are a planned refinement, not shipped.

### Bahasa Melayu (ms)
- **Variant**: Standard Malay (ms-MY), not Indonesian
- **Style**: Colloquial, as used in Singapore/Malaysia
- **Where they differ from Indonesian**: use Malay (e.g. "kereta" not "mobil" for car)
- **TTS**: ⚠️ Poor or absent on iOS. The voice prefix chain is `ms` → `id`, so a device
  with no Malay voice lands on Indonesian rather than the engine's English default.
- **Counting**: the measure word is **baked into the countable noun** (see below).
- **Notes**: English loanwords are fine where that is the everyday word ("piza", "burger",
  "basikal").

### 日本語 / Japanese (ja)
- **Variant**: Standard Japanese (ja-JP)
- **Script**: **Hiragana for native Japanese words, katakana for loanwords. No kanji,
  anywhere.** This is enforced by convention, not by a test — a grep of `ja:` fields in
  `emojis.ts` and `countables.ts` currently returns zero CJK ideographs. Kanji are wrong
  here for two reasons: the audience is pre-literate, and many kanji have multiple
  readings that a TTS engine can pick wrong with no surrounding context.
- **Examples**: "りんご" (ringo, apple — native), "ピザ" (piza, pizza — loanword),
  "ぞう" (zou, elephant), "ハンバーガー" (hanbaagaa, hamburger)
- **TTS**: Excellent on all platforms. Voice prefix chain: `ja`.

#### The tooth exception — 🦷 is ハ (katakana), on purpose
`🦷` is `ja: "ハ"` in **both** `src/data/emojis.ts` and `src/data/countables.ts`, and
`🪥` toothbrush is `"ハブラシ"`. These are katakana even though the word is native
Japanese, which breaks the rule above. **This is deliberate. Do not "correct" it.**

A standalone hiragana `は` is parsed by ja-JP speech engines as the topic particle *wa*
and voiced "wa" — the wrong sound entirely. Nothing in a single-word utterance gives the
engine the context to decide otherwise. Writing it as katakana `ハ` forces the /ha/
reading. The trade-off is a native word in the loanword script, which no toddler will
notice, versus the wrong phoneme, which every listener will. Pronunciation wins.

Any future single-mora entry that collides with a particle (`は`, `を`, `へ`, `も`, `の`)
should get the same treatment, and a comment saying why.

#### Japanese counting — universal counter, noun first
`NUMBER_WORDS.ja` is the universal ～つ counter: ひとつ, ふたつ, みっつ, よっつ, いつつ,
むっつ, ななつ, やっつ, ここのつ, とお. This is the first counting system Japanese
children learn, and it sidesteps the whole classifier system (本 / 匹 / 台 / 枚 …) that
would otherwise need a per-noun field.

Word order is **noun first, count last**, with a space and no particle:

```
りんご みっつ     ← what a parent says out loud
みっつのりんご     ← bookish; NOT what the app speaks
```

`buildPhrase()` builds this as `` `${item.ja} ${numWord}` ``. Because the counter is
already fused into the number word, the **noun never changes form** — which is why the
`ja` field in `countables.ts` stays bare.

### Tagalog / Filipino (tl)
- **Variant**: Tagalog, spoken as Filipino. The app speaks with the BCP-47 tag `fil-PH`,
  because iOS tags its Filipino voice `fil` while other engines use `tl`.
- **Style**: **Everyday Metro Manila register** — the word a parent actually uses at home,
  not the Komisyon-sa-Wikang-Filipino purist coinage.
- **Loanwords are correct where they are the common word.** Spanish-derived vocabulary is
  not a compromise, it is the real language: "kotse" (car), "sapatos" (shoes), "kutsara"
  (spoon), "silya" (chair), "kuneho" (rabbit), "elepante", "leon", "bisikleta", "regalo",
  "kendi", "piyano", "mansanas". English-derived words are fine on the same test —
  "stoplight", "burger", "pizza", "strawberry" — where that is what is said out loud.
- **TTS**: ⚠️ Most devices ship no Filipino voice at all. Voice prefix chain:
  `fil` → `tl` → `ms` → `id` → `es`. Malay and Indonesian come before Spanish because
  Tagalog and Malay are both Austronesian — same five pure vowels, same Latin
  orthography, and both write /ŋ/ as "ng", which the numeral linker uses constantly
  (isang, tatlong, ngipin). Spanish handles the Spanish-derived loanwords but mangles
  "ng", so it sits last. Without this chain the engine falls through to English and says
  "saging" as SAY-jing.
- **Counting**: the noun stays **bare**; the linker attaches to the number (see below).

## Classifiers, counters and linkers

This is the single most common source of mistakes in this repo, because the four
non-English languages split two-and-two and it is tempting to copy one pattern across all
of them. `src/data/countables.ts` says it in a comment; here is the reasoning.

**`zh` and `ms` bake the measure word into the countable noun.**
The measure word is grammatically part of the noun phrase and varies per noun with no
rule you can derive from the word itself — 只狗 but 头牛 but 条鱼; `ekor anjing` but
`biji epal` but `buah kereta`. There is no algorithm, so the correct measure word is
stored with the noun and `buildPhrase()` just concatenates:

| lang | stored `countables` value | spoken at 3 |
|------|---------------------------|-------------|
| zh   | `只狗`                     | `三只狗` (no spaces) |
| ms   | `ekor anjing`             | `tiga ekor anjing` |

**`ja` and `tl` keep the noun bare.**
Not because they lack classifiers, but because in the form this app uses, the counting
morphology lives on the *number*, never on the noun:

| lang | stored `countables` value | spoken at 3 | where the morphology sits |
|------|---------------------------|-------------|---------------------------|
| ja   | `いぬ`                     | `いぬ みっつ` | fused into the ～つ number word |
| tl   | `aso`                     | `tatlong aso` | the linker, suffixed to the number |

Putting a counter into the `ja` field would double-count (`いぬ いっぴき みっつ`), and
putting a linker into the `tl` field would strand it away from the number it must attach
to. Adding a countable means writing the measure word for `zh`/`ms` and leaving `ja`/`tl`
in their dictionary form.

### The Tagalog linker rule
The linker joins numeral to noun and attaches to the **number**, not the noun.
`buildPhrase()` derives it from the number's last letter:

| number ends in | linker | example |
|----------------|--------|---------|
| a vowel        | `-ng` suffix | tatlo → **tatlong** aso |
| `n`            | `-g` suffix  | (no 1–10 number hits this; kept for future numerals) |
| any other consonant | separate `na` | apat → **apat na** aso |

`NUMBER_WORDS.tl` is isa, dalawa, tatlo, apat, lima, anim, pito, walo, siyam, sampu — so
in practice 4, 6 and 9 take `na` and the rest take `-ng`. **Nouns are never pluralised
after a numeral** (no "mga"): `limang aso`, not `limang mga aso`.

## Translation Quality Checklist

For each word, verify:
- [ ] Is this the word a parent would actually use with a toddler?
- [ ] Is the spelling correct in standard form?
- [ ] Does the TTS engine pronounce it correctly? — test on a real device, not by reading
- [ ] Is it unambiguous? (e.g., "mouse" → is it the animal or computer device?)
- [ ] Is it culturally appropriate?
- [ ] `ja` only: hiragana/katakana only, no kanji — unless it is a documented
      particle-collision exception like 🦷
- [ ] `zh`/`ms` only: if this is a countable, does the `countables.ts` value carry the
      measure word?
- [ ] `tl`/`ja` only: if this is a countable, is the `countables.ts` value bare?

Note that `emojis.ts` and `countables.ts` are independent — the same emoji can and does
carry a different word in each. 🌸 is `さくら` in `emojis.ts` (the flower shown) and
`おはな` in `countables.ts` (the word you count with a toddler). Change one and check the
other.

## Problematic Emojis by Language

| Emoji | Issue | Resolution |
|-------|-------|------------|
| 🦷 | ja: standalone `は` is read as the topic particle *wa* | Use katakana `ハ`. Deliberate, documented above. Same for 🪥 `ハブラシ`. |
| 🥑 | "Avocado" not a common word in all languages | Use the most common local term ("abokado", "アボカド") |
| 🌮 | "Taco" may not be recognized in Asia | Keep it — emojis teach new things too |
| 🧁 | "Cupcake" is an English loanword in many languages | Use the loanword if that's what parents say |
| 🍱 | "Bento" is Japanese — other languages? | "Lunch box" equivalent in each language |
| 🍋 | ms: the English loanword "lemon" was used at first | Changed to "limau", the everyday Malay citrus word (commit `0d1c2f4`). The everyday word beats the precise one. |

## Adding a New Language — Checklist
1. [ ] Identify the BCP 47 code, and check which tag the voices actually ship under
       (iOS `fil-PH` vs `tl-PH` was a real bug)
2. [ ] Add the code to `Language` in `src/types.ts` — this makes every missing
       translation a compile error
3. [ ] Add a `LanguageMeta` entry in `src/data/languages.ts`, including a
       `voicePrefixes` fallback chain and a comment explaining the chain's ordering
4. [ ] Translate all 200 words in `src/data/emojis.ts`
5. [ ] Translate all 84 words in `src/data/countables.ts`, deciding first whether the
       language bakes in a measure word or keeps the noun bare
6. [ ] Add `NUMBER_WORDS` 1–10, and a `buildPhrase()` branch in
       `src/components/CountMode.tsx` if the language needs one
7. [ ] Add the UI strings: the 8 category labels in `CATEGORIES` (`src/data/emojis.ts`),
       `UI_TEXT` in `src/components/CountMode.tsx`, and `PLACEHOLDER` in
       `src/components/TypingRow.tsx`. All three are `Record<Language, string>`, so the
       compiler will find them for you — but also grep for `lang ===`, which finds the
       hand-written conditionals it will not (the "Count to" indicator in `CountMode.tsx`
       is one, and it currently has no `ja` or `tl` branch)
8. [ ] Native speaker review
9. [ ] TTS pronunciation test — every word, on real iOS and Android hardware
10. [ ] Update `index.html` meta descriptions and the PWA manifest description in
        `vite.config.ts`, both of which name the languages

Only the child-facing surface is translated today. The parent-facing panels —
`AboutPanel.tsx`, `StatsPanel.tsx` and `ParentalGate.tsx` — are English-only. A toddler
never sees them, so localising them is optional rather than part of shipping a language.

### Not implemented (and not planned right now)
- **Spanish (`es`)** is not a supported language and never was. It appears in
  `src/data/languages.ts` only as the last-resort voice fallback for Tagalog, and in
  `docs/growth/FULL_GROWTH_PLAN.md` as a market hypothesis. Do not treat it as content.
- **Thai (`th`)** appears in `docs/content/EMOJI_DATABASE.md` as a future expansion idea.
  No data, no code.
- **Phonetic fields** (pinyin, romaji, tone marks) are not in the schema. The record is
  `{ emoji, cat, en, zh, ms, ja, tl }` and nothing more. Adding a romanization column is
  a plausible future step for parents who cannot read the script, but nothing reads such
  a field today.
- **Traditional Chinese**, per-region vocabulary variants, and in-app parental gating in
  each language are all unbuilt.
