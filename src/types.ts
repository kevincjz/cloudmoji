export type Category = "fruits" | "food" | "animals" | "vehicles" | "nature" | "objects" | "people" | "faces";

export interface EmojiEntry {
  emoji: string;
  cat: Category;
  en: string;
  zh: string;
}

export interface CategoryTab {
  id: "all" | Category;
  icon: string;
  label: string;
  labelZh: string;
}

export type Language = "en" | "zh";

export type MascotMood = "happy" | "excited" | "speaking" | "beaming";

export interface TypedEmoji {
  emoji: string;
  word: string;
  id: number;
}
