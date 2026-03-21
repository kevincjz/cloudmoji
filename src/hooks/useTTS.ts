import { useCallback, useRef } from "react";
import type { MascotMood } from "../types";

interface UseTTSOptions {
  muted: boolean;
  safeMood: (mood: MascotMood) => void;
}

export function useTTS({ muted, safeMood }: UseTTSOptions) {
  const ttsInit = useRef(false);
  const voiceCache = useRef<Map<string, SpeechSynthesisVoice>>(new Map());

  const initTTS = useCallback(() => {
    if (ttsInit.current) return;
    try {
      const s = new SpeechSynthesisUtterance("");
      s.volume = 0;
      speechSynthesis.speak(s);
      ttsInit.current = true;
    } catch {
      // iOS may block — will retry on next interaction
    }
  }, []);

  const getVoice = useCallback((langCode: string): SpeechSynthesisVoice | undefined => {
    const cached = voiceCache.current.get(langCode);
    if (cached) return cached;

    const voices = speechSynthesis.getVoices();
    const prefix = langCode.split("-")[0];
    const matching = voices.filter((v) => v.lang.startsWith(prefix));
    if (matching.length === 0) return undefined;

    // Prefer: exact lang match > female-sounding name > first match
    const exact = matching.filter((v) => v.lang === langCode);
    const pool = exact.length > 0 ? exact : matching;

    const female = pool.find((v) => {
      const name = v.name.toLowerCase();
      return name.includes("female") || name.includes("samantha") ||
        name.includes("karen") || name.includes("tessa") ||
        name.includes("tingting") || name.includes("sinji");
    });

    const voice = female ?? pool[0];
    voiceCache.current.set(langCode, voice);
    return voice;
  }, []);

  const speak = useCallback(
    (text: string, langCode: string) => {
      if (muted) return;
      initTTS();
      try {
        speechSynthesis.cancel();
        const u = new SpeechSynthesisUtterance(text);
        u.lang = langCode;
        u.rate = 0.85;
        u.pitch = 1.1;

        const voice = getVoice(langCode);
        if (voice) u.voice = voice;

        u.onstart = () => safeMood("speaking");
        u.onend = () => safeMood("happy");
        u.onerror = () => safeMood("happy");

        speechSynthesis.speak(u);
      } catch {
        // Swallow TTS errors — no failure states for toddlers
      }
    },
    [initTTS, getVoice, muted, safeMood],
  );

  return { speak, initTTS };
}
