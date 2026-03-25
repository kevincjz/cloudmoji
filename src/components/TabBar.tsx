export type TabId = "words" | "count";

interface TabBarProps {
  activeTab: TabId;
  onSelect: (tab: TabId) => void;
}

const TABS: Array<{ id: TabId; icon: string; label: string }> = [
  { id: "words", icon: "🗣️", label: "Words" },
  { id: "count", icon: "🔢", label: "Count" },
];

export function TabBar({ activeTab, onSelect }: TabBarProps) {
  return (
    <div
      data-testid="tab-bar"
      className="shrink-0 flex"
      style={{
        minHeight: 64,
        background: "rgba(15,14,42,0.95)",
        borderTop: "1px solid rgba(255,255,255,0.06)",
        backdropFilter: "blur(12px)",
        paddingBottom: "env(safe-area-inset-bottom, 0px)",
      }}
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
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              gap: 2,
              background: "none",
              border: "none",
              cursor: "pointer",
              padding: 0,
              transition: "all 0.2s",
            }}
          >
            <span
              style={{
                fontSize: 24,
                lineHeight: 1,
                filter: isActive ? "none" : "grayscale(0.5) opacity(0.5)",
                transition: "filter 0.2s",
              }}
            >
              {tab.icon}
            </span>
            <span
              style={{
                fontSize: 11,
                fontWeight: 900,
                color: isActive ? "#4ECDC4" : "rgba(255,255,255,0.3)",
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
  );
}
