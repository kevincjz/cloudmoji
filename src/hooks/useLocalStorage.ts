import { useState, useEffect } from "react";

/**
 * Persisted state with a validator. Without one, a stale or hand-edited value
 * flows straight through as T — a leftover language like "es" reaches
 * NUMBER_WORDS[lang] and crashes Count mode on the first tap.
 */
export function useLocalStorage<T>(
  key: string,
  init: T,
  isValid?: (v: unknown) => v is T,
): [T, (v: T) => void] {
  const [val, setVal] = useState<T>(() => {
    try {
      const s = localStorage.getItem(key);
      if (!s) return init;
      const parsed: unknown = JSON.parse(s);
      if (isValid && !isValid(parsed)) {
        // Recover rather than propagate a bad value.
        localStorage.removeItem(key);
        return init;
      }
      return parsed as T;
    } catch {
      return init;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(key, JSON.stringify(val));
    } catch {
      // Private mode and full quotas both throw here. Losing persistence is
      // acceptable; taking the app down with it is not.
    }
  }, [key, val]);

  return [val, setVal];
}
