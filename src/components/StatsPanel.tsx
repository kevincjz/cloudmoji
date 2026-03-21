import { useState, useEffect } from "react";
import {
  getSessionStats,
  exportEventsJSON,
  clearEvents,
  type SessionStats,
} from "../lib/measurement";

interface StatsPanelProps {
  onClose: () => void;
}

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}m ${s}s`;
}

export function StatsPanel({ onClose }: StatsPanelProps) {
  const [stats, setStats] = useState<SessionStats | null>(null);

  useEffect(() => {
    setStats(getSessionStats());
  }, []);

  const handleExport = () => {
    const json = exportEventsJSON();
    const blob = new Blob([json], { type: "application/json" });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `cloudmoji-events-${new Date().toISOString().slice(0, 10)}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleClear = () => {
    clearEvents();
    setStats(null);
  };

  return (
    <div
      data-testid="stats-panel"
      className="fixed inset-0 z-50 flex items-center justify-center"
      style={{ background: "rgba(0,0,0,0.7)" }}
      onClick={onClose}
    >
      <div
        className="rounded-2xl"
        style={{
          background: "linear-gradient(160deg, #1A1145, #0D2137)",
          border: "1.5px solid rgba(255,255,255,0.1)",
          padding: "20px 24px",
          maxWidth: 340,
          width: "90%",
          fontFamily: "'Nunito', sans-serif",
          color: "#fff",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center justify-between" style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 16, fontWeight: 900 }}>📊 Cloudmoji Stats</div>
          <button
            data-testid="stats-close"
            onClick={onClose}
            style={{
              background: "rgba(255,255,255,0.1)",
              border: "none",
              borderRadius: 10,
              width: 32,
              height: 32,
              fontSize: 14,
              cursor: "pointer",
              color: "#fff",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            ✕
          </button>
        </div>

        {stats ? (
          <div className="flex flex-col gap-3">
            <div>
              <div style={{ fontSize: 11, fontWeight: 800, color: "rgba(255,255,255,0.4)", marginBottom: 6, textTransform: "uppercase", letterSpacing: 1 }}>
                All Time
              </div>
              <div className="grid grid-cols-2 gap-2">
                <StatBox label="Sessions" value={String(stats.totalSessions)} />
                <StatBox label="Total Taps" value={String(stats.totalTaps)} />
                <StatBox label="Unique Emojis" value={String(stats.uniqueEmojis)} />
                <StatBox label="Duration" value={formatDuration(stats.sessionDurationSec)} />
              </div>
            </div>

            <div>
              <div style={{ fontSize: 11, fontWeight: 800, color: "rgba(255,255,255,0.4)", marginBottom: 6, textTransform: "uppercase", letterSpacing: 1 }}>
                Activity
              </div>
              <div className="grid grid-cols-2 gap-2">
                <StatBox label="Lang Switches" value={String(stats.langSwitches)} />
                <StatBox label="Cat Changes" value={String(stats.categoryChanges)} />
              </div>
            </div>

            {stats.topEmojis.length > 0 && (
              <div>
                <div style={{ fontSize: 11, fontWeight: 800, color: "rgba(255,255,255,0.4)", marginBottom: 6, textTransform: "uppercase", letterSpacing: 1 }}>
                  Most Tapped
                </div>
                <div className="flex gap-2">
                  {stats.topEmojis.map((e) => (
                    <div
                      key={e.emoji}
                      className="flex flex-col items-center"
                      style={{
                        background: "rgba(255,255,255,0.04)",
                        borderRadius: 12,
                        padding: "6px 10px",
                        border: "1px solid rgba(255,255,255,0.06)",
                      }}
                    >
                      <span style={{ fontSize: 24 }}>{e.emoji}</span>
                      <span style={{ fontSize: 10, fontWeight: 800, color: "rgba(255,255,255,0.3)" }}>
                        {e.count}×
                      </span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            <div className="flex gap-2" style={{ marginTop: 4 }}>
              <button
                data-testid="stats-export"
                onClick={handleExport}
                style={{
                  flex: 1,
                  background: "rgba(78,205,196,0.2)",
                  border: "1.5px solid rgba(78,205,196,0.3)",
                  borderRadius: 12,
                  padding: "8px 0",
                  color: "#4ECDC4",
                  fontSize: 12,
                  fontWeight: 900,
                  cursor: "pointer",
                  fontFamily: "'Nunito', sans-serif",
                }}
              >
                Export JSON
              </button>
              <button
                data-testid="stats-clear"
                onClick={handleClear}
                style={{
                  flex: 1,
                  background: "rgba(255,107,107,0.2)",
                  border: "1.5px solid rgba(255,107,107,0.3)",
                  borderRadius: 12,
                  padding: "8px 0",
                  color: "#FF6B6B",
                  fontSize: 12,
                  fontWeight: 900,
                  cursor: "pointer",
                  fontFamily: "'Nunito', sans-serif",
                }}
              >
                Clear Data
              </button>
            </div>
          </div>
        ) : (
          <div style={{ color: "rgba(255,255,255,0.3)", fontSize: 14, fontWeight: 800, textAlign: "center", padding: "20px 0" }}>
            No data yet. Start tapping!
          </div>
        )}
      </div>
    </div>
  );
}

function StatBox({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        background: "rgba(255,255,255,0.04)",
        border: "1px solid rgba(255,255,255,0.06)",
        borderRadius: 12,
        padding: "8px 10px",
      }}
    >
      <div style={{ fontSize: 18, fontWeight: 900 }}>{value}</div>
      <div style={{ fontSize: 10, fontWeight: 800, color: "rgba(255,255,255,0.3)" }}>
        {label}
      </div>
    </div>
  );
}
