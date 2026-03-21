import { useState, useEffect } from "react";

export function useLocalStorage<T>(key: string, init: T): [T, (v: T) => void] {
  const [val, setVal] = useState<T>(() => {
    try {
      const s = localStorage.getItem(key);
      return s ? (JSON.parse(s) as T) : init;
    } catch {
      return init;
    }
  });

  useEffect(() => {
    localStorage.setItem(key, JSON.stringify(val));
  }, [key, val]);

  return [val, setVal];
}
