import { useSyncExternalStore } from 'react';

// Dependency-free external store signalling that the stored Jira credential was
// rejected (D-04). Stores a boolean flag and nothing else - never the token, never
// the email, never a session value.
let invalid = false;
const listeners = new Set<() => void>();

function emit() {
  listeners.forEach((l) => l());
}

export function markCredentialInvalid() {
  if (!invalid) {
    invalid = true;
    emit();
  }
}

export function clearCredentialInvalid() {
  if (invalid) {
    invalid = false;
    emit();
  }
}

export function useCredentialInvalid() {
  return useSyncExternalStore(
    (l) => {
      listeners.add(l);
      return () => listeners.delete(l);
    },
    () => invalid,
    () => false,
  );
}
