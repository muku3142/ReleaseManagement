import { useCallback, useState } from 'react';
import { usePrefersReducedMotion } from './usePrefersReducedMotion';

// pulsing turns true when value changes after mount; spread onAnimationEnd on the pulsing element to reset it.
export function usePulseOnChange<T>(value: T): { pulsing: boolean; onAnimationEnd: () => void } {
  const reduced = usePrefersReducedMotion();
  const [prev, setPrev] = useState(value);
  const [pulsing, setPulsing] = useState(false);

  if (!Object.is(prev, value)) {
    setPrev(value);
    setPulsing(!reduced);
  }

  const onAnimationEnd = useCallback(() => setPulsing(false), []);
  return { pulsing, onAnimationEnd };
}
