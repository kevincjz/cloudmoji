import { useState, useEffect, useRef, useCallback } from "react";

/* ─── Phase timings (seconds) ─── */
const INHALE = 4;
const HOLD = 2;
const EXHALE = 6;
const CYCLE = INHALE + HOLD + EXHALE;

const DURATIONS = [
  { label: "2 min", mins: 2 },
  { label: "5 min", mins: 5 },
  { label: "10 min", mins: 10 },
];

/* ─── Sleepy Cloud ─── */
function BreathingCloud({ scale, phase, asleep, dimAmount }) {
  const eyeClosed = asleep || phase === "hold";

  return (
    <div
      style={{
        transform: `scale(${scale})`,
        transition: "transform 0.1s linear",
        filter: `drop-shadow(0 12px ${28 * scale}px rgba(168,214,255,${0.28 * (1 - dimAmount)}))`,
        willChange: "transform",
      }}
    >
      <svg viewBox="0 0 120 78" width={200} height={130} style={{ overflow: "visible" }}>
        {/* soft halo */}
        <ellipse
          cx="60"
          cy="46"
          rx={54 * scale}
          ry={36 * scale}
          fill="url(#calmGlow)"
          opacity={0.35 * (1 - dimAmount)}
        />
        <defs>
          <radialGradient id="calmGlow">
            <stop offset="0%" stopColor="#A8D6FF" stopOpacity="0.5" />
            <stop offset="100%" stopColor="#A8D6FF" stopOpacity="0" />
          </radialGradient>
        </defs>

        {/* cloud body */}
        <circle cx="30" cy="46" r="20" fill="white" />
        <circle cx="52" cy="36" r="23" fill="white" />
        <circle cx="72" cy="30" r="26" fill="white" />
        <circle cx="94" cy="42" r="19" fill="white" />
        <circle cx="42" cy="44" r="16" fill="white" />
        <rect x="12" y="48" width="96" height="24" rx="12" fill="white" />
        <circle cx="72" cy="22" r="12" fill="#F8FCFF" />
        <circle cx="50" cy="30" r="8" fill="#F8FCFF" opacity="0.7" />
        <ellipse cx="60" cy="68" rx="44" ry="6" fill="#E8EEF4" opacity="0.4" />

        {/* cheeks — soft */}
        <ellipse cx="34" cy="58" rx="8" ry="4.5" fill="#FFB5B5" opacity="0.42" />
        <ellipse cx="86" cy="58" rx="8" ry="4.5" fill="#FFB5B5" opacity="0.42" />

        {/* eyes — gentle closed arcs when holding or asleep */}
        {eyeClosed ? (
          <>
            <path d="M40 52 Q46 47 52 52" fill="none" stroke="#2D3436" strokeWidth="2.2" strokeLinecap="round" />
            <path d="M68 52 Q74 47 80 52" fill="none" stroke="#2D3436" strokeWidth="2.2" strokeLinecap="round" />
          </>
        ) : (
          <>
            <ellipse cx="46" cy="51" rx="2.4" ry="2.8" fill="#2D3436" />
            <ellipse cx="74" cy="51" rx="2.4" ry="2.8" fill="#2D3436" />
          </>
        )}

        {/* mouth — small o on inhale, soft smile otherwise */}
        {asleep ? (
          <ellipse cx="60" cy="62" rx="4" ry="3" fill="#FF9E9E" opacity="0.75" />
        ) : phase === "inhale" ? (
          <ellipse cx="60" cy="62" rx="4.5" ry="4" fill="#FF9E9E" opacity="0.7" />
        ) : (
          <path d="M55 61 Q60 65 65 61" fill="none" stroke="#2D3436" strokeWidth="1.6" strokeLinecap="round" opacity="0.75" />
        )}

        {/* zzz when asleep */}
        {asleep && (
          <>
            <text x="98" y="18" fontSize="12" fill="#A8D6FF" opacity="0.8" style={{ animation: "zzzFloat 2.4s ease-in-out infinite" }}>z</text>
            <text x="106" y="8" fontSize="9" fill="#A8D6FF" opacity="0.6" style={{ animation: "zzzFloat 2.4s ease-in-out infinite 0.5s" }}>z</text>
          </>
        )}
      </svg>
    </div>
  );
}

