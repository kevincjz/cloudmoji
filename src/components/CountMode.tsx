import { useState, useCallback, useEffect, useRef } from "react";
import type { MascotMood, Language } from "../types";
import { COUNTABLES, NUMBER_WORDS } from "../data/countables";
import type { Countable } from "../data/countables";
import { useTTS } from "../hooks/useTTS";
import { logEvent } from "../lib/measurement";
import { CloudMascot } from "./CloudMascot";
import { LangToggle } from "./LangToggle";

const SPEECH_LANG: Record<Language, string> = {
  en: "en-US",
  zh: "zh-CN",
  ms: "ms-MY",
};

interface CountModeProps {
  lang: Language;
  muted: boolean;
  onLangToggle: () => void;
  onMuteToggle: () => void;
  onTitleTap: () => void;
  onAbout: () => void;
}

export function CountMode({ lang, muted, onLangToggle, onMuteToggle, onTitleTap, onAbout }: CountModeProps) {
  const [count, setCount] = useState(3);
  const [currentItem, setCurrentItem] = useState<Countable | null>(null);
  const [tappedOrder, setTappedOrder] = useState<number[]>([]);
  const [lastTapped, setLastTapped] = useState<number | null>(null);
  const [mascotMood, setMascotMood] = useState<MascotMood>("happy");
  const [completed, setCompleted] = useState(false);
  const [showNumber, setShowNumber] = useState<{ phrase: string; id: number } | null>(null);

  const beamingRef = useRef(false);

  const safeMood = useCallback((m: MascotMood) => {
    if (beamingRef.current && m !== "beaming") return;
    setMascotMood(m);
  }, []);

  const { speak, initTTS } = useTTS({ muted, safeMood });

  const newRound = useCallback(() => {
    const item = COUNTABLES[Math.floor(Math.random() * COUNTABLES.length)];
    setCurrentItem(item);
    setTappedOrder([]);
    setLastTapped(null);
    setCompleted(false);
    setShowNumber(null);
    beamingRef.current = false;
    setMascotMood("happy");
  }, []);

  const nextRound = useCallback(() => {
    setCount((prev) => {
      if (prev < 9) return prev + 1;
      return 2 + Math.floor(Math.random() * 8);
    });
    newRound();
  }, [newRound]);

  useEffect(() => {
    newRound();
  }, [newRound]);

  const buildPhrase = useCallback(
    (item: Countable, num: number): string => {
      const numWord = NUMBER_WORDS[lang][num - 1];
      if (lang === "zh") {
        return `${numWord}${item.zh}`;
      }
      if (lang === "ms") {
        return `${numWord} ${item.ms}`;
      }
      // English: pluralize
      const noun = item.en;
      const plural = num > 1 ? (noun.endsWith("s") ? noun : noun + "s") : noun;
      return `${numWord} ${plural}`;
    },
    [lang],
  );

  const handleTap = useCallback(
    (index: number) => {
      if (completed || !currentItem) return;
      if (tappedOrder.includes(index)) return;

      initTTS();

      const newTapped = [...tappedOrder, index];
      setTappedOrder(newTapped);
      setLastTapped(index);

      const currentNumber = newTapped.length;
      const phrase = buildPhrase(currentItem, currentNumber);

      setShowNumber({ phrase, id: Date.now() });
      if (!beamingRef.current) setMascotMood("excited");
      speak(phrase, SPEECH_LANG[lang]);
      logEvent("count_tap", `${currentItem.emoji}:${currentNumber}`);

      setTimeout(() => safeMood("happy"), 600);

      if (currentNumber === count) {
        setCompleted(true);
        setTimeout(() => {
          beamingRef.current = true;
          setMascotMood("beaming");
          const completionPhrase = buildPhrase(currentItem, count) + "!";
          speak(completionPhrase, SPEECH_LANG[lang]);
          setTimeout(() => {
            beamingRef.current = false;
            setMascotMood("happy");
          }, 3500);
        }, 1200);
      }
    },
    [completed, currentItem, tappedOrder, count, lang, speak, initTTS, safeMood, buildPhrase],
  );

  if (!currentItem) return null;

  const getGridCols = (n: number) => {
    if (n <= 3) return 3;
    if (n <= 6) return 3;
    return Math.min(4, Math.ceil(Math.sqrt(n)));
  };

  const emojiSize = count <= 3 ? 64 : count <= 6 ? 54 : 46;
  const btnSize = count <= 3 ? 96 : count <= 6 ? 82 : 72;

  const subtitleText = lang === "zh" ? "数一数!" : lang === "ms" ? "Jom kira!" : "Let's count!";
  const shuffleText = lang === "zh" ? "换一换" : lang === "ms" ? "Tukar" : "Shuffle";
  const nextText = lang === "zh" ? "下一个!" : lang === "ms" ? "Seterusnya!" : "Next!";

  return (
    <>
      {/* Header */}
      <div
        className="flex items-center justify-between shrink-0"
        style={{ padding: "10px 14px 6px" }}
      >
        <div className="flex items-center gap-2">
          <CloudMascot mood={mascotMood} size={64} />
          <div onClick={onTitleTap} style={{ cursor: "default" }}>
            <div
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
              Cloudculator
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
              🔢 {subtitleText}
            </div>
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
          <LangToggle lang={lang} onToggle={onLangToggle} />
        </div>
      </div>

      {/* Progress dots + phrase */}
      <div
        className="shrink-0 flex flex-col items-center justify-center"
        style={{ padding: "4px 0 2px", minHeight: 48 }}
      >
        <div className="flex gap-[6px]" style={{ marginBottom: 6 }}>
          {Array.from({ length: count }).map((_, i) => (
            <div
              key={i}
              style={{
                width: 10,
                height: 10,
                borderRadius: "50%",
                background: i < tappedOrder.length ? "#4ECDC4" : "rgba(255,255,255,0.1)",
                border: i < tappedOrder.length ? "1.5px solid rgba(78,205,196,0.6)" : "1.5px solid rgba(255,255,255,0.06)",
                transition: "all 0.3s ease",
                transform: i < tappedOrder.length ? "scale(1.2)" : "scale(1)",
              }}
            />
          ))}
        </div>

        {showNumber && (
          <div key={showNumber.id} style={{ animation: "countPop 0.4s ease-out" }}>
            <span
              style={{
                background: "linear-gradient(135deg, rgba(255,107,107,0.2), rgba(78,205,196,0.2))",
                padding: "5px 18px",
                borderRadius: 18,
                fontWeight: 900,
                fontSize: 20,
                color: "#fff",
                border: "1.5px solid rgba(255,255,255,0.1)",
              }}
            >
              {showNumber.phrase}
            </span>
          </div>
        )}
      </div>

      {/* Emoji Counting Area */}
      <div
        className="flex-1 flex items-center justify-center"
        style={{ padding: "16px 20px" }}
      >
        <div
          className="grid w-full"
          style={{
            gridTemplateColumns: `repeat(${getGridCols(count)}, 1fr)`,
            gap: count <= 4 ? 16 : 12,
            maxWidth: 360,
            animation: "fadeIn 0.4s ease-out",
          }}
        >
          {Array.from({ length: count }).map((_, i) => {
            const isTapped = tappedOrder.includes(i);
            const isJustTapped = lastTapped === i && isTapped;

            return (
              <button
                key={i}
                className="active:scale-85"
                onClick={() => handleTap(i)}
                style={{
                  width: btnSize,
                  height: btnSize,
                  margin: "0 auto",
                  borderRadius: 22,
                  border: isTapped
                    ? "2.5px solid rgba(78,205,196,0.6)"
                    : "2.5px solid rgba(255,255,255,0.12)",
                  background: isTapped
                    ? "rgba(78,205,196,0.15)"
                    : "rgba(255,255,255,0.04)",
                  fontSize: emojiSize,
                  cursor: isTapped ? "default" : "pointer",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  transition: "all 0.25s ease",
                  animation: isJustTapped
                    ? "bounceEmoji 0.35s ease"
                    : !isTapped
                      ? "gentleFloat 2s ease-in-out infinite"
                      : "none",
                  animationDelay: !isTapped ? `${i * 0.15}s` : "0s",
                  opacity: isTapped ? 1 : 0.75,
                  transform: isTapped ? "scale(1)" : "scale(0.95)",
                  position: "relative",
                  padding: 0,
                  lineHeight: 1,
                }}
              >
                {currentItem.emoji}
                {isTapped && (
                  <div
                    style={{
                      position: "absolute",
                      top: -6,
                      right: -6,
                      width: 24,
                      height: 24,
                      borderRadius: "50%",
                      background: "linear-gradient(135deg, #4ECDC4, #44B8AC)",
                      color: "#fff",
                      fontSize: 13,
                      fontWeight: 900,
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      fontFamily: "'Nunito', sans-serif",
                      animation: "popIn 0.3s ease-out",
                      boxShadow: "0 2px 8px rgba(78,205,196,0.4)",
                    }}
                  >
                    {tappedOrder.indexOf(i) + 1}
                  </div>
                )}
              </button>
            );
          })}
        </div>
      </div>

      {/* Bottom Controls */}
      <div
        className="shrink-0 flex justify-center gap-3"
        style={{ padding: "12px 20px 8px" }}
      >
        <button
          className="active:scale-88"
          onClick={newRound}
          style={{
            background: "rgba(255,179,71,0.15)",
            border: "2px solid rgba(255,179,71,0.25)",
            borderRadius: 18,
            padding: "10px 20px",
            color: "#FFB347",
            fontSize: 14,
            fontWeight: 900,
            cursor: "pointer",
            fontFamily: "'Nunito', sans-serif",
            display: "flex",
            alignItems: "center",
            gap: 6,
            transition: "transform 0.1s",
          }}
        >
          🔄 {shuffleText}
        </button>

        {completed && (
          <button
            className="active:scale-88"
            onClick={nextRound}
            style={{
              background: "rgba(78,205,196,0.2)",
              border: "2px solid rgba(78,205,196,0.4)",
              borderRadius: 18,
              padding: "10px 24px",
              color: "#4ECDC4",
              fontSize: 14,
              fontWeight: 900,
              cursor: "pointer",
              fontFamily: "'Nunito', sans-serif",
              display: "flex",
              alignItems: "center",
              gap: 6,
              animation: "slideUp 0.4s ease-out, completePulse 1.5s ease-in-out infinite 0.4s",
            }}
          >
            ✨ {nextText}
          </button>
        )}
      </div>

      {/* Count indicator */}
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
        {lang === "zh" ? "数到" : lang === "ms" ? "Kira hingga" : "Count to"} {count}
      </div>
    </>
  );
}
