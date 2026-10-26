import type { TransformErrorOutcome } from '../api/documentAssist';

export type IncompleteCause = 'length' | 'deadline' | 'failed' | 'stopped';

// A stopped Clean up/Custom run whose unprocessed sections the server restored; it can be saved.
export type PartialCause = 'deadline' | 'failed';

export type ResultState =
  | { phase: 'waiting' }
  | { phase: 'streaming'; text: string; filled: boolean }
  | {
      phase: 'complete';
      output: string;
      buffer: string;
      edited: boolean;
      saveError: string | null;
      partial: PartialCause | null;
    }
  | { phase: 'incomplete'; cause: IncompleteCause; text: string }
  | { phase: 'error'; outcome: TransformErrorOutcome; retryAfterSeconds: number | null }
  | { phase: 'saving'; output: string; buffer: string; edited: boolean; partial: PartialCause | null };

export type ResultAction =
  | { type: 'token'; text: string }
  | { type: 'remainder'; keep: number; text: string }
  | { type: 'done'; complete: boolean; truncated: boolean }
  | { type: 'error'; outcome: TransformErrorOutcome; retryAfterSeconds: number | null }
  | { type: 'ended' }
  | { type: 'stop' }
  | { type: 'edit'; buffer: string }
  | { type: 'save-start' }
  | { type: 'save-failed'; message: string }
  | { type: 'retry' };

export const initialResultState: ResultState = { phase: 'waiting' };

function textOf(state: ResultState): string | null {
  if (state.phase === 'streaming') return state.text;
  return null;
}

function failWith(state: ResultState, outcome: TransformErrorOutcome, retryAfterSeconds: number | null): ResultState {
  const text = textOf(state);
  if (text === null) return { phase: 'error', outcome, retryAfterSeconds };
  if (state.phase === 'streaming' && state.filled) {
    return {
      phase: 'complete',
      output: text,
      buffer: text,
      edited: false,
      saveError: null,
      partial: outcome === 'DEADLINE' ? 'deadline' : 'failed',
    };
  }
  return { phase: 'incomplete', cause: outcome === 'DEADLINE' ? 'deadline' : 'failed', text };
}

export function resultReducer(state: ResultState, action: ResultAction): ResultState {
  switch (action.type) {
    case 'token':
      if (state.phase === 'waiting') return { phase: 'streaming', text: action.text, filled: false };
      if (state.phase === 'streaming') return { ...state, text: state.text + action.text };
      return state;
    case 'remainder':
      if (state.phase !== 'waiting' && state.phase !== 'streaming') return state;
      return { phase: 'streaming', text: (textOf(state) ?? '').slice(0, action.keep) + action.text, filled: true };
    case 'done': {
      if (state.phase !== 'waiting' && state.phase !== 'streaming') return state;
      const text = textOf(state) ?? '';
      if (action.complete && !action.truncated) {
        return { phase: 'complete', output: text, buffer: text, edited: false, saveError: null, partial: null };
      }
      return { phase: 'incomplete', cause: 'length', text };
    }
    case 'error':
      if (state.phase !== 'waiting' && state.phase !== 'streaming') return state;
      return failWith(state, action.outcome, action.retryAfterSeconds);
    case 'ended':
      if (state.phase !== 'waiting' && state.phase !== 'streaming') return state;
      return failWith(state, 'FAILED', null);
    case 'stop':
      if (state.phase !== 'waiting' && state.phase !== 'streaming') return state;
      return { phase: 'incomplete', cause: 'stopped', text: textOf(state) ?? '' };
    case 'edit':
      if (state.phase !== 'complete') return state;
      return { ...state, buffer: action.buffer, edited: action.buffer !== state.output };
    case 'save-start':
      if (state.phase !== 'complete') return state;
      return {
        phase: 'saving',
        output: state.output,
        buffer: state.buffer,
        edited: state.edited,
        partial: state.partial,
      };
    case 'save-failed':
      if (state.phase !== 'saving') return state;
      return { ...state, phase: 'complete', saveError: action.message };
    case 'retry':
      return initialResultState;
  }
}

export function canSave(state: ResultState): boolean {
  return state.phase === 'complete' && state.buffer.trim() !== '';
}
