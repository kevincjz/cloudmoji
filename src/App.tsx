import { useState, useCallback, useRef, useEffect } from "react";
import type { EmojiEntry, MascotMood, TypedEmoji, Language, Category } from "./types";
import { EMOJIS } from "./data/emojis";
import { useLocalStorage } from "./hooks/useLocalStorage";
import { useTTS } from "./hooks/useTTS";
import { logEvent } from "./lib/measurement";
import { CloudMascot } from "./components/CloudMascot";
import { EmojiGrid } from "./components/EmojiGrid";
import { TypingRow } from "./components/TypingRow";
import { WordBubble } from "./components/WordBubble";
import { CategoryBar } from "./components/CategoryBar";
import { LangToggle } from "./components/LangToggle";
import { StatsPanel } from "./components/StatsPanel";

const SPEECH_LANG: Record<Language, string> = {
  en: "en-US",
  zh: "zh-CN",
  ms: "ms-MY",
};

function getWord(item: EmojiEntry, lang: Language): string {
  return item[lang];
}

export default function App() {
  const [lang, setLang] = useLocalStorage<Language>("cm_lang", "en");
  const [muted, setMuted] = useState(false);
  const [category, setCategory] = useState<"all" | Category>("all");
  const [typed, setTyped] = useState<TypedEmoji[]>([]);
  const [showWord, setShowWord] = useState<{
    emoji: string;
    word: string;
    id: number;
  } | null>(null);
  const [bounceIdx, setBounceIdx] = useState<number | null>(null);
  const [mascotMood, setMascotMood] = useState<MascotMood>("happy");
  const [tapCount, setTapCount] = useState(0);
  const [showStats, setShowStats] = useState(false);

  const wordTimerRef = useRef<ReturnType<typeof setTimeout>>(null);
  const beamingRef = useRef(false);
  const titleTapRef = useRef<number[]>([]);

  // Log session start
  useEffect(() => {
    logEvent("session_start");
  }, []);

  const safeMood = useCallback((m: MascotMood) => {
    if (beamingRef.current && m !== "beaming") return;
    setMascotMood(m);
  }, []);

  const { speak, initTTS } = useTTS({ muted, safeMood });

  // Parental gate: tap title 5 times rapidly to open stats
  const handleTitleTap = useCallback(() => {
    const now = Date.now();
    titleTapRef.current.push(now);
    // Keep only taps within last 2 seconds
    titleTapRef.current = titleTapRef.current.filter((t) => now - t < 2000);
    if (titleTapRef.current.length >= 5) {
      titleTapRef.current = [];
      setShowStats(true);
    }
  }, []);

  const handleTap = useCallback(
    (item: EmojiEntry, idx: number) => {
      initTTS();
      const word = getWord(item, lang);

      setTyped((prev) => [...prev, { emoji: item.emoji, word, id: Date.now() }]);
      setShowWord({ emoji: item.emoji, word, id: Date.now() });
      setBounceIdx(idx);
      if (!beamingRef.current) setMascotMood("excited");

      speak(word, SPEECH_LANG[lang]);
      logEvent("tap", item.emoji);

      const newCount = tapCount + 1;
      setTapCount(newCount);

      if ([10, 25, 50, 100].includes(newCount)) {
        setTimeout(() => {
          beamingRef.current = true;
          setMascotMood("beaming");
          setTimeout(() => {
            beamingRef.current = false;
            setMascotMood("happy");
          }, 3000);
        }, 500);
      }

      if (wordTimerRef.current) clearTimeout(wordTimerRef.current);
      wordTimerRef.current = setTimeout(() => setShowWord(null), 2200);
      setTimeout(() => setBounceIdx(null), 400);
      setTimeout(() => safeMood("happy"), 600);
    },
    [lang, speak, tapCount, initTTS, safeMood],
  );

  const handleTapTyped = useCallback(
    (emoji: string) => {
      const found = EMOJIS.find((e) => e.emoji === emoji);
      if (!found) return;
      const word = getWord(found, lang);
      setShowWord({ emoji, word, id: Date.now() });
      speak(word, SPEECH_LANG[lang]);
      if (wordTimerRef.current) clearTimeout(wordTimerRef.current);
      wordTimerRef.current = setTimeout(() => setShowWord(null), 2200);
    },
    [lang, speak],
  );

  const handleLangToggle = useCallback(() => {
    const cycle: Language[] = ["en", "zh", "ms"];
    const next = cycle[(cycle.indexOf(lang) + 1) % cycle.length];
    setLang(next);
    logEvent("lang", next);
  }, [lang, setLang]);

  const handleCategorySelect = useCallback((cat: "all" | Category) => {
    setCategory(cat);
    logEvent("cat", cat);
  }, []);

  const replayAll = useCallback(() => {
    if (typed.length === 0 || muted) return;
    speechSynthesis.cancel();
    logEvent("replay", String(typed.length));
    typed.forEach((item, i) => {
      setTimeout(() => {
        const found = EMOJIS.find((e) => e.emoji === item.emoji);
        const word = found ? getWord(found, lang) : item.word;
        setShowWord({ emoji: item.emoji, word, id: Date.now() });
        speak(word, SPEECH_LANG[lang]);
      }, i * 1200);
    });
    setTimeout(() => setShowWord(null), typed.length * 1200 + 1500);
  }, [typed, lang, speak, muted]);

  const handleClear = useCallback(() => {
    setTyped([]);
    logEvent("clear");
  }, []);

  const filtered =
    category === "all"
      ? EMOJIS
      : EMOJIS.filter((e) => e.cat === category);

  return (
    <div
      className="flex flex-col relative"
      style={{
        minHeight: "100vh",
        height: "100dvh",
        background:
          "linear-gradient(160deg, #0F0E2A 0%, #1A1145 40%, #0D2137 100%)",
        fontFamily: "'Nunito', sans-serif",
        overflow: "hidden",
        paddingTop: "env(safe-area-inset-top)",
        paddingBottom: "env(safe-area-inset-bottom)",
        paddingLeft: "env(safe-area-inset-left)",
        paddingRight: "env(safe-area-inset-right)",
      }}
    >
      {/* Background glow orbs */}
      <div className="fixed inset-0 pointer-events-none z-0 overflow-hidden">
        <div
          className="absolute rounded-full"
          style={{
            width: 300,
            height: 300,
            background:
              "radial-gradient(circle, rgba(255,107,107,0.08) 0%, transparent 70%)",
            top: "-10%",
            right: "-10%",
            animation: "bgGlow 6s ease-in-out infinite",
          }}
        />
        <div
          className="absolute rounded-full"
          style={{
            width: 250,
            height: 250,
            background:
              "radial-gradient(circle, rgba(78,205,196,0.08) 0%, transparent 70%)",
            bottom: "10%",
            left: "-8%",
            animation: "bgGlow 8s ease-in-out infinite 2s",
          }}
        />
        <div
          className="absolute rounded-full"
          style={{
            width: 200,
            height: 200,
            background:
              "radial-gradient(circle, rgba(255,230,109,0.06) 0%, transparent 70%)",
            top: "40%",
            right: "5%",
            animation: "bgGlow 7s ease-in-out infinite 1s",
          }}
        />
      </div>

      {/* Main content */}
      <div
        className="relative z-1 flex flex-col w-full mx-auto"
        style={{ height: "100dvh", maxWidth: 500, overflow: "hidden" }}
      >
        {/* Header */}
        <div
          className="flex items-center justify-between shrink-0"
          style={{ padding: "10px 14px 6px" }}
        >
          <div className="flex items-center gap-2">
            <CloudMascot mood={mascotMood} size={64} />
            <div onClick={handleTitleTap} style={{ cursor: "default" }}>
              <div
                data-testid="app-title"
                style={{
                  fontFamily: "'Lilita One', sans-serif",
                  fontSize: 21,
                  background:
                    "linear-gradient(135deg, #4ECDC4, #56B4D3, #7B93DB)",
                  WebkitBackgroundClip: "text",
                  WebkitTextFillColor: "transparent",
                  lineHeight: 1.1,
                  letterSpacing: 0.5,
                }}
              >
                Cloudmoji
              </div>
              <div
                style={{
                  fontSize: 10,
                  fontWeight: 800,
                  color: "rgba(255,255,255,0.3)",
                  letterSpacing: 0.5,
                  marginTop: 1,
                }}
              >
                Tap. Listen. Learn!
              </div>
            </div>
          </div>

          <div className="flex gap-[6px] items-center">
            <button
              data-testid="mute-btn"
              onClick={() =>
                setMuted((m) => {
                  if (!m) speechSynthesis.cancel();
                  return !m;
                })
              }
              className="active:scale-88"
              style={{
                background: muted
                  ? "rgba(255,107,107,0.2)"
                  : "rgba(255,255,255,0.06)",
                border: muted
                  ? "2px solid rgba(255,107,107,0.3)"
                  : "2px solid rgba(255,255,255,0.12)",
                borderRadius: 14,
                width: 40,
                height: 40,
                fontSize: 18,
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                transition: "all 0.2s",
              }}
            >
              {muted ? "🔇" : "🔊"}
            </button>

            <LangToggle lang={lang} onToggle={handleLangToggle} />
          </div>
        </div>

        {/* Typing Row */}
        <TypingRow
          typed={typed}
          lang={lang}
          muted={muted}
          onReplayAll={replayAll}
          onDeleteLast={() => setTyped((p) => p.slice(0, -1))}
          onClear={handleClear}
          onTapTyped={handleTapTyped}
        />

        {/* Word Bubble */}
        <div
          className="flex items-center justify-center shrink-0"
          style={{ height: 38 }}
        >
          {showWord && (
            <WordBubble
              emoji={showWord.emoji}
              word={showWord.word}
              id={showWord.id}
            />
          )}
        </div>

        {/* Category Bar */}
        <CategoryBar
          category={category}
          lang={lang}
          onSelect={handleCategorySelect}
        />

        {/* Emoji Grid */}
        <EmojiGrid
          emojis={filtered}
          bounceIdx={bounceIdx}
          onTap={handleTap}
        />

        {/* Tap counter */}
        <div
          className="fixed z-10"
          style={{
            bottom: 8,
            right: 12,
            fontSize: 10,
            fontWeight: 800,
            color: "rgba(255,255,255,0.12)",
            fontFamily: "'Nunito', sans-serif",
          }}
        >
          {tapCount > 0 ? `${tapCount} ✨` : ""}
        </div>
      </div>

      {/* Hidden Stats Panel */}
      {showStats && <StatsPanel onClose={() => setShowStats(false)} />}
    </div>
  );
}
