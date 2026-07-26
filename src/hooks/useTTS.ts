import { useCallback, useEffect, useRef } from "react";
import type { MascotMood, Language } from "../types";
import { pickVoice } from "../lib/voices";

interface UseTTSOptions {
  muted: boolean;
  /** Cancels any queued speech when this changes — a new language must not
   *  finish speaking the old one. */
  lang: Language;
  safeMood: (mood: MascotMood) => void;
}

export interface QueueItem {
  text: string;
  langCode: string;
  /** Runs just before this item is spoken, so the UI can follow the audio. */
  onSpeak?: () => void;
}

/** Rough upper bound for one short word, used as a stall watchdog. */
const UTTERANCE_WATCHDOG_MS = 6000;

export function useTTS({ muted, lang, safeMood }: UseTTSOptions) {
  const ttsInit = useRef(false);
  const voiceCache = useRef<Map<string, SpeechSynthesisVoice>>(new Map());
  /** Bumped on every cancel. Queued callbacks compare against it and bail out,
   *  which is what makes an in-flight sequence genuinely cancellable. */
  const genRef = useRef(0);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const mutedRef = useRef(muted);
  mutedRef.current = muted;

  // getVoices() is async on iOS Safari — it returns [] on the first call and
  // populates shortly after. Prime it so the first tap has the right voice.
  useEffect(() => {
    const prime = () => void speechSynthesis.getVoices();
    prime();
    speechSynthesis.addEventListener("voiceschanged", prime);
    return () => speechSynthesis.removeEventListener("voiceschanged", prime);
  }, []);

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
    const voice = pickVoice(speechSynthesis.getVoices(), langCode);
    if (voice) voiceCache.current.set(langCode, voice);
    return voice;
  }, []);

  /** Stop everything: the utterance being spoken, and anything still queued. */
  const cancelAll = useCallback(() => {
    genRef.current += 1;
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    try {
      speechSynthesis.cancel();
    } catch {
      // nothing to cancel
    }
  }, []);

  const buildUtterance = useCallback(
    (text: string, langCode: string) => {
      const u = new SpeechSynthesisUtterance(text);
      u.lang = langCode;
      u.rate = 0.85;
      u.pitch = 1.1;
      const voice = getVoice(langCode);
      if (voice) {
        u.voice = voice;
        // Keep lang consistent with the chosen voice, or some engines
        // re-resolve and ignore the explicit voice.
        u.lang = voice.lang;
      }
      return u;
    },
    [getVoice],
  );

  const speak = useCallback(
    (text: string, langCode: string) => {
      if (mutedRef.current) return;
      initTTS();
      cancelAll();
      const gen = genRef.current;
      try {
        const u = buildUtterance(text, langCode);
        u.onstart = () => {
          if (gen === genRef.current) safeMood("speaking");
        };
        u.onend = () => {
          if (gen === genRef.current) safeMood("happy");
        };
        u.onerror = () => {
          if (gen === genRef.current) safeMood("happy");
        };
        speechSynthesis.speak(u);
      } catch {
        // Swallow TTS errors — no failure states for toddlers
      }
    },
    [initTTS, cancelAll, buildUtterance, safeMood],
  );

  /**
   * Speak items one after another, chained on `onend` rather than on fixed
   * timeouts. A timeout-based queue keeps firing after mute, after a language
   * change and after the round is replaced, because each callback closes over
   * stale state and nothing can call it back.
   */
  const speakSequence = useCallback(
    (items: QueueItem[], gapMs = 350) => {
      if (mutedRef.current || items.length === 0) return;
      initTTS();
      cancelAll();
      const gen = genRef.current;
      let index = 0;

      const step = () => {
        if (gen !== genRef.current || mutedRef.current || index >= items.length) {
          if (gen === genRef.current) safeMood("happy");
          return;
        }
        const item = items[index++];
        item.onSpeak?.();

        let advanced = false;
        const advance = () => {
          if (advanced || gen !== genRef.current) return;
          advanced = true;
          timerRef.current = setTimeout(step, gapMs);
        };

        try {
          const u = buildUtterance(item.text, item.langCode);
          u.onstart = () => {
            if (gen === genRef.current) safeMood("speaking");
          };
          u.onend = advance;
          // A missing voice can fail silently; advancing on error keeps the
          // queue from stalling forever.
          u.onerror = advance;
          speechSynthesis.speak(u);
          // Watchdog: some engines never fire onend if the utterance is
          // dropped. Without this the rest of the sequence would never play.
          timerRef.current = setTimeout(advance, UTTERANCE_WATCHDOG_MS);
        } catch {
          advance();
        }
      };

      step();
    },
    [initTTS, cancelAll, buildUtterance, safeMood],
  );

  // Mute or a language switch must silence anything already queued.
  useEffect(() => {
    cancelAll();
  }, [muted, lang, cancelAll]);

  // Leaving the mode (tab switch unmounts it) must not leave audio running.
  useEffect(() => cancelAll, [cancelAll]);

  return { speak, speakSequence, cancelAll, initTTS };
}
