import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';

export type Theme = 'light' | 'dark' | 'system';
type ResolvedTheme = 'light' | 'dark';

interface ThemeContextValue {
  theme: Theme;
  resolvedTheme: ResolvedTheme;
  setTheme: (theme: Theme) => void;
}

const STORAGE_KEY = 'productops-theme';
const DARK_QUERY = '(prefers-color-scheme: dark)';

// A default value rather than a throw, so components using the hook render in tests without a provider.
const ThemeContext = createContext<ThemeContextValue>({
  theme: 'system',
  resolvedTheme: 'light',
  setTheme: () => {},
});

function readStored(): Theme {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    return stored === 'light' || stored === 'dark' ? stored : 'system';
  } catch {
    return 'system';
  }
}

function persist(theme: Theme) {
  try {
    if (theme === 'system') localStorage.removeItem(STORAGE_KEY);
    else localStorage.setItem(STORAGE_KEY, theme);
  } catch {
    // Storage blocked (private mode, policy): the choice still applies for this session.
  }
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(readStored);
  const [resolvedTheme, setResolvedTheme] = useState<ResolvedTheme>('light');

  useEffect(() => {
    const apply = (dark: boolean) => {
      const root = document.documentElement;
      root.classList.toggle('dark', dark);
      root.style.colorScheme = dark ? 'dark' : 'light';
      setResolvedTheme(dark ? 'dark' : 'light');
    };
    const media = window.matchMedia(DARK_QUERY);
    apply(theme === 'dark' || (theme === 'system' && media.matches));
    if (theme !== 'system') return;
    const onChange = (event: { matches: boolean }) => apply(event.matches);
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, [theme]);

  const value = useMemo<ThemeContextValue>(
    () => ({
      theme,
      resolvedTheme,
      setTheme: (next) => {
        setThemeState(next);
        persist(next);
      },
    }),
    [theme, resolvedTheme],
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  return useContext(ThemeContext);
}
