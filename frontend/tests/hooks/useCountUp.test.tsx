import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { useCountUp } from '@/hooks/useCountUp';
import { usePrefersReducedMotion } from '@/hooks/usePrefersReducedMotion';
import { renderHook } from './renderHook';

vi.mock('@/hooks/usePrefersReducedMotion', () => ({ usePrefersReducedMotion: vi.fn(() => false) }));

const reduced = vi.mocked(usePrefersReducedMotion);

function advance(ms: number) {
  act(() => vi.advanceTimersByTime(ms));
}

describe('useCountUp', () => {
  beforeEach(() => {
    reduced.mockReturnValue(false);
    vi.useFakeTimers({ toFake: ['requestAnimationFrame', 'cancelAnimationFrame', 'performance'] });
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns null for a null value', () => {
    const { result } = renderHook((v: number | null) => useCountUp(v), null);
    expect(result.current).toBeNull();
  });

  it('counts from 0 to the value once, then returns null', () => {
    const { result } = renderHook((v: number | null) => useCountUp(v), 42);
    expect(result.current).toBe(0);
    advance(300);
    expect(result.current).toBeGreaterThan(0);
    expect(result.current).toBeLessThan(42);
    advance(400);
    expect(result.current).toBeNull();
  });

  it('does not count again when the value changes later', () => {
    const { result, rerender } = renderHook((v: number | null) => useCountUp(v), 42);
    advance(700);
    rerender(50);
    expect(result.current).toBeNull();
    advance(100);
    expect(result.current).toBeNull();
  });

  it('does not animate zero', () => {
    const { result } = renderHook((v: number | null) => useCountUp(v), 0);
    expect(result.current).toBeNull();
  });

  it('animates once when the number arrives after loading', () => {
    const { result, rerender } = renderHook((v: number | null) => useCountUp(v), null);
    expect(result.current).toBeNull();
    rerender(7);
    expect(result.current).toBe(0);
    advance(700);
    expect(result.current).toBeNull();
  });

  it('never animates under reduced motion', () => {
    reduced.mockReturnValue(true);
    const { result } = renderHook((v: number | null) => useCountUp(v), 42);
    expect(result.current).toBeNull();
    advance(300);
    expect(result.current).toBeNull();
    advance(400);
    expect(result.current).toBeNull();
  });
});
