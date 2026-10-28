import { act } from 'react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { usePulseOnChange } from '@/hooks/usePulseOnChange';
import { usePrefersReducedMotion } from '@/hooks/usePrefersReducedMotion';
import { renderHook } from './renderHook';

vi.mock('@/hooks/usePrefersReducedMotion', () => ({ usePrefersReducedMotion: vi.fn(() => false) }));

const reduced = vi.mocked(usePrefersReducedMotion);

describe('usePulseOnChange', () => {
  beforeEach(() => reduced.mockReturnValue(false));

  it('does not pulse on mount or when the value is unchanged', () => {
    const { result, rerender } = renderHook((v: string) => usePulseOnChange(v), 'FROZEN');
    expect(result.current.pulsing).toBe(false);
    rerender('FROZEN');
    expect(result.current.pulsing).toBe(false);
  });

  it('pulses when the value changes and resets on animation end', () => {
    const { result, rerender } = renderHook((v: string) => usePulseOnChange(v), 'FROZEN');
    rerender('DEPLOYED');
    expect(result.current.pulsing).toBe(true);
    act(() => result.current.onAnimationEnd());
    expect(result.current.pulsing).toBe(false);
  });

  it('never pulses under reduced motion', () => {
    reduced.mockReturnValue(true);
    const { result, rerender } = renderHook((v: string) => usePulseOnChange(v), 'FROZEN');
    rerender('DEPLOYED');
    expect(result.current.pulsing).toBe(false);
  });
});
