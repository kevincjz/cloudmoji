import { useCallback, useEffect, useRef } from "react";
import type { MascotMood } from "../types";
import { pickVoice } from "../lib/voices";

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

  // getVoices() is async on iOS Safari — it returns [] on the first call and
  // populates shortly after. Prime it on mount so the very first tap already
  // has the right voice instead of falling back to the engine default.
  useEffect(() => {
    const prime = () => void speechSynthesis.getVoices();
    prime();
    speechSynthesis.addEventListener("voiceschanged", prime);
    return () => speechSynthesis.removeEventListener("voiceschanged", prime);
  }, []);

  const getVoice = useCallback((langCode: string): SpeechSynthesisVoice | undefined => {
    const cached = voiceCache.current.get(langCode);
    if (cached) return cached;

    const voice = pickVoice(speechSynthesis.getVoices(), langCode);
    if (voice) voiceCache.current.set(langCode, voice);
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
        if (voice) {
          u.voice = voice;
          // Keep lang consistent with the chosen voice. Leaving lang as the
          // requested code while voice is a fallback (fil-PH + a Malay voice)
          // makes some engines re-resolve and ignore the explicit voice.
          u.lang = voice.lang;
        }

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
