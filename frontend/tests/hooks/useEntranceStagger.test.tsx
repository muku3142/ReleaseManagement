import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useEntranceStagger } from '@/hooks/useEntranceStagger';
import { usePrefersReducedMotion } from '@/hooks/usePrefersReducedMotion';
import { renderHook } from './renderHook';

vi.mock('@/hooks/usePrefersReducedMotion', () => ({ usePrefersReducedMotion: vi.fn(() => false) }));

const reduced = vi.mocked(usePrefersReducedMotion);

describe('useEntranceStagger', () => {
  beforeEach(() => reduced.mockReturnValue(false));

  it('returns nothing until ready, then staggers the first ready render with a capped delay', () => {
    const { result, rerender } = renderHook((ready: boolean) => useEntranceStagger(ready), false);
    expect(result.current(0)).toEqual({});

    rerender(true);
    expect(result.current(0).className).toContain('animate-row-in');
    expect(result.current(0).style?.animationDelay).toBe('0ms');
    expect(result.current(3).style?.animationDelay).toBe('90ms');
    expect(result.current(25).style?.animationDelay).toBe('300ms');
  });

  it('stops staggering after the first ready render', () => {
    const { result, rerender } = renderHook((ready: boolean) => useEntranceStagger(ready), true);
    expect(result.current(0).className).toContain('animate-row-in');
    rerender(true);
    expect(result.current(0)).toEqual({});
    expect(result.current(5)).toEqual({});
  });

  it('never staggers under reduced motion', () => {
    reduced.mockReturnValue(true);
    const { result, rerender } = renderHook((ready: boolean) => useEntranceStagger(ready), false);
    rerender(true);
    expect(result.current(0)).toEqual({});
  });
});
