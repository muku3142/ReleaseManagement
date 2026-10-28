import { useEffect, useRef, useState } from 'react';
import { usePrefersReducedMotion } from './usePrefersReducedMotion';

export const COUNT_UP_MS = 600;

// The number to show while counting, or null when nothing is animating. Plays once, the first time value is a positive number.
export function useCountUp(value: number | null, durationMs: number = COUNT_UP_MS): number | null {
  const reduced = usePrefersReducedMotion();
  const played = useRef(false);
  const [display, setDisplay] = useState<number | null>(null);

  useEffect(() => {
    if (played.current || reduced || typeof value !== 'number' || !Number.isFinite(value) || value <= 0) return;
    played.current = true;
    setDisplay(0);
    const start = performance.now();
    let frame = 0;
    const tick = (now: number) => {
      const t = Math.min((now - start) / durationMs, 1);
      if (t >= 1) {
        setDisplay(null);
        return;
      }
      setDisplay(Math.round(value * (1 - (1 - t) ** 3)));
      frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
    return () => {
      cancelAnimationFrame(frame);
      setDisplay(null);
    };
  }, [value, reduced, durationMs]);

  return display;
}
