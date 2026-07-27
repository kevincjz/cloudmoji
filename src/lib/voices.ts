import { voicePrefixesForSpeech } from "../data/languages";

/** Structural subset of SpeechSynthesisVoice — keeps this unit-testable. */
export interface VoiceLike {
  lang: string;
  name: string;
}

const FEMALE_HINTS = [
  "female", "samantha", "karen", "tessa",
  "tingting", "sinji", "amira", "kyoko", "o-ren", "rosa",
];

/**
 * Pick the best available voice for a language.
 *
 * Walks the language's prefix chain in order and takes the first tier that has
 * any voice. That is what stops a device with no Filipino voice from falling
 * through to the engine's English default, which mispronounces Tagalog badly;
 * it lands on Malay instead, which shares Tagalog's vowels and its "ng".
 */
export function pickVoice<T extends VoiceLike>(
  voices: T[],
  langCode: string,
): T | undefined {
  let matching: T[] = [];
  for (const prefix of voicePrefixesForSpeech(langCode)) {
    // Exact tag match, or a "-"-delimited subtag boundary — never a bare
    // startsWith. "tlh" (Klingon's real IANA subtag) starts with the letters
    // "tl" but is not Tagalog; a bare startsWith would wrongly seat it in
    // Tagalog's tier before Malay/Indonesian ever got a look in. Mirrors
    // VoiceResolver.pick in ios/CloudmojiCore — same rule, two ports.
    matching = voices.filter((v) => v.lang === prefix || v.lang.startsWith(prefix + "-"));
    if (matching.length > 0) break;
  }
  if (matching.length === 0) return undefined;

  // Prefer: exact lang match > female-sounding name > first match
  const exact = matching.filter((v) => v.lang === langCode);
  const pool = exact.length > 0 ? exact : matching;

  const female = pool.find((v) => {
    const name = v.name.toLowerCase();
    return FEMALE_HINTS.some((h) => name.includes(h));
  });

  return female ?? pool[0];
}