export default function BreathingCloudApp() {
  const [duration, setDuration] = useState(null); // null = picker
  const [elapsed, setElapsed] = useState(0);
  const [scale, setScale] = useState(0.75);
  const [phase, setPhase] = useState("inhale");
  const [asleep, setAsleep] = useState(false);
  const rafRef = useRef(null);
  const startRef = useRef(null);

  const totalSeconds = duration ? duration * 60 : 0;

  /* breathing loop driven by rAF for smoothness */
  useEffect(() => {
    if (duration === null) return;
    startRef.current = performance.now();

    const tick = (now) => {
      const t = (now - startRef.current) / 1000;
      setElapsed(t);

      if (t >= totalSeconds) {
        setAsleep(true);
        setScale(0.72);
        return; // stop — no loop
      }

      const p = t % CYCLE;
      let s, ph;
      if (p < INHALE) {
        const k = p / INHALE;
        const eased = 0.5 - 0.5 * Math.cos(Math.PI * k); // ease in-out
        s = 0.75 + eased * 0.35;
        ph = "inhale";
      } else if (p < INHALE + HOLD) {
        s = 1.1;
        ph = "hold";
      } else {
        const k = (p - INHALE - HOLD) / EXHALE;
        const eased = 0.5 - 0.5 * Math.cos(Math.PI * k);
        s = 1.1 - eased * 0.35;
        ph = "exhale";
      }
      setScale(s);
      setPhase(ph);
      rafRef.current = requestAnimationFrame(tick);
    };

    rafRef.current = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(rafRef.current);
  }, [duration, totalSeconds]);

  const reset = useCallback(() => {
    cancelAnimationFrame(rafRef.current);
    setDuration(null);
    setElapsed(0);
    setAsleep(false);
    setScale(0.75);
    setPhase("inhale");
  }, []);

  /* screen dims progressively across the session */
  const progress = totalSeconds ? Math.min(elapsed / totalSeconds, 1) : 0;
  const dim = progress * 0.55;

  return (
    <div
      style={{
        minHeight: "100vh",
        background: `linear-gradient(170deg,
          rgba(15,14,42,${1}) 0%,
          rgba(26,17,69,${1 - dim * 0.35}) 45%,
          rgba(13,33,55,${1 - dim * 0.25}) 100%)`,
        fontFamily: "'Nunito', sans-serif",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        position: "relative",
        overflow: "hidden",
        userSelect: "none",
        WebkitTapHighlightColor: "transparent",
        transition: "background 1.2s linear",
      }}
    >
      <link href="https://fonts.googleapis.com/css2?family=Lilita+One&family=Nunito:wght@700;800;900&display=swap" rel="stylesheet" />
      <style>{`
        @keyframes zzzFloat{0%,100%{opacity:.35;transform:translateY(0)}50%{opacity:.9;transform:translateY(-4px)}}
        @keyframes starTwinkle{0%,100%{opacity:.15}50%{opacity:.5}}
        @keyframes fadeUp{0%{opacity:0;transform:translateY(12px)}100%{opacity:1;transform:translateY(0)}}
        .pick:active{transform:scale(.94)}
      `}</style>

      {/* dark overlay that deepens as session progresses */}
      <div style={{ position: "fixed", inset: 0, background: "#000", opacity: dim * 0.5, pointerEvents: "none", transition: "opacity 1.2s linear" }} />

      {/* faint stars */}
      <div style={{ position: "fixed", inset: 0, pointerEvents: "none" }}>
        {[...Array(14)].map((_, i) => (
          <div
            key={i}
            style={{
              position: "absolute",
              left: `${(i * 37) % 100}%`,
              top: `${(i * 23) % 90}%`,
              width: 3,
              height: 3,
              borderRadius: "50%",
              background: "#A8D6FF",
              animation: `starTwinkle ${3 + (i % 4)}s ease-in-out infinite ${i * 0.4}s`,
            }}
          />
        ))}
      </div>

      <div style={{ position: "relative", zIndex: 2, display: "flex", flexDirection: "column", alignItems: "center", gap: 28 }}>
        {/* ── duration picker ── */}
        {duration === null ? (
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 26, animation: "fadeUp .5s ease-out" }}>
            <BreathingCloud scale={0.85} phase="hold" asleep={false} dimAmount={0} />
            <div style={{ textAlign: "center" }}>
              <div style={{ fontFamily: "'Lilita One',sans-serif", fontSize: 26, background: "linear-gradient(135deg,#A8D6FF,#C4B5FD)", WebkitBackgroundClip: "text", WebkitTextFillColor: "transparent" }}>
                Sleepy Cloud
              </div>
              <div style={{ fontSize: 12, fontWeight: 800, color: "rgba(255,255,255,.32)", marginTop: 2, letterSpacing: .4 }}>
                Breathe along with the cloud
              </div>
            </div>

            <div style={{ display: "flex", gap: 10 }}>
              {DURATIONS.map((d) => (
                <button
                  key={d.mins}
                  className="pick"
                  onClick={() => setDuration(d.mins)}
                  style={{
                    background: "rgba(168,214,255,.10)",
                    border: "2px solid rgba(168,214,255,.22)",
                    borderRadius: 20,
                    padding: "14px 24px",
                    color: "#A8D6FF",
                    fontSize: 16,
                    fontWeight: 900,
                    fontFamily: "'Nunito',sans-serif",
                    cursor: "pointer",
                    minWidth: 88,
                    minHeight: 56,
                    transition: "transform .12s",
                  }}
                >
                  {d.label}
                </button>
              ))}
            </div>
            <div style={{ fontSize: 11, fontWeight: 800, color: "rgba(255,255,255,.18)" }}>
              Grown-up picks the time
            </div>
          </div>
        ) : (
          /* ── breathing session ── */
          <>
            <BreathingCloud scale={scale} phase={phase} asleep={asleep} dimAmount={dim} />

            {!asleep && (
              <div
                style={{
                  fontSize: 15,
                  fontWeight: 800,
                  color: `rgba(168,214,255,${0.5 - dim * 0.4})`,
                  letterSpacing: 1.4,
                  textTransform: "lowercase",
                  transition: "color .6s linear",
                  minHeight: 20,
                }}
              >
                {phase === "inhale" ? "breathe in" : phase === "hold" ? "" : "breathe out"}
              </div>
            )}

            {asleep && (
              <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 18, animation: "fadeUp .8s ease-out" }}>
                <div style={{ fontSize: 15, fontWeight: 800, color: "rgba(168,214,255,.45)", letterSpacing: 1 }}>
                  all done
                </div>
                <button
                  onClick={reset}
                  style={{
                    background: "rgba(255,255,255,.05)",
                    border: "2px solid rgba(255,255,255,.10)",
                    borderRadius: 18,
                    padding: "12px 22px",
                    color: "rgba(255,255,255,.4)",
                    fontSize: 13,
                    fontWeight: 900,
                    fontFamily: "'Nunito',sans-serif",
                    cursor: "pointer",
                    minHeight: 52,
                  }}
                >
                  ← back
                </button>
              </div>
            )}

            {/* thin progress line */}
            {!asleep && (
              <div style={{ position: "fixed", bottom: 0, left: 0, right: 0, height: 2, background: "rgba(255,255,255,.04)" }}>
                <div
                  style={{
                    height: "100%",
                    width: `${progress * 100}%`,
                    background: "linear-gradient(90deg,#A8D6FF,#C4B5FD)",
                    opacity: 0.35,
                    transition: "width .3s linear",
                  }}
                />
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
