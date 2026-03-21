# Emoji Data Model

## Schema

All emoji data lives in a single TypeScript file: `src/data/emojis.ts`

```typescript
// Types
type Category = "fruits" | "food" | "animals" | "vehicles" | "nature" | "objects" | "people" | "faces";

interface EmojiEntry {
  emoji: string;      // Unicode emoji character
  category: Category;
  en: string;         // English word (colloquial, what a parent would say)
  zh: string;         // Simplified Chinese (standard Mandarin)
}

// Data
export const EMOJIS: EmojiEntry[] = [
  { emoji: "🍎", category: "fruits", en: "apple", zh: "苹果" },
  { emoji: "🐶", category: "animals", en: "dog", zh: "狗" },
  // ... 120+ entries
];

export const CATEGORIES: { id: Category; label: string; labelZh: string; icon: string }[] = [
  { id: "fruits", label: "Fruits", labelZh: "水果", icon: "🍎" },
  { id: "food", label: "Food", labelZh: "食物", icon: "🍕" },
  { id: "animals", label: "Animals", labelZh: "动物", icon: "🐶" },
  { id: "vehicles", label: "Vehicles", labelZh: "交通", icon: "🚗" },
  { id: "nature", label: "Nature", labelZh: "自然", icon: "🌈" },
  { id: "objects", label: "Things", labelZh: "物品", icon: "🎈" },
  { id: "people", label: "People", labelZh: "人物", icon: "👶" },
  { id: "faces", label: "Faces", labelZh: "表情", icon: "😀" },
];
```

## Why This Simple
- No `id` field — the emoji character itself is the unique key
- No `tier` field — no monetization in MVP
- No `phonetic` field — not needed for TTS
- No `tts_override` field — add only if specific words sound wrong
- Two languages only — `en` and `zh`, flat strings, no nested objects

If we add more languages later, the schema evolves to:
```typescript
interface EmojiEntry {
  emoji: string;
  category: Category;
  words: { [lang: string]: string };
}
```
But don't build this until we actually need a third language.

## Curation Rules
See docs/content/EMOJI_DATABASE.md for inclusion/exclusion criteria and the full curation philosophy. The short version:

- ✅ Things a toddler can point at in real life
- ✅ Use the word a parent would naturally say
- ❌ No weapons, alcohol, violence, abstract concepts, religious/political symbols
