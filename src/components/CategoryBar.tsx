import { SECTION_CATEGORIES } from "../lib/sections";
import { useScrollEdges } from "../hooks/useScrollEdges";
import { ScrollHint } from "./ScrollHint";
import type { Language, Category } from "../types";

interface CategoryBarProps {
  /** The section the list is currently showing — set by scrolling, not by tapping. */
  category: Category;
  lang: Language;
  /** Scrolls the list to that section. It does not filter anything. */
  onSelect: (cat: Category, label: string, icon: string) => void;
}

export function CategoryBar({ category, lang, onSelect }: CategoryBarProps) {
  const [barRef, bar] = useScrollEdges<HTMLDivElement>("x");

  return (
    <div data-testid="category-bar" className="shrink-0 relative">
      <ScrollHint side="left" visible={bar.overflows && !bar.atStart} />
      <ScrollHint side="right" visible={bar.overflows && !bar.atEnd} />
      <div
        ref={barRef}
        className="no-scroll flex gap-2 overflow-x-auto items-center"
        style={{ padding: "2px 12px 6px" }}
      >
      {SECTION_CATEGORIES.map((cat) => {
        const isActive = category === cat.id;
        return (
          <button
            key={cat.id}
            data-testid={`cat-${cat.id}`}
            data-active={isActive ? "true" : "false"}
            onClick={() => {
              const label = cat.labels[lang];
              onSelect(cat.id, label, cat.icon);
            }}
            className="active:scale-90 shrink-0"
            style={{
              background: isActive
                ? "rgba(78,205,196,0.2)"
                : "rgba(255,255,255,0.04)",
              border: isActive
                ? "1.5px solid rgba(78,205,196,0.4)"
                : "1.5px solid rgba(255,255,255,0.06)",
              borderRadius: 16,
              minHeight: 64,
              padding: "7px 16px",
              color: isActive ? "#4ECDC4" : "rgba(255,255,255,0.35)",
              fontSize: 14,
              fontWeight: 800,
              cursor: "pointer",
              whiteSpace: "nowrap",
              display: "flex",
              alignItems: "center",
              gap: 4,
              fontFamily: "'Nunito', sans-serif",
              transition: "all 0.2s",
            }}
          >
            <span style={{ fontSize: 18 }}>{cat.icon}</span>
            {cat.labels[lang]}
          </button>
          );
        })}
      </div>
    </div>
  );
}
