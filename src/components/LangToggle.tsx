import { useState, useRef, useEffect } from "react";
import type { Language } from "../types";
import { LANGUAGES, langMeta } from "../data/languages";

interface LangToggleProps {
  lang: Language;
  onSelect: (lang: Language) => void;
}

export function LangToggle({ lang, onSelect }: LangToggleProps) {
  const [open, setOpen] = useState(false);
  const wrapRef = useRef<HTMLDivElement>(null);

  // Close when tapping anywhere else — toddlers tap everywhere.
  useEffect(() => {
    if (!open) return;
    const onDown = (e: PointerEvent) => {
      if (!wrapRef.current?.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener("pointerdown", onDown);
    return () => document.removeEventListener("pointerdown", onDown);
  }, [open]);

  return (
    <div ref={wrapRef} style={{ position: "relative" }}>
      <button
        data-testid="lang-toggle"
        onClick={() => setOpen((o) => !o)}
        className="active:scale-88"
        style={{
          background: open ? "rgba(78,205,196,0.18)" : "rgba(255,255,255,0.06)",
          border: open
            ? "2px solid rgba(78,205,196,0.4)"
            : "2px solid rgba(255,255,255,0.12)",
          borderRadius: 14,
          padding: "6px 10px",
          color: "#fff",
          fontSize: 14,
          fontWeight: 900,
          cursor: "pointer",
          display: "flex",
          alignItems: "center",
          gap: 4,
          fontFamily: "'Nunito', sans-serif",
          transition: "all 0.2s",
        }}
      >
        <span>{langMeta(lang).short}</span>
        <span
          style={{
            fontSize: 9,
            opacity: 0.6,
            transform: open ? "rotate(180deg)" : "none",
            transition: "transform 0.2s",
          }}
        >
          ▼
        </span>
      </button>

      {open && (
        <div
          data-testid="lang-menu"
          style={{
            position: "absolute",
            top: "calc(100% + 6px)",
            right: 0,
            zIndex: 60,
            minWidth: 172,
            background: "rgba(20,18,52,0.98)",
            border: "1.5px solid rgba(255,255,255,0.12)",
            borderRadius: 16,
            padding: 5,
            boxShadow: "0 12px 32px rgba(0,0,0,0.5)",
            backdropFilter: "blur(12px)",
            animation: "popIn 0.16s ease-out",
          }}
        >
          {LANGUAGES.map((l) => {
            const isActive = l.id === lang;
            return (
              <button
                key={l.id}
                data-testid={`lang-${l.id}`}
                onClick={() => {
                  onSelect(l.id);
                  setOpen(false);
                }}
                className="active:scale-95"
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 8,
                  width: "100%",
                  minHeight: 44,
                  padding: "0 10px",
                  background: isActive ? "rgba(78,205,196,0.16)" : "none",
                  border: "none",
                  borderRadius: 12,
                  color: isActive ? "#4ECDC4" : "rgba(255,255,255,0.75)",
                  fontSize: 15,
                  fontWeight: 800,
                  fontFamily: "'Nunito', sans-serif",
                  cursor: "pointer",
                  textAlign: "left",
                  transition: "background 0.15s",
                }}
              >
                <span style={{ width: 14, fontSize: 13 }}>{isActive ? "✓" : ""}</span>
                <span style={{ minWidth: 46, fontWeight: 900 }}>{l.short}</span>
                <span style={{ fontSize: 13, opacity: 0.65, fontWeight: 700 }}>
                  {l.name}
                </span>
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}
