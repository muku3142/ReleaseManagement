import { describe, expect, it } from 'vitest';
import { safeRedirectTarget } from './safeRedirect';

describe('safeRedirectTarget', () => {
  it('allows same-origin relative paths', () => {
    expect(safeRedirectTarget('/settings')).toBe('/settings');
    expect(safeRedirectTarget('/settings?tab=jira')).toBe('/settings?tab=jira');
    expect(safeRedirectTarget('/')).toBe('/');
  });

  it('rejects absolute URLs to another origin', () => {
    expect(safeRedirectTarget('https://evil.example.com')).toBe('/');
    expect(safeRedirectTarget('http://evil.example.com/steal')).toBe('/');
  });

  it('rejects protocol-relative and backslash-normalised off-site targets', () => {
    // browsers resolve both of these against the current scheme, landing off-origin
    expect(safeRedirectTarget('//evil.example.com')).toBe('/');
    expect(safeRedirectTarget('/\\evil.example.com')).toBe('/');
  });

  it('rejects non-path values and absent input', () => {
    expect(safeRedirectTarget('javascript:alert(1)')).toBe('/');
    expect(safeRedirectTarget('settings')).toBe('/');
    expect(safeRedirectTarget(null)).toBe('/');
    expect(safeRedirectTarget(undefined)).toBe('/');
    expect(safeRedirectTarget('')).toBe('/');
  });
});
