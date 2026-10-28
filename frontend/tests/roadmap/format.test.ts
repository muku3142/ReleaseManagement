import { describe, expect, it } from 'vitest';
import { formatChange, formatInputValue, relativeTime } from '../../src/roadmap/format';

const NOW = new Date('2026-10-25T12:00:00Z');
const ago = (ms: number) => new Date(NOW.getTime() - ms).toISOString();

describe('relativeTime', () => {
  it('uses the UI-SPEC buckets', () => {
    expect(relativeTime(ago(30_000), NOW)).toBe('just now');
    expect(relativeTime(ago(5 * 60_000), NOW)).toBe('5m ago');
    expect(relativeTime(ago(3 * 3_600_000), NOW)).toBe('3h ago');
    expect(relativeTime(ago(2 * 86_400_000), NOW)).toBe('2d ago');
  });
});

describe('formatChange', () => {
  it('formats each input with its unit or label', () => {
    expect(formatChange('CONFIDENCE', 80, 100)).toBe('Medium (80%) → High (100%)');
    expect(formatChange('EFFORT', 2, 3.5)).toBe('2 → 3.5 person-weeks');
    expect(formatChange('REACH', null, 10)).toBe('not set → 10');
    expect(formatChange('IMPACT', 1, 2)).toBe('1 → 2');
  });
});

describe('formatInputValue', () => {
  it('labels values', () => {
    expect(formatInputValue('EFFORT', 2)).toBe('2 person-weeks');
    expect(formatInputValue('CONFIDENCE', 50)).toBe('Low (50%)');
    expect(formatInputValue('REACH', null)).toBe('not set');
  });
});
