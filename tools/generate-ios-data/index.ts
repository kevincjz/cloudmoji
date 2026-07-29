import { EMOJIS, CATEGORIES } from "../../src/data/emojis";
import { COUNTABLES, NUMBER_WORDS } from "../../src/data/countables";
import { LANGUAGES } from "../../src/data/languages";
import { ANIMAL_SOUNDS } from "../../src/data/animalSounds";
import type { IosEmojiData } from "./schema";

export function build(): IosEmojiData {
  return {
    version: 1,
    languages: LANGUAGES.map((l) => ({
      id: l.id,
      short: l.short,
      name: l.name,
      speech: l.speech,
      voicePrefixes: l.voicePrefixes,
    })),
    categories: CATEGORIES.map((c) => ({
      id: c.id,
      icon: c.icon,
      labels: { ...c.labels },
    })),
    emojis: EMOJIS.map((e) => ({
      emoji: e.emoji,
      cat: e.cat,
      en: e.en,
      zh: e.zh,
      ms: e.ms,
      ja: e.ja,
      tl: e.tl,
    })),
    countables: COUNTABLES.map((c) => ({
      emoji: c.emoji,
      en: c.en,
      ...(c.enPlural ? { enPlural: c.enPlural } : {}),
      zh: c.zh,
      ms: c.ms,
      ja: c.ja,
      tl: c.tl,
    })),
    numberWords: { ...NUMBER_WORDS },
    animalSounds: { ...ANIMAL_SOUNDS },
  };
}

/** Stable formatting so the committed file only changes when the data does. */
export function serialise(data: IosEmojiData): string {
  return JSON.stringify(data, null, 2) + "\n";
}
