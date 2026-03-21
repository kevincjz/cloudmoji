import { useEffect, useRef } from "react";
import type { TypedEmoji, Language } from "../types";


interface TypingRowProps {
  typed: TypedEmoji[];
  lang: Language;
  muted: boolean;
  onReplayAll: () => void;
  onDeleteLast: () => void;
  onClear: () => void;
  onTapTyped: (emoji: string) => void;
}

export function TypingRow({
  typed,
  lang,
  muted,
  onReplayAll,
  onDeleteLast,
  onClear,
  onTapTyped,
}: TypingRowProps) {
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollLeft = scrollRef.current.scrollWidth;
    }
  }, [typed]);

  return (
    <div
      data-testid="typing-row"
      className="shrink-0"
      style={{
        margin: "4px 12px 0",
        background: "rgba(255,255,255,0.04)",
        borderRadius: 20,
        padding: "7px 10px",
        minHeight: 56,
        display: "flex",
        alignItems: "center",
        border: "1.5px solid rgba(255,255,255,0.06)",
        gap: 4,
      }}
    >
      <div
        ref={scrollRef}
        className="no-scroll flex flex-1 gap-[3px] items-center overflow-x-auto"
        style={{ scrollBehavior: "smooth" }}
      >
        {typed.length === 0 ? (
          <span
            style={{
              color: "rgba(255,255,255,0.2)",
              fontSize: 13,
              fontWeight: 800,
              padding: "0 4px",
            }}
          >
            {lang === "zh" ? "点击下面的表情 👇" : lang === "ms" ? "Ketik emoji di bawah! 👇" : "Tap emojis below! 👇"}
          </span>
        ) : (
          typed.map((item) => (
            <span
              key={item.id}
              data-testid="typed-emoji"
              style={{
                fontSize: 32,
                animation: "popIn 0.3s ease-out",
                cursor: "pointer",
                lineHeight: 1,
              }}
              onClick={() => onTapTyped(item.emoji)}
            >
              {item.emoji}
            </span>
          ))
        )}
      </div>

      {typed.length > 0 && (
        <div className="flex gap-1 shrink-0">
          {!muted && (
            <button
              data-testid="replay-btn"
              className="active:scale-88"
              onClick={onReplayAll}
              style={{
                background: "rgba(78,205,196,0.2)",
                border: "1.5px solid rgba(78,205,196,0.3)",
                borderRadius: 12,
                width: 34,
                height: 34,
                fontSize: 15,
                cursor: "pointer",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
              }}
            >
              🔊
            </button>
          )}
          <button
            data-testid="delete-btn"
            className="active:scale-88"
            onClick={onDeleteLast}
            style={{
              background: "rgba(255,179,71,0.2)",
              border: "1.5px solid rgba(255,179,71,0.3)",
              borderRadius: 12,
              width: 34,
              height: 34,
              fontSize: 14,
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            ⌫
          </button>
          <button
            data-testid="clear-btn"
            className="active:scale-88"
            onClick={onClear}
            style={{
              background: "rgba(255,107,107,0.2)",
              border: "1.5px solid rgba(255,107,107,0.3)",
              borderRadius: 12,
              width: 34,
              height: 34,
              fontSize: 13,
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            ✕
          </button>
        </div>
      )}
    </div>
  );
}
