import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { FilterTile, HelpToggle } from '../../src/components/DashboardTiles';

let container: HTMLDivElement;
let root: Root;

async function render(node: React.ReactNode) {
  await act(async () => {
    root.render(node);
  });
}

function click(el: Element) {
  return act(async () => {
    el.dispatchEvent(new MouseEvent('click', { bubbles: true }));
  });
}

describe('DashboardTiles', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });
  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });
  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
  });

  const tile = (props: Partial<React.ComponentProps<typeof FilterTile>> = {}) => (
    <FilterTile
      id="t"
      caption="Releases missing documents"
      figure={3}
      unit="releases"
      pressed={false}
      onPress={() => undefined}
      help="Help copy"
      {...props}
    />
  );

  it('FilterTile is a pressable button with figure, caption and unit', async () => {
    const onPress = vi.fn();
    await render(tile({ onPress, pressed: true }));
    const button = container.querySelector('button#t')!;
    expect(button.getAttribute('aria-pressed')).toBe('true');
    expect(button.textContent).toContain('3');
    expect(button.textContent).toContain('Releases missing documents');
    expect(button.textContent).toContain('releases');
    await click(button);
    expect(onPress).toHaveBeenCalledTimes(1);
  });

  it('a disabled FilterTile points at its note and ignores clicks', async () => {
    const onPress = vi.fn();
    await render(tile({ onPress, disabledNote: 'Document status unavailable' }));
    const button = container.querySelector('button#t')!;
    expect(button.getAttribute('aria-disabled')).toBe('true');
    expect(button.getAttribute('aria-describedby')).toBe('t-note');
    expect(container.querySelector('#t-note')?.textContent).toBe('Document status unavailable');
    await click(button);
    expect(onPress).not.toHaveBeenCalled();
  });

  it('HelpToggle reveals the help text and never presses the tile', async () => {
    const onPress = vi.fn();
    await render(tile({ onPress }));
    const help = container.querySelector('button[aria-label="What does this count?"]')!;
    expect(help.getAttribute('aria-expanded')).toBe('false');
    expect(container.querySelector('#t-help')).toBeNull();
    await click(help);
    expect(help.getAttribute('aria-expanded')).toBe('true');
    expect(container.querySelector('#t-help')?.textContent).toBe('Help copy');
    expect(onPress).not.toHaveBeenCalled();
  });

  it('HelpToggle stands alone', async () => {
    await render(<HelpToggle id="h" text="Alone" />);
    await click(container.querySelector('button')!);
    expect(container.querySelector('#h')?.textContent).toBe('Alone');
  });
});
