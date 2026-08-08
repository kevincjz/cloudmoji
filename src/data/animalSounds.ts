import type { Language } from "../types";

/**
 * What each animal *says*, in each language.
 *
 * Not a translation table — an onomatopoeia table, and the difference is the
 * whole point. A dog says "woof" in English, 汪汪 in Chinese, ワンワン in Japanese
 * and "guk guk" in Malay, and a child who learns both the animal's name and the
 * noise it makes in two languages has learnt something the word lists cannot
 * teach. This is the same premise as the rest of the app applied to a new axis.
 *
 * It also removes a dependency: the alternative was fifteen CC0 recordings,
 * which meant sourcing, licensing and ear-approving third-party audio for a
 * kids app. Device text-to-speech already ships, already speaks five languages,
 * and already has a mute switch.
 *
 * **These need a native-speaker pass**, exactly as the word lists got. English
 * and Chinese are the ones I would stand behind; the Malay, Japanese and Tagalog
 * rows are researched rather than known, and are the obvious first thing to
 * check with someone who grew up with them.
 *
 * Written for a *speech synthesiser*, not for the page. "bzzz" is what a bee
 * says in print; a TTS voice reads it as a stutter, so the entries below are
 * spelled the way they need to be pronounced.
 *
 * Keyed by emoji glyph, which is unique across the catalogue — the generator's
 * parity check enforces that — and every key must exist in `EMOJIS` under the
 * `animals` category.
 */
export const ANIMAL_SOUNDS: Record<string, Record<Language, string>> = {
  "🐶": { en: "woof woof", zh: "汪汪", ms: "guk guk", ja: "ワンワン", tl: "aw aw" },
  "🐱": { en: "meow", zh: "喵喵", ms: "miau", ja: "ニャーニャー", tl: "ngiyaw" },
  "🐮": { en: "moo", zh: "哞哞", ms: "moo", ja: "モーモー", tl: "ungaa" },
  "🐄": { en: "moo", zh: "哞哞", ms: "moo", ja: "モーモー", tl: "ungaa" },
  "🐷": { en: "oink oink", zh: "哼哼", ms: "oink oink", ja: "ブーブー", tl: "oink oink" },
  "🐔": { en: "cluck cluck", zh: "咯咯", ms: "kokok", ja: "コッコッ", tl: "putak putak" },
  "🦆": { en: "quack quack", zh: "嘎嘎", ms: "kuek kuek", ja: "ガーガー", tl: "kwak kwak" },
  "🐑": { en: "baa baa", zh: "咩咩", ms: "mbek", ja: "メーメー", tl: "mee" },
  "🐴": { en: "neigh", zh: "咴咴", ms: "ringkik", ja: "ヒヒーン", tl: "halinghing" },
  "🐸": { en: "ribbit ribbit", zh: "呱呱", ms: "krok krok", ja: "ケロケロ", tl: "kokak" },
  "🐝": { en: "buzz buzz", zh: "嗡嗡", ms: "bzz bzz", ja: "ブーン", tl: "bzz bzz" },
  "🦉": { en: "hoot hoot", zh: "咕咕", ms: "hu hu", ja: "ホーホー", tl: "huhu" },
  "🦁": { en: "roar", zh: "吼", ms: "aum", ja: "ガオー", tl: "ngaw" },
  "🐯": { en: "roar", zh: "吼", ms: "aum", ja: "ガオー", tl: "ngaw" },
  "🐦": { en: "tweet tweet", zh: "啾啾", ms: "cit cit", ja: "ピヨピヨ", tl: "tsip tsip" },
  "🐭": { en: "squeak squeak", zh: "吱吱", ms: "ciit ciit", ja: "チューチュー", tl: "ngiik" },
  "🐍": { en: "hiss", zh: "嘶嘶", ms: "desis", ja: "シューシュー", tl: "sss" },
  "🐵": { en: "ooh ooh ah ah", zh: "吱吱", ms: "uk uk", ja: "ウキーウキー", tl: "uk uk" },
  "🐘": { en: "pawoo", zh: "呜呜", ms: "pawoo", ja: "パオーン", tl: "pawoo" },
  "🦜": { en: "squawk", zh: "呱呱", ms: "kuak", ja: "ギャーギャー", tl: "kwak" },
};
