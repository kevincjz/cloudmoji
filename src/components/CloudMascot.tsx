import type { MascotMood } from "../types";

interface CloudMascotProps {
  mood?: MascotMood;
  size?: number;
}

export function CloudMascot({ mood = "happy", size = 64 }: CloudMascotProps) {
  const isBeaming = mood === "beaming";
  const isSpeaking = mood === "speaking";
  const isExcited = mood === "excited";
  const s = size;

  const animName = isSpeaking
    ? "mascotBounce"
    : isBeaming
      ? "mascotBeam"
      : "mascotFloat";
  const animDur = isSpeaking ? "0.4s" : isBeaming ? "0.6s" : "3s";
  const easing =
    isSpeaking || isBeaming ? "ease" : "ease-in-out";
  const shadowColor = isBeaming
    ? "rgba(255,230,109,0.5)"
    : "rgba(78,205,196,0.35)";

  return (
    <div
      data-testid="cloud-mascot"
      style={{
        width: s,
        height: s * 0.78,
        position: "relative",
        animation: `${animName} ${animDur} ${easing} infinite`,
        filter: `drop-shadow(0 ${s * 0.06}px ${s * 0.15}px ${shadowColor})`,
        transition: "filter 0.3s ease",
      }}
    >
      <svg
        viewBox="0 0 120 78"
        width={s}
        height={s * 0.78}
        style={{ overflow: "visible" }}
      >
        <defs>
          <radialGradient id="beamGlow">
            <stop offset="0%" stopColor="#FFE66D" stopOpacity="0.6" />
            <stop offset="100%" stopColor="#FFE66D" stopOpacity="0" />
          </radialGradient>
        </defs>

        {/* Beaming glow */}
        {isBeaming && (
          <circle
            cx="60"
            cy="45"
            r="52"
            fill="url(#beamGlow)"
            opacity="0.4"
            style={{ animation: "sparkle 1.2s ease-in-out infinite" }}
          />
        )}

        {/* Cloud body */}
        <circle cx="30" cy="46" r="20" fill="white" />
        <circle cx="52" cy="36" r="23" fill="white" />
        <circle cx="72" cy="30" r="26" fill="white" />
        <circle cx="94" cy="42" r="19" fill="white" />
        <circle cx="42" cy="44" r="16" fill="white" />
        <rect x="12" y="48" width="96" height="24" rx="12" fill="white" />

        {/* Cloud highlights */}
        <circle cx="72" cy="22" r="12" fill="#F8FCFF" />
        <circle cx="50" cy="30" r="8" fill="#F8FCFF" opacity="0.7" />

        {/* Cloud shadow */}
        <ellipse cx="60" cy="68" rx="44" ry="6" fill="#E8EEF4" opacity="0.4" />

        {/* Blush cheeks */}
        <ellipse
          cx="34"
          cy="58"
          rx={isBeaming ? 10 : 8}
          ry={isBeaming ? 5.5 : 4.5}
          fill={isBeaming ? "#FF9E9E" : "#FFB5B5"}
          opacity={isBeaming ? 0.7 : 0.55}
        />
        <ellipse
          cx="86"
          cy="58"
          rx={isBeaming ? 10 : 8}
          ry={isBeaming ? 5.5 : 4.5}
          fill={isBeaming ? "#FF9E9E" : "#FFB5B5"}
          opacity={isBeaming ? 0.7 : 0.55}
        />

        {/* Eyes */}
        {isBeaming ? (
          <g>
            <path d="M39 51 Q46 45 53 51" fill="none" stroke="#2D3436" strokeWidth="2.5" strokeLinecap="round" />
            <path d="M67 51 Q74 45 81 51" fill="none" stroke="#2D3436" strokeWidth="2.5" strokeLinecap="round" />
          </g>
        ) : isExcited ? (
          <g>
            <text x="46" y="53" textAnchor="middle" fontSize="12" fill="#2D3436" fontWeight="bold" fontFamily="sans-serif">★</text>
            <text x="74" y="53" textAnchor="middle" fontSize="12" fill="#2D3436" fontWeight="bold" fontFamily="sans-serif">★</text>
          </g>
        ) : isSpeaking ? (
          <g>
            <text x="46" y="54" textAnchor="middle" fontSize="12" fill="#2D3436" fontWeight="bold" fontFamily="sans-serif">●</text>
            <text x="74" y="54" textAnchor="middle" fontSize="12" fill="#2D3436" fontWeight="bold" fontFamily="sans-serif">●</text>
          </g>
        ) : (
          <g>
            <text x="46" y="54" textAnchor="middle" fontSize="12" fill="#2D3436" fontWeight="bold" fontFamily="sans-serif">◠</text>
            <text x="74" y="54" textAnchor="middle" fontSize="12" fill="#2D3436" fontWeight="bold" fontFamily="sans-serif">◠</text>
          </g>
        )}

        {/* Mouth */}
        {isSpeaking ? (
          <ellipse cx="60" cy="62" rx="5.5" ry="4.5" fill="#FF6B6B" stroke="#E55555" strokeWidth="0.5" />
        ) : isBeaming ? (
          <path d="M46 59 Q60 74 74 59" fill="#FF6B6B" stroke="#E55555" strokeWidth="0.5" />
        ) : isExcited ? (
          <path d="M53 60 Q60 69 67 60" fill="#FF6B6B" stroke="none" />
        ) : (
          <path d="M54 61 Q60 66 66 61" fill="none" stroke="#2D3436" strokeWidth="1.8" strokeLinecap="round" />
        )}

        {/* Sparkles for excited/beaming */}
        {(isExcited || isBeaming) && (
          <g>
            <text x="102" y="24" fontSize="10" style={{ animation: "sparkle 0.6s ease infinite" }}>✨</text>
            <text x="12" y="28" fontSize="8" style={{ animation: "sparkle 0.8s ease infinite 0.2s" }}>✨</text>
          </g>
        )}
        {isBeaming && (
          <g>
            <text x="4" y="50" fontSize="9" style={{ animation: "sparkle 0.7s ease infinite 0.1s" }}>⭐</text>
            <text x="110" y="48" fontSize="9" style={{ animation: "sparkle 0.9s ease infinite 0.4s" }}>⭐</text>
            <text x="58" y="12" fontSize="11" style={{ animation: "sparkle 0.5s ease infinite 0.3s" }}>🌟</text>
          </g>
        )}
      </svg>
    </div>
  );
}
