import { describe, expect, it } from 'vitest';
import {
  canSave,
  initialResultState,
  resultReducer,
  type ResultAction,
  type ResultState,
} from '../../src/documentAssist/resultState';

const run = (actions: ResultAction[], from: ResultState = initialResultState) =>
  actions.reduce(resultReducer, from);

const token = (text: string): ResultAction => ({ type: 'token', text });
const complete = run([token('# Hi'), { type: 'done', complete: true, truncated: false }]);

describe('resultReducer', () => {
  it('starts waiting, streams, appends tokens', () => {
    expect(initialResultState).toEqual({ phase: 'waiting' });
    expect(run([token('a'), token('b')])).toEqual({ phase: 'streaming', text: 'ab', filled: false });
  });

  it('completes with the output as the buffer', () => {
    expect(complete).toEqual({
      phase: 'complete',
      output: '# Hi',
      buffer: '# Hi',
      edited: false,
      saveError: null,
      partial: null,
    });
  });

  it('treats a non-complete or truncated done as incomplete/length', () => {
    expect(run([token('x'), { type: 'done', complete: false, truncated: false }])).toEqual({
      phase: 'incomplete',
      cause: 'length',
      text: 'x',
    });
    expect(run([token('x'), { type: 'done', complete: true, truncated: true }])).toMatchObject({
      phase: 'incomplete',
      cause: 'length',
    });
  });

  it('maps errors with text to incomplete causes', () => {
    const err = (outcome: 'DEADLINE' | 'FAILED'): ResultAction => ({ type: 'error', outcome, retryAfterSeconds: null });
    expect(run([token('x'), err('DEADLINE')])).toEqual({ phase: 'incomplete', cause: 'deadline', text: 'x' });
    expect(run([token('x'), err('FAILED')])).toEqual({ phase: 'incomplete', cause: 'failed', text: 'x' });
  });

  it('maps errors without text to the error state', () => {
    expect(run([{ type: 'error', outcome: 'OFFLINE', retryAfterSeconds: null }])).toEqual({
      phase: 'error',
      outcome: 'OFFLINE',
      retryAfterSeconds: null,
    });
    expect(run([{ type: 'error', outcome: 'BUSY', retryAfterSeconds: 5 }])).toMatchObject({ retryAfterSeconds: 5 });
    expect(run([{ type: 'error', outcome: 'MODEL_MISSING', retryAfterSeconds: null }])).toMatchObject({
      outcome: 'MODEL_MISSING',
    });
    expect(run([{ type: 'error', outcome: 'FAILED', retryAfterSeconds: null }])).toMatchObject({ phase: 'error' });
  });

  it('treats a stream that ends without done as failed', () => {
    expect(run([token('x'), { type: 'ended' }])).toEqual({ phase: 'incomplete', cause: 'failed', text: 'x' });
    expect(run([{ type: 'ended' }])).toEqual({ phase: 'error', outcome: 'FAILED', retryAfterSeconds: null });
  });

  it('keeps text on stop and ignores later events', () => {
    const stopped = run([token('x'), { type: 'stop' }]);
    expect(stopped).toEqual({ phase: 'incomplete', cause: 'stopped', text: 'x' });
    expect(run([{ type: 'ended' }, token('y')], stopped)).toBe(stopped);
  });

  it('edits only in complete', () => {
    const edited = run([{ type: 'edit', buffer: 'changed' }], complete);
    expect(edited).toMatchObject({ buffer: 'changed', output: '# Hi', edited: true });
    expect(run([{ type: 'edit', buffer: '# Hi' }], edited)).toMatchObject({ edited: false });
    const streaming = run([token('x')]);
    expect(run([{ type: 'edit', buffer: 'z' }], streaming)).toBe(streaming);
  });

  it('allows Save only for a complete, non-blank result', () => {
    expect(canSave(complete)).toBe(true);
    expect(canSave(run([{ type: 'edit', buffer: '  ' }], complete))).toBe(false);
    for (const cause of ['length', 'deadline', 'failed', 'stopped'] as const) {
      expect(canSave({ phase: 'incomplete', cause, text: 'x' })).toBe(false);
    }
    expect(canSave(run([token('x')]))).toBe(false);
  });

  it('saves and recovers from a save failure with the buffer kept', () => {
    const edited = run([{ type: 'edit', buffer: 'mine' }], complete);
    const saving = run([{ type: 'save-start' }], edited);
    expect(saving.phase).toBe('saving');
    expect(canSave(saving)).toBe(false);
    expect(run([{ type: 'save-failed', message: 'nope' }], saving)).toEqual({
      phase: 'complete',
      output: '# Hi',
      buffer: 'mine',
      edited: true,
      saveError: 'nope',
      partial: null,
    });
  });

  it('a remainder replaces everything after keep and marks the stream filled', () => {
    const filled = run([token('AB-partial'), { type: 'remainder', keep: 2, text: 'XYZ' }]);
    expect(filled).toEqual({ phase: 'streaming', text: 'ABXYZ', filled: true });
    expect(run([token('!')], filled)).toEqual({ phase: 'streaming', text: 'ABXYZ!', filled: true });
  });

  it('a filled stream that stops becomes a saveable complete result', () => {
    const filled = run([token('AB-p'), { type: 'remainder', keep: 2, text: 'XYZ' }]);
    const deadline = run([{ type: 'error', outcome: 'DEADLINE', retryAfterSeconds: null }], filled);
    expect(deadline).toEqual({
      phase: 'complete',
      output: 'ABXYZ',
      buffer: 'ABXYZ',
      edited: false,
      saveError: null,
      partial: 'deadline',
    });
    expect(canSave(deadline)).toBe(true);
    for (const action of [
      { type: 'error', outcome: 'OFFLINE', retryAfterSeconds: null },
      { type: 'error', outcome: 'FAILED', retryAfterSeconds: null },
      { type: 'error', outcome: 'BUSY', retryAfterSeconds: 5 },
      { type: 'ended' },
    ] as ResultAction[]) {
      expect(run([action], filled)).toMatchObject({ phase: 'complete', partial: 'failed' });
    }
    expect(run([{ type: 'save-start' }], deadline)).toMatchObject({ phase: 'saving', partial: 'deadline' });
  });

  it('an unfilled stop, done and a user stop keep their meaning', () => {
    expect(run([token('x'), { type: 'error', outcome: 'DEADLINE', retryAfterSeconds: null }])).toEqual({
      phase: 'incomplete',
      cause: 'deadline',
      text: 'x',
    });
    const filled = run([token('AB'), { type: 'remainder', keep: 2, text: 'XYZ' }]);
    expect(run([{ type: 'done', complete: true, truncated: false }], filled)).toMatchObject({
      phase: 'complete',
      partial: null,
    });
    expect(run([{ type: 'stop' }], filled)).toEqual({ phase: 'incomplete', cause: 'stopped', text: 'ABXYZ' });
  });

  it('retry clears to waiting', () => {
    expect(run([{ type: 'retry' }], complete)).toEqual({ phase: 'waiting' });
  });
});
