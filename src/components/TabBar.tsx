export type TabId = "words" | "count";

interface TabBarProps {
  activeTab: TabId;
  onSelect: (tab: TabId) => void;
  maxWidth: number;
}

const TABS: Array<{ id: TabId; icon: string; label: string }> = [
  { id: "words", icon: "🗣️", label: "Words" },
  { id: "count", icon: "🧮", label: "Count" },
];

export function TabBar({ activeTab, onSelect, maxWidth }: TabBarProps) {
  return (
    <div
      data-testid="tab-bar"
      className="shrink-0"
      style={{
        // The safe-area inset must ADD to the 64px tap area, not eat into it.
        // box-sizing is border-box, so folding the inset into minHeight is what
        // kept the real tap target at 42.5px on notched phones.
        minHeight: "calc(64px + var(--sai-bottom))",
        paddingBottom: "var(--sai-bottom)",
        background: "rgba(15,14,42,0.95)",
        borderTop: "1px solid rgba(255,255,255,0.06)",
        backdropFilter: "blur(12px)",
      }}
    >
      {/* Match the content column above so the tabs line up with the grid */}
      <div
        className="flex w-full mx-auto"
        style={{ maxWidth, minHeight: 64 }}
      >
        {TABS.map((tab) => {
          const isActive = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              data-testid={`tab-${tab.id}`}
              onClick={() => onSelect(tab.id)}
              className="active:scale-90"
              style={{
                flex: 1,
                minHeight: 64,
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                gap: 3,
                background: "none",
                border: "none",
                cursor: "pointer",
                padding: 0,
                transition: "all 0.2s",
              }}
            >
              <span
                style={{
                  fontSize: 26,
                  lineHeight: 1,
                  filter: isActive ? "none" : "grayscale(0.5) opacity(0.5)",
                  transition: "filter 0.2s",
                }}
              >
                {tab.icon}
              </span>
              <span
                style={{
                  fontSize: 12,
                  fontWeight: 900,
                  color: isActive ? "#4ECDC4" : "rgba(255,255,255,0.35)",
                  fontFamily: "'Nunito', sans-serif",
                  letterSpacing: 0.3,
                  transition: "color 0.2s",
                }}
              >
                {tab.label}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
