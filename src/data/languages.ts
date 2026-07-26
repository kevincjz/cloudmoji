import type { Language } from "../types";

export interface LanguageMeta {
  id: Language;
  /** Shown on the toggle button — the language's own name, short form. */
  short: string;
  /** Shown in the picker list, in English, so a parent can find it. */
  name: string;
  /** BCP-47 code handed to speechSynthesis. */
  speech: string;
}

export const LANGUAGES: LanguageMeta[] = [
  { id: "en", short: "EN", name: "English", speech: "en-US" },
  { id: "zh", short: "中文", name: "Chinese", speech: "zh-CN" },
  { id: "ms", short: "BM", name: "Malay", speech: "ms-MY" },
  { id: "ja", short: "日本語", name: "Japanese", speech: "ja-JP" },
  // Tagalog speech is tagged fil-PH — that is the code iOS ships its Filipino
  // voice under. useTTS falls back on the "fil" / "tl" prefix if it is absent.
  { id: "tl", short: "TL", name: "Tagalog", speech: "fil-PH" },
];

export const SPEECH_LANG: Record<Language, string> = Object.fromEntries(
  LANGUAGES.map((l) => [l.id, l.speech]),
) as Record<Language, string>;

export function langMeta(id: Language): LanguageMeta {
  return LANGUAGES.find((l) => l.id === id) ?? LANGUAGES[0];
}
