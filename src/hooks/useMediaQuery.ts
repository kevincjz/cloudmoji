import { useState, useEffect } from "react";

export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() =>
    typeof window !== "undefined" ? window.matchMedia(query).matches : false,
  );

  useEffect(() => {
    const mql = window.matchMedia(query);
    const onChange = () => setMatches(mql.matches);
    onChange();
    mql.addEventListener("change", onChange);
    return () => mql.removeEventListener("change", onChange);
  }, [query]);

  return matches;
}

/**
 * True when the viewport is too short to fit the full-height layout —
 * i.e. a phone held sideways. Keyed on height, not orientation alone, so a
 * tall iPad in landscape keeps the roomy portrait layout.
 */
export function useCompactLayout(): boolean {
  return useMediaQuery("(max-height: 560px) and (orientation: landscape)");
}
