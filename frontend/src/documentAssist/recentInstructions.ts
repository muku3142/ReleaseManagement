import { useSyncExternalStore } from 'react';

// In memory only (D-14-09, D-13-12): instruction text may name tickets or customers, so it is never
// written to web storage.
export const RECENT_MAX = 5;

let recent: readonly string[] = [];
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((l) => l());
}

export function addRecentInstruction(text: string) {
  const trimmed = text.trim();
  if (trimmed === '') return;
  recent = [trimmed, ...recent.filter((r) => r !== trimmed)].slice(0, RECENT_MAX);
  emit();
}

export function resetRecentInstructions() {
  recent = [];
  emit();
}

export function useRecentInstructions(): readonly string[] {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => recent,
    () => recent,
  );
}
