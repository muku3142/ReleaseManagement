import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProductFilter } from '../../src/components/ProductFilter';
import type { Product } from '../../src/api/products';

const product = (id: string, name: string, archived = false): Product => ({
  id,
  name,
  archived,
  clientCount: 0,
  releaseCount: 0,
  jiraProjectKeys: [],
});
const PRODUCTS = [product('b', 'beta'), product('a', 'Alpha'), product('c', 'Core'), product('z', 'Old', true)];

let container: HTMLDivElement;
let root: Root;

async function render(selected: string[], onChange = vi.fn(), products = PRODUCTS) {
  await act(async () => {
    root.render(<ProductFilter products={products} selected={selected} onChange={onChange} />);
  });
  return onChange;
}

const summary = () => container.querySelector('summary')!;
const details = () => container.querySelector('details')!;

async function openPopover() {
  await act(async () => {
    details().open = true;
    details().dispatchEvent(new Event('toggle'));
  });
}

describe('ProductFilter', () => {
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

  it('summarises the selection', async () => {
    await render([]);
    expect(summary().textContent).toBe('All products');
    await render(['a']);
    expect(summary().textContent).toBe('Alpha');
    await render(['a', 'c']);
    expect(summary().textContent).toBe('Alpha, Core');
    await render(['a', 'b', 'c']);
    expect(summary().textContent).toBe('3 products');
    expect(summary().getAttribute('title')).toBe('Alpha, beta, Core');
  });

  it('ticking and unticking report the new id list', async () => {
    const onChange = await render(['a']);
    const box = (name: string) =>
      [...container.querySelectorAll('label')].find((l) => l.textContent === name)!.querySelector('input')!;
    await act(async () => box('Core').click());
    expect(onChange).toHaveBeenLastCalledWith(['a', 'c']);
    await act(async () => box('Alpha').click());
    expect(onChange).toHaveBeenLastCalledWith([]);
  });

  it('Clear products clears and only shows with a selection', async () => {
    await render([]);
    expect(container.textContent).not.toContain('Clear products');
    const onChange = await render(['a']);
    const clear = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Clear products')!;
    await act(async () => clear.click());
    expect(onChange).toHaveBeenCalledWith([]);
  });

  it('lists archived products only when selected, sorted case-insensitively', async () => {
    await render([]);
    const labels = () => [...container.querySelectorAll('label')].map((l) => l.textContent);
    expect(labels()).toEqual(['Alpha', 'beta', 'Core']);
    await render(['z']);
    expect(labels()).toEqual(['Alpha', 'beta', 'Core', 'Old (archived)']);
  });

  it('Escape closes the popover and focuses the summary', async () => {
    await render([]);
    await openPopover();
    await act(async () => {
      container.querySelector('input')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    });
    expect(details().open).toBe(false);
    expect(document.activeElement).toBe(summary());
  });

  it('a mousedown outside closes the popover', async () => {
    await render([]);
    await openPopover();
    await act(async () => {
      document.body.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    });
    expect(details().open).toBe(false);
  });

  it('renders nothing without products', async () => {
    await render([], vi.fn(), []);
    expect(container.innerHTML).toBe('');
  });
});
