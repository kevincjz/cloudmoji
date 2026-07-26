import type { Language } from "../types";

export interface LanguageMeta {
  id: Language;
  /** Shown on the toggle button — the language's own name, short form. */
  short: string;
  /** Shown in the picker list, in English, so a parent can find it. */
  name: string;
  /** BCP-47 code handed to speechSynthesis. */
  speech: string;
  /**
   * Voice-language prefixes to try, in order, when picking a voice.
   * Most devices ship no Filipino voice at all, and the engine's own fallback
   * is whatever its default is — usually English, which mispronounces Tagalog
   * badly ("saging" as SAY-jing). Tagalog and Malay are both Austronesian:
   * same five pure vowels, same Latin orthography, and both write /ŋ/ as "ng",
   * which Tagalog's number linker uses constantly (isang, tatlong, ngipin).
   * Spanish handles the Spanish-derived loanwords but mangles "ng", so it sits
   * last. Same idea for Malay falling back to Indonesian.
   */
  voicePrefixes: string[];
}

export const LANGUAGES: LanguageMeta[] = [
  { id: "en", short: "EN", name: "English", speech: "en-US", voicePrefixes: ["en"] },
  { id: "zh", short: "中文", name: "Chinese", speech: "zh-CN", voicePrefixes: ["zh"] },
  { id: "ms", short: "BM", name: "Malay", speech: "ms-MY", voicePrefixes: ["ms", "id"] },
  { id: "ja", short: "日本語", name: "Japanese", speech: "ja-JP", voicePrefixes: ["ja"] },
  // iOS tags its Filipino voice fil-PH; other engines use tl-PH. Both first.
  {
    id: "tl",
    short: "TL",
    name: "Tagalog",
    speech: "fil-PH",
    voicePrefixes: ["fil", "tl", "ms", "id", "es"],
  },
];

export const VOICE_PREFIXES: Record<Language, string[]> = Object.fromEntries(
  LANGUAGES.map((l) => [l.id, l.voicePrefixes]),
) as Record<Language, string[]>;

/** Look up the prefix chain by the BCP-47 code the app speaks with. */
export function voicePrefixesForSpeech(speech: string): string[] {
  return LANGUAGES.find((l) => l.speech === speech)?.voicePrefixes ?? [speech.split("-")[0]];
}

export const SPEECH_LANG: Record<Language, string> = Object.fromEntries(
  LANGUAGES.map((l) => [l.id, l.speech]),
) as Record<Language, string>;

export function langMeta(id: Language): LanguageMeta {
  return LANGUAGES.find((l) => l.id === id) ?? LANGUAGES[0];
}

/** Runtime guard for values coming out of localStorage or a URL. */
export function isLanguage(v: unknown): v is Language {
  return typeof v === "string" && LANGUAGES.some((l) => l.id === v);
}
