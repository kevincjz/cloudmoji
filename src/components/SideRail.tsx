import { CATEGORIES } from "../data/emojis";
import { useScrollEdges } from "../hooks/useScrollEdges";
import { ScrollHint } from "./ScrollHint";
import type { Language, Category } from "../types";
import type { TabId } from "./TabBar";

interface SideRailProps {
  category: "all" | Category;
  lang: Language;
  activeTab: TabId;
  onSelectCategory: (cat: "all" | Category, label: string, icon: string) => void;
  onSelectTab: (tab: TabId) => void;
  /** Count mode has no categories — the rail is then just the tab switcher. */
  showCategories?: boolean;
}

const TABS: Array<{ id: TabId; icon: string }> = [
  { id: "words", icon: "🗣️" },
  { id: "count", icon: "🧮" },
];

// Two columns of 64px targets plus gaps. One column left only 2.6 of the 9
// categories reachable without scrolling, because the stacked tab switcher
// was taking 45% of the rail.
const RAIL_WIDTH = 156;

/**
 * Landscape-only navigation. In landscape the screen has width to spare and
 * almost no height — a phone gives roughly 320px, and Safari's own chrome has
 * already taken a quarter of it. Stacking the category bar and tab bar
 * horizontally left the grid under one row tall. Moving both into a vertical
 * rail spends width we are not using and hands the height back to the grid.
 *
 * Icons only: labels would force the rail wider, and the emoji already carries
 * the meaning for a child who cannot read.
 */
export function SideRail({
  category,
  lang,
  activeTab,
  onSelectCategory,
  onSelectTab,
  showCategories = true,
}: SideRailProps) {
  const [catsRef, cats] = useScrollEdges<HTMLDivElement>("y");
  return (
    <div
      data-testid="side-rail"
      className="shrink-0 flex flex-col"
      style={{
        // Grow by the inset instead of padding into it, so the icons keep
        // their full width while the background still reaches the edge.
        width: `calc(${RAIL_WIDTH}px + var(--sai-left))`,
        borderRight: "1px solid rgba(255,255,255,0.06)",
        background: "rgba(15,14,42,0.5)",
        paddingLeft: "var(--sai-left)",
      }}
    >
      <div className="flex-1 relative" style={{ minHeight: 0 }}>
        <ScrollHint side="top" visible={cats.overflows && !cats.atStart} />
        <ScrollHint side="bottom" visible={cats.overflows && !cats.atEnd} />
        <div
          ref={catsRef}
          className="no-scroll h-full flex flex-wrap content-start justify-center gap-2 overflow-y-auto"
          style={{ padding: "8px 4px" }}
        >
        {showCategories &&
          CATEGORIES.map((cat) => {
            const isActive = category === cat.id;
            return (
              <button
                key={cat.id}
                data-testid={`rail-cat-${cat.id}`}
                aria-label={cat.labels[lang]}
                onClick={() => onSelectCategory(cat.id, cat.labels[lang], cat.icon)}
                className="active:scale-90 shrink-0"
                style={{
                  width: 64,
                  minHeight: 64,
                  borderRadius: 16,
                  border: isActive
                    ? "1.5px solid rgba(78,205,196,0.4)"
                    : "1.5px solid transparent",
                  background: isActive ? "rgba(78,205,196,0.2)" : "none",
                  cursor: "pointer",
                  fontSize: 28,
                  lineHeight: 1,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  filter: isActive ? "none" : "grayscale(0.4) opacity(0.65)",
                  transition: "all 0.2s",
                }}
              >
                {cat.icon}
                </button>
            );
          })}
        </div>
      </div>

      <div
        className="shrink-0 flex items-center justify-center gap-2"
        style={{
          borderTop: "1px solid rgba(255,255,255,0.06)",
          // Longhand only — mixing `padding` with `paddingBottom` makes React
          // warn and can leave the two out of sync across renders.
          paddingTop: 8,
          paddingLeft: 4,
          paddingRight: 4,
          paddingBottom: "calc(8px + var(--sai-bottom))",
        }}
      >
        {TABS.map((tab) => {
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              data-testid={`tab-${tab.id}`}
              onClick={() => onSelectTab(tab.id)}
              className="active:scale-90"
              style={{
                width: 64,
                minHeight: 64,
                borderRadius: 16,
                border: "none",
                background: isActive ? "rgba(78,205,196,0.14)" : "none",
                cursor: "pointer",
                fontSize: 26,
                lineHeight: 1,
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                filter: isActive ? "none" : "grayscale(0.5) opacity(0.5)",
                transition: "all 0.2s",
              }}
            >
              {tab.icon}
            </button>
          );
        })}
      </div>
    </div>
  );
}
