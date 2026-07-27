import { useEffect, useRef, useState } from "react";

export interface ScrollEdges {
  /** There is more content before the current scroll position. */
  atStart: boolean;
  /** There is more content after the current scroll position. */
  atEnd: boolean;
  /** False when everything fits and no affordance should be drawn. */
  overflows: boolean;
}

const FITS: ScrollEdges = { atStart: true, atEnd: true, overflows: false };

/**
 * Tracks whether a scroll container has hidden content on either side.
 *
 * Scrollbars are hidden across the app and absent on iOS regardless, so without
 * this there is nothing telling a parent that six of the nine categories are
 * visible and the rest are below the fold.
 */
export function useScrollEdges<T extends HTMLElement>(axis: "x" | "y") {
  const ref = useRef<T>(null);
  const [edges, setEdges] = useState<ScrollEdges>(FITS);

  // Everything lives inside the effect, which depends only on `axis`. Taking a
  // callback as a dependency made this re-run on every render, and each run
  // disconnected the ResizeObserver before it could deliver its first callback
  // — so the fades never appeared at all.
  useEffect(() => {
    const el = ref.current;
    if (!el) return;

    const measure = () => {
      const pos = axis === "y" ? el.scrollTop : el.scrollLeft;
      const size = axis === "y" ? el.clientHeight : el.clientWidth;
      const total = axis === "y" ? el.scrollHeight : el.scrollWidth;
      setEdges({
        overflows: total > size + 1,
        atStart: pos <= 1,
        // 1px slack stops the fade flickering on fractional scroll offsets.
        atEnd: pos + size >= total - 1,
      });
    };

    el.addEventListener("scroll", measure, { passive: true });
    // Fires once on observe, which gives the initial reading without setting
    // state synchronously during the effect body.
    const ro = new ResizeObserver(measure);
    ro.observe(el);
    if (el.firstElementChild) ro.observe(el.firstElementChild);

    return () => {
      el.removeEventListener("scroll", measure);
      ro.disconnect();
    };
  }, [axis]);

  // Returned as a pair, not spread together: bundling the ref into the same
  // object makes every property read look like a ref access during render.
  return [ref, edges] as const;
}
