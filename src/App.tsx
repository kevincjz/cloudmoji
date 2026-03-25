import { useState, useCallback, useRef, useEffect } from "react";
import type { Language } from "./types";
import { useLocalStorage } from "./hooks/useLocalStorage";
import { logEvent } from "./lib/measurement";
import { WordsMode } from "./components/WordsMode";
import { CountMode } from "./components/CountMode";
import { TabBar, type TabId } from "./components/TabBar";
import { StatsPanel } from "./components/StatsPanel";
import { AboutPanel } from "./components/AboutPanel";

export default function App() {
  const [lang, setLang] = useLocalStorage<Language>("cm_lang", "en");
  const [muted, setMuted] = useState(false);
  const [activeTab, setActiveTab] = useState<TabId>("words");
  const [showStats, setShowStats] = useState(false);
  const [showAbout, setShowAbout] = useState(false);

  const titleTapRef = useRef<number[]>([]);

  // Log session start
  useEffect(() => {
    logEvent("session_start");
  }, []);

  const handleLangToggle = useCallback(() => {
    const cycle: Language[] = ["en", "zh", "ms"];
    const next = cycle[(cycle.indexOf(lang) + 1) % cycle.length];
    setLang(next);
    logEvent("lang", next);
  }, [lang, setLang]);

  const handleMuteToggle = useCallback(() => {
    setMuted((m) => {
      if (!m) speechSynthesis.cancel();
      return !m;
    });
  }, []);

  // Parental gate: tap title 5 times rapidly to open stats
  const handleTitleTap = useCallback(() => {
    const now = Date.now();
    titleTapRef.current.push(now);
    titleTapRef.current = titleTapRef.current.filter((t) => now - t < 2000);
    if (titleTapRef.current.length >= 5) {
      titleTapRef.current = [];
      setShowStats(true);
    }
  }, []);

  const handleTabSelect = useCallback((tab: TabId) => {
    setActiveTab(tab);
    logEvent("tab", tab);
  }, []);

  return (
    <div
      className="flex flex-col relative"
      style={{
        minHeight: "100svh",
        height: "100svh",
        background:
          "linear-gradient(160deg, #0F0E2A 0%, #1A1145 40%, #0D2137 100%)",
        fontFamily: "'Nunito', sans-serif",
        overflow: "hidden",
        paddingTop: "env(safe-area-inset-top)",
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
        style={{ flex: 1, maxWidth: 500, overflow: "hidden" }}
      >
        {activeTab === "words" ? (
          <WordsMode
            lang={lang}
            muted={muted}
            onLangToggle={handleLangToggle}
            onMuteToggle={handleMuteToggle}
            onTitleTap={handleTitleTap}
            onAbout={() => setShowAbout(true)}
          />
        ) : (
          <CountMode
            lang={lang}
            muted={muted}
            onLangToggle={handleLangToggle}
            onMuteToggle={handleMuteToggle}
            onTitleTap={handleTitleTap}
            onAbout={() => setShowAbout(true)}
          />
        )}
      </div>

      {/* Tab Bar */}
      <TabBar activeTab={activeTab} onSelect={handleTabSelect} />

      {/* Hidden Stats Panel */}
      {showStats && <StatsPanel onClose={() => setShowStats(false)} />}

      {/* About Panel */}
      {showAbout && <AboutPanel onClose={() => setShowAbout(false)} />}
    </div>
  );
}
