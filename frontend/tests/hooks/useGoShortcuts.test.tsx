import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { useGoShortcuts } from '../../src/hooks/useGoShortcuts';

const navigate = vi.fn();

vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
}));

function Harness() {
  useGoShortcuts();
  return null;
}

let container: HTMLDivElement;
let root: Root;

function press(key: string, target: EventTarget = document.body, init: KeyboardEventInit = {}) {
  target.dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, ...init }));
}

describe('useGoShortcuts', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(async () => {
    navigate.mockReset();
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    await act(async () => root.render(<Harness />));
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    document.body.innerHTML = '';
    vi.useRealTimers();
  });

  it.each([
    ['d', '/'],
    ['t', '/tickets'],
    ['r', '/releases'],
    ['m', '/roadmap'],
    ['b', '/briefings'],
    ['p', '/portfolio'],
    ['s', '/settings'],
  ])('g then %s navigates to %s', (key, to) => {
    press('g');
    press(key);
    expect(navigate).toHaveBeenCalledWith({ to });
  });

  it('ignores a second key that arrives after the 1500 ms window', () => {
    vi.useFakeTimers();
    press('g');
    vi.advanceTimersByTime(1600);
    press('r');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('disarms on a non-matching key', () => {
    press('g');
    press('x');
    press('r');
    expect(navigate).not.toHaveBeenCalled();
  });

  it.each(['input', 'textarea', 'select'])('does nothing while typing in a %s', (tag) => {
    const field = document.createElement(tag);
    document.body.appendChild(field);
    press('g', field);
    press('r', field);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does nothing inside a contenteditable element', () => {
    const editor = document.createElement('div');
    editor.setAttribute('contenteditable', 'true');
    const inner = document.createElement('span');
    editor.appendChild(inner);
    document.body.appendChild(editor);
    press('g', inner);
    press('r', inner);
    expect(navigate).not.toHaveBeenCalled();
  });

  it.each(['ctrlKey', 'metaKey', 'altKey'])('does nothing with %s held', (modifier) => {
    press('g', document.body, { [modifier]: true });
    press('r');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does nothing while a dialog is open', () => {
    const dialog = document.createElement('div');
    dialog.setAttribute('role', 'dialog');
    dialog.setAttribute('data-state', 'open');
    document.body.appendChild(dialog);
    press('g');
    press('r');
    expect(navigate).not.toHaveBeenCalled();
  });
});
