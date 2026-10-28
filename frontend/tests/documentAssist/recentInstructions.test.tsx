import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  addRecentInstruction,
  resetRecentInstructions,
  useRecentInstructions,
} from '../../src/documentAssist/recentInstructions';

function Probe() {
  return <p data-testid="recent">{JSON.stringify(useRecentInstructions())}</p>;
}

describe('recentInstructions', () => {
  let container: HTMLDivElement;
  let root: Root;

  const shown = () => JSON.parse(container.querySelector('p')!.textContent!) as string[];

  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    act(() => root.render(<Probe />));
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
    act(() => resetRecentInstructions());
    vi.restoreAllMocks();
  });

  it('de-duplicates and puts the newest first', () => {
    act(() => {
      addRecentInstruction('a');
      addRecentInstruction('b');
      addRecentInstruction('a');
    });
    expect(shown()).toEqual(['a', 'b']);
  });

  it('keeps only the last five', () => {
    act(() => {
      ['1', '2', '3', '4', '5', '6'].forEach(addRecentInstruction);
    });
    expect(shown()).toEqual(['6', '5', '4', '3', '2']);
  });

  it('ignores blank text', () => {
    act(() => addRecentInstruction('   '));
    expect(shown()).toEqual([]);
  });

  it('never touches web storage', () => {
    const set = vi.spyOn(Storage.prototype, 'setItem');
    const get = vi.spyOn(Storage.prototype, 'getItem');
    act(() => addRecentInstruction('secret'));
    expect(set).not.toHaveBeenCalled();
    expect(get).not.toHaveBeenCalled();
  });
});
