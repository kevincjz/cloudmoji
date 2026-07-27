import type { CSSProperties } from "react";

type Side = "top" | "bottom" | "left" | "right";

const GRADIENT: Record<Side, string> = {
  top: "180deg",
  bottom: "0deg",
  left: "90deg",
  right: "270deg",
};

/** Chevron rotation and nudge direction — both point at the hidden content. */
const POINT: Record<Side, { rot: number; dx: number; dy: number }> = {
  top: { rot: 180, dx: 0, dy: -3 },
  bottom: { rot: 0, dx: 0, dy: 3 },
  left: { rot: 90, dx: -3, dy: 0 },
  right: { rot: -90, dx: 3, dy: 0 },
};

/**
 * Marks a scroll edge that has more content behind it.
 *
 * A gradient alone was invisible: the app is near-black, so a dark fade over a
 * dark surface reads as nothing. The chevron carries the meaning; the gradient
 * only softens the content sliding under it.
 *
 * Non-interactive on purpose — a child tapping it should hit the emoji
 * underneath, not a control that does nothing they expect.
 */
export function ScrollHint({ side, visible }: { side: Side; visible: boolean }) {
  const vertical = side === "top" || side === "bottom";
  const { rot, dx, dy } = POINT[side];

  const wrapper: CSSProperties = {
    position: "absolute",
    pointerEvents: "none",
    opacity: visible ? 1 : 0,
    transition: "opacity 0.2s",
    background: `linear-gradient(${GRADIENT[side]}, rgba(13,12,36,0.92) 35%, rgba(13,12,36,0))`,
    zIndex: 3,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    ...(vertical
      ? { left: 0, right: 0, height: 34, [side]: 0 }
      : { top: 0, bottom: 0, width: 34, [side]: 0 }),
  };

  // The keyframes read these, so rotation and nudge compose in one transform
  // instead of the animation clobbering an inline rotate().
  const badge = {
    "--hint-rot": `${rot}deg`,
    "--hint-dx": `${dx}px`,
    "--hint-dy": `${dy}px`,
    display: "flex",
    alignItems: "center",
    justifyContent: "center",
    width: 26,
    height: 26,
    borderRadius: 999,
    background: "rgba(78,205,196,0.25)",
    border: "1.5px solid rgba(78,205,196,0.6)",
    boxShadow: "0 2px 10px rgba(0,0,0,0.5)",
    animation: "scrollHintNudge 1.8s ease-in-out infinite",
  } as CSSProperties;

  return (
    <div aria-hidden data-testid={`scroll-hint-${side}`} style={wrapper}>
      <span style={badge}>
        <svg width="13" height="13" viewBox="0 0 12 12" fill="none" aria-hidden>
          <path
            d="M2.5 4.5L6 8L9.5 4.5"
            stroke="#4ECDC4"
            strokeWidth="2.2"
            strokeLinecap="round"
            strokeLinejoin="round"
          />
        </svg>
      </span>
    </div>
  );
}
