export type Category = "fruits" | "food" | "animals" | "vehicles" | "nature" | "objects" | "people" | "faces";

export type Language = "en" | "zh" | "ms" | "ja" | "tl";

export interface EmojiEntry {
  emoji: string;
  cat: Category;
  en: string;
  zh: string;
  ms: string;
  ja: string;
  tl: string;
}

export interface CategoryTab {
  id: "all" | Category;
  icon: string;
  labels: Record<Language, string>;
}

export type MascotMood = "happy" | "excited" | "speaking" | "beaming";

export interface TypedEmoji {
  emoji: string;
  word: string;
  id: number;
}
