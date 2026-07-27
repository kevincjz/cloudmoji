import type { CSSProperties } from "react";

type Side = "top" | "bottom" | "left" | "right";

const DIRECTION: Record<Side, string> = {
  top: "180deg",
  bottom: "0deg",
  left: "90deg",
  right: "270deg",
};

/**
 * Gradient marking an edge that has more content behind it. Scrollbars are
 * hidden across the app and absent on iOS anyway, so without this there is
 * nothing telling a parent the list continues past the fold.
 * Non-interactive: a toddler tapping it should hit whatever is underneath.
 */
export function ScrollFade({ side, visible }: { side: Side; visible: boolean }) {
  const vertical = side === "top" || side === "bottom";
  const style: CSSProperties = {
    position: "absolute",
    pointerEvents: "none",
    opacity: visible ? 1 : 0,
    transition: "opacity 0.2s",
    background: `linear-gradient(${DIRECTION[side]}, rgba(15,14,42,0.95), rgba(15,14,42,0))`,
    zIndex: 2,
    ...(vertical
      ? { left: 0, right: 0, height: 28, [side]: 0 }
      : { top: 0, bottom: 0, width: 28, [side]: 0 }),
  };
  return <div aria-hidden data-testid={`scroll-fade-${side}`} style={style} />;
}
