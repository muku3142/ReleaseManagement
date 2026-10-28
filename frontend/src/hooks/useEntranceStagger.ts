import { useEffect, useRef, type CSSProperties } from 'react';
import { usePrefersReducedMotion } from './usePrefersReducedMotion';

const STAGGER_STEP_MS = 30;
const STAGGER_MAX_STEPS = 10;

type StaggerProps = { className?: string; style?: CSSProperties };

// Staggers rows in on the first render where ready is true only; later renders (sort, filter, refetch) get nothing.
export function useEntranceStagger(ready: boolean): (index: number) => StaggerProps {
  const reduced = usePrefersReducedMotion();
  const done = useRef(false);

  useEffect(() => {
    if (ready) done.current = true;
  }, [ready]);

  const active = ready && !reduced && !done.current;
  return (index) =>
    active
      ? {
          className: 'motion-safe:animate-row-in',
          style: { animationDelay: `${Math.min(index, STAGGER_MAX_STEPS) * STAGGER_STEP_MS}ms` },
        }
      : {};
}
