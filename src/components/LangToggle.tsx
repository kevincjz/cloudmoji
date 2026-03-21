import type { Language } from "../types";

interface LangToggleProps {
  lang: Language;
  onToggle: () => void;
}

export function LangToggle({ lang, onToggle }: LangToggleProps) {
  return (
    <button
      data-testid="lang-toggle"
      onClick={onToggle}
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
        gap: 5,
        fontFamily: "'Nunito', sans-serif",
        transition: "all 0.2s",
      }}
    >
      <span>{lang === "en" ? "EN" : lang === "zh" ? "中文" : "BM"}</span>
    </button>
  );
}
