import type { Category, Language } from "../../src/types";

export interface IosLanguage {
  id: Language;
  short: string;
  name: string;
  speech: string;
  voicePrefixes: string[];
}

export interface IosCategory {
  id: string; // "all" or a Category
  icon: string;
  labels: Record<Language, string>;
}

export interface IosEmoji {
  emoji: string;
  cat: Category;
  en: string;
  zh: string;
  ms: string;
  ja: string;
  tl: string;
}

export interface IosCountable extends IosEmoji {
  /** Present only where the regular pluraliser is wrong (teeth, mice). */
  enPlural?: string;
}

export interface IosEmojiData {
  version: number;
  languages: IosLanguage[];
  categories: IosCategory[];
  emojis: IosEmoji[];
  countables: Omit<IosCountable, "cat">[];
  numberWords: Record<string, string[]>;
}
