import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ThemeProvider, useTheme } from '../../src/components/theme/ThemeProvider';

const KEY = 'productops-theme';

type Listener = (event: { matches: boolean }) => void;

let container: HTMLDivElement;
let root: Root;
let originalMatchMedia: typeof window.matchMedia;
let listeners: Set<Listener>;
let current: ReturnType<typeof useTheme>;

function installMatchMedia(matches: boolean) {
  listeners = new Set();
  window.matchMedia = ((query: string) => ({
    matches,
    media: query,
    onchange: null,
    addEventListener: (_type: string, listener: Listener) => listeners.add(listener),
    removeEventListener: (_type: string, listener: Listener) => listeners.delete(listener),
    addListener() {},
    removeListener() {},
    dispatchEvent: () => false,
  })) as unknown as typeof window.matchMedia;
}

function fireSchemeChange(matches: boolean) {
  act(() => listeners.forEach((listener) => listener({ matches })));
}

function Probe() {
  current = useTheme();
  return null;
}

function renderWithProvider() {
  act(() =>
    root.render(
      <ThemeProvider>
        <Probe />
      </ThemeProvider>,
    ),
  );
}

const html = () => document.documentElement;

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});

beforeEach(() => {
  originalMatchMedia = window.matchMedia;
  installMatchMedia(false);
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  window.matchMedia = originalMatchMedia;
  vi.restoreAllMocks();
  localStorage.clear();
  html().classList.remove('dark');
  html().style.colorScheme = '';
});

describe('ThemeProvider', () => {
  it('applies a stored dark choice', () => {
    localStorage.setItem(KEY, 'dark');
    renderWithProvider();
    expect(current.theme).toBe('dark');
    expect(current.resolvedTheme).toBe('dark');
    expect(html().classList.contains('dark')).toBe(true);
    expect(html().style.colorScheme).toBe('dark');
  });

  it.each([['banana'], [null]])('treats stored %s as system and follows a light OS', (stored) => {
    if (stored) localStorage.setItem(KEY, stored);
    renderWithProvider();
    expect(current.theme).toBe('system');
    expect(current.resolvedTheme).toBe('light');
    expect(html().classList.contains('dark')).toBe(false);
    expect(html().style.colorScheme).toBe('light');
  });

  it('falls back to system when storage reads throw', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(() => renderWithProvider()).not.toThrow();
    expect(current.theme).toBe('system');
  });

  it('persists light and clears the key for system', () => {
    renderWithProvider();
    act(() => current.setTheme('light'));
    expect(localStorage.getItem(KEY)).toBe('light');
    expect(current.theme).toBe('light');
    act(() => current.setTheme('system'));
    expect(localStorage.getItem(KEY)).toBeNull();
  });

  it('does not throw when storage writes throw', () => {
    renderWithProvider();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('quota');
    });
    expect(() => act(() => current.setTheme('dark'))).not.toThrow();
    expect(html().classList.contains('dark')).toBe(true);
  });

  it('follows OS changes live only while on system', () => {
    renderWithProvider();
    expect(listeners.size).toBe(1);
    fireSchemeChange(true);
    expect(html().classList.contains('dark')).toBe(true);
    expect(current.resolvedTheme).toBe('dark');
    expect(html().style.colorScheme).toBe('dark');

    act(() => current.setTheme('light'));
    expect(html().classList.contains('dark')).toBe(false);
    expect(listeners.size).toBe(0);
    fireSchemeChange(true);
    expect(html().classList.contains('dark')).toBe(false);
  });

  it('resolves system to dark on a dark OS', () => {
    installMatchMedia(true);
    renderWithProvider();
    expect(current.theme).toBe('system');
    expect(current.resolvedTheme).toBe('dark');
    expect(html().classList.contains('dark')).toBe(true);
  });
});

describe('useTheme without a provider', () => {
  it('returns system with a no-op setter', () => {
    act(() => root.render(<Probe />));
    expect(current.theme).toBe('system');
    expect(current.resolvedTheme).toBe('light');
    expect(() => current.setTheme('dark')).not.toThrow();
    expect(html().classList.contains('dark')).toBe(false);
  });
});
