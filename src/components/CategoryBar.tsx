import { CATEGORIES } from "../data/emojis";
import type { Language, Category } from "../types";

interface CategoryBarProps {
  category: "all" | Category;
  lang: Language;
  onSelect: (cat: "all" | Category, label: string, icon: string) => void;
}

export function CategoryBar({ category, lang, onSelect }: CategoryBarProps) {
  return (
    <div
      data-testid="category-bar"
      className="no-scroll flex gap-[5px] shrink-0 overflow-x-auto"
      style={{ padding: "2px 12px 6px" }}
    >
      {CATEGORIES.map((cat) => {
        const isActive = category === cat.id;
        return (
          <button
            key={cat.id}
            data-testid={`cat-${cat.id}`}
            onClick={() => {
              const label = lang === "zh" ? cat.labelZh : lang === "ms" ? cat.labelMs : cat.label;
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
              padding: "7px 14px",
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
            {lang === "zh" ? cat.labelZh : lang === "ms" ? cat.labelMs : cat.label}
          </button>
        );
      })}
    </div>
  );
}
