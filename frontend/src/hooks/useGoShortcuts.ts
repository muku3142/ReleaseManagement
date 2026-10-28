import { useEffect } from 'react';
import { useNavigate } from '@tanstack/react-router';
import { NAV_ITEMS } from '@/components/layout/navItems';

const SEQUENCE_WINDOW_MS = 1500;
const TYPING_TARGETS = 'input, textarea, select, [contenteditable]';

// Two-key sequences only: single-letter shortcuts would steal keys from the roadmap's in-cell editors.
export function useGoShortcuts(): void {
  const navigate = useNavigate();

  useEffect(() => {
    let armedAt: number | null = null;

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.ctrlKey || event.metaKey || event.altKey) return;
      if (event.target instanceof Element && event.target.closest(TYPING_TARGETS)) return;
      if (document.querySelector('[role="dialog"][data-state="open"]')) return;

      if (armedAt !== null && Date.now() - armedAt <= SEQUENCE_WINDOW_MS) {
        armedAt = null;
        const target = NAV_ITEMS.find((item) => item.goKey === event.key);
        if (target) navigate({ to: target.to });
        return;
      }
      armedAt = event.key === 'g' ? Date.now() : null;
    };

    document.addEventListener('keydown', onKeyDown);
    return () => document.removeEventListener('keydown', onKeyDown);
  }, [navigate]);
}
