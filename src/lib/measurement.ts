interface AppEvent {
  t: number;
  e: string;
  d?: string;
}

const SESSION_KEY = "cm_events";
const MAX_EVENTS = 500;

export function logEvent(event: string, data?: string): void {
  try {
    const events: AppEvent[] = JSON.parse(
      localStorage.getItem(SESSION_KEY) || "[]",
    );
    events.push({ t: Date.now(), e: event, d: data });
    if (events.length > MAX_EVENTS)
      events.splice(0, events.length - MAX_EVENTS);
    localStorage.setItem(SESSION_KEY, JSON.stringify(events));
  } catch {
    // Silent fail — measurement should never break the app
  }
}

export function getEvents(): AppEvent[] {
  try {
    return JSON.parse(localStorage.getItem(SESSION_KEY) || "[]");
  } catch {
    return [];
  }
}

export function clearEvents(): void {
  try {
    localStorage.removeItem(SESSION_KEY);
  } catch {
    // Silent fail
  }
}

export function exportEventsJSON(): string {
  return JSON.stringify(getEvents(), null, 2);
}

export interface SessionStats {
  totalTaps: number;
  uniqueEmojis: number;
  sessionDurationSec: number;
  langSwitches: number;
  categoryChanges: number;
  topEmojis: Array<{ emoji: string; count: number }>;
  totalSessions: number;
  totalEvents: number;
}

export function getSessionStats(): SessionStats | null {
  const events = getEvents();
  if (events.length === 0) return null;

  const taps = events.filter((e) => e.e === "tap");
  const uniqueEmojis = new Set(taps.map((e) => e.d));
  const langSwitches = events.filter((e) => e.e === "lang");
  const categories = events.filter((e) => e.e === "cat");
  const sessions = events.filter((e) => e.e === "session_start");
  const sessionStart = events[0].t;
  const sessionEnd = events[events.length - 1].t;

  // Count emoji taps
  const counts: Record<string, number> = {};
  for (const tap of taps) {
    if (tap.d) counts[tap.d] = (counts[tap.d] || 0) + 1;
  }
  const topEmojis = Object.entries(counts)
    .sort((a, b) => b[1] - a[1])
    .slice(0, 5)
    .map(([emoji, count]) => ({ emoji, count }));

  return {
    totalTaps: taps.length,
    uniqueEmojis: uniqueEmojis.size,
    sessionDurationSec: Math.round((sessionEnd - sessionStart) / 1000),
    langSwitches: langSwitches.length,
    categoryChanges: categories.length,
    topEmojis,
    totalSessions: Math.max(sessions.length, 1),
    totalEvents: events.length,
  };
}
