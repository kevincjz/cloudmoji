import { useState, useCallback, useRef } from "react";
import type { EmojiEntry, MascotMood, TypedEmoji, Language, Category } from "../types";
import { EMOJIS } from "../data/emojis";
import { SPEECH_LANG } from "../data/languages";
import { useTTS } from "../hooks/useTTS";
import { logEvent } from "../lib/measurement";
import { CloudMascot } from "./CloudMascot";
import { EmojiGrid } from "./EmojiGrid";
import { TypingRow } from "./TypingRow";
import { WordBubble } from "./WordBubble";
import { CategoryBar } from "./CategoryBar";
import { LangToggle } from "./LangToggle";

/** PRD: at most 50 emojis in the typing row, oldest dropped first. */
const MAX_TYPED = 50;

function getWord(item: EmojiEntry, lang: Language): string {
  return item[lang];
}

interface WordsModeProps {
  lang: Language;
  muted: boolean;
  compact: boolean;
  onLangSelect: (lang: Language) => void;
  onMuteToggle: () => void;
  onTitleTap: () => void;
  onAbout: () => void;
}

export function WordsMode({ lang, muted, compact, onLangSelect, onMuteToggle, onTitleTap, onAbout }: WordsModeProps) {
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

  const wordTimerRef = useRef<ReturnType<typeof setTimeout>>(null);
  const beamingRef = useRef(false);

  const safeMood = useCallback((m: MascotMood) => {
    if (beamingRef.current && m !== "beaming") return;
    setMascotMood(m);
  }, []);

  const { speak, speakSequence, cancelAll, initTTS } = useTTS({ muted, lang, safeMood });

  const handleTap = useCallback(
    (item: EmojiEntry, idx: number) => {
      initTTS();
      const word = getWord(item, lang);

      setTyped((prev) =>
        [...prev, { emoji: item.emoji, word, id: Date.now() }].slice(-MAX_TYPED),
      );
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

  const handleCategorySelect = useCallback((cat: "all" | Category, label: string, icon: string) => {
    setCategory(cat);
    logEvent("cat", cat);

    setShowWord({ emoji: icon, word: label, id: Date.now() });
    speak(label, SPEECH_LANG[lang]);
    if (!beamingRef.current) setMascotMood("excited");

    if (wordTimerRef.current) clearTimeout(wordTimerRef.current);
    wordTimerRef.current = setTimeout(() => setShowWord(null), 2200);
    setTimeout(() => safeMood("happy"), 600);
  }, [lang, speak, safeMood]);

  const replayAll = useCallback(() => {
    if (typed.length === 0 || muted) return;
    logEvent("replay", String(typed.length));
    speakSequence(
      typed.map((item) => {
        const found = EMOJIS.find((e) => e.emoji === item.emoji);
        const word = found ? getWord(found, lang) : item.word;
        return {
          text: word,
          langCode: SPEECH_LANG[lang],
          onSpeak: () => setShowWord({ emoji: item.emoji, word, id: Date.now() }),
        };
      }),
    );
  }, [typed, lang, speakSequence, muted]);

  const handleClear = useCallback(() => {
    cancelAll();
    setShowWord(null);
    setTyped([]);
    logEvent("clear");
  }, [cancelAll]);

  const filtered =
    category === "all"
      ? EMOJIS
      : EMOJIS.filter((e) => e.cat === category);

  return (
    <>
      {/* Header */}
      <div
        className="flex items-center justify-between shrink-0"
        style={{ padding: compact ? "4px 12px 2px" : "10px 14px 6px" }}
      >
        <div className="flex items-center gap-2">
          <CloudMascot mood={mascotMood} size={compact ? 42 : 64} />
          <div onClick={onTitleTap} style={{ cursor: "default" }}>
            <div
              data-testid="app-title"
              style={{
                fontFamily: "'Lilita One', sans-serif",
                fontSize: compact ? 17 : 21,
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
            {!compact && (
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
            )}
          </div>
        </div>

        <div className="flex gap-[6px] items-center">
          <button
            data-testid="about-btn"
            onClick={onAbout}
            className="active:scale-88"
            style={{
              background: "rgba(255,255,255,0.06)",
              border: "2px solid rgba(255,255,255,0.12)",
              borderRadius: 14,
              padding: "6px 12px",
              color: "#fff",
              fontSize: 14,
              fontWeight: 900,
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              fontFamily: "'Nunito', sans-serif",
              transition: "all 0.2s",
            }}
          >
            About
          </button>
          <button
            data-testid="mute-btn"
            onClick={onMuteToggle}
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

          <LangToggle lang={lang} onSelect={onLangSelect} />
        </div>
      </div>

      {/* Typing Row */}
      <TypingRow
        typed={typed}
        lang={lang}
        muted={muted}
        onReplayAll={replayAll}
        onDeleteLast={() => {
          cancelAll();
          setTyped((p) => p.slice(0, -1));
        }}
        onClear={handleClear}
        onTapTyped={handleTapTyped}
      />

      {/* Word Bubble — in compact (landscape) it floats over the grid instead of
          reserving a row, which is 38px the grid badly needs when the phone is sideways. */}
      <div
        className="flex items-center justify-center shrink-0"
        style={
          compact
            ? {
                position: "absolute",
                left: 0,
                right: 0,
                bottom: 6,
                height: 38,
                zIndex: 20,
                pointerEvents: "none",
              }
            : { height: 38 }
        }
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
          bottom: 72,
          right: 12,
          fontSize: 10,
          fontWeight: 800,
          color: "rgba(255,255,255,0.12)",
          fontFamily: "'Nunito', sans-serif",
        }}
      >
        {tapCount > 0 ? `${tapCount} ✨` : ""}
      </div>
    </>
  );
}
