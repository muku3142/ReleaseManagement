import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { ItemChurn } from '../../src/components/ItemChurn';
import type { ItemChurn as ItemChurnFigures } from '../../src/api/releases';

function churn(overrides: Partial<ItemChurnFigures> = {}): ItemChurnFigures {
  return { commitsToDone: 3, postDoneCommits: 1, reopens: 2, keyTaggedOnly: false, outlier: false, ...overrides };
}

let container: HTMLDivElement;
let root: Root;

async function render(value: ItemChurnFigures | null) {
  await act(async () => root.render(<ItemChurn churn={value} />));
}

describe('ItemChurn', () => {
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

  it('renders No data, never 0, when there is no churn', async () => {
    await render(null);
    expect(container.textContent).toBe('No data');
    expect(container.querySelector('span')?.className).toContain('italic');
  });

  it('renders all three figures', async () => {
    await render(churn());
    expect(container.textContent).toBe('3 to Done · 2 reopens · 1 after Done');
  });

  it('says so when commits are unknown and singularises one reopen', async () => {
    await render(churn({ commitsToDone: null, postDoneCommits: null, reopens: 1 }));
    expect(container.textContent).toBe('No commit data · 1 reopen');
  });

  it('says so when reopens are unknown', async () => {
    await render(churn({ reopens: null }));
    expect(container.textContent).toBe('3 to Done · reopens: no data · 1 after Done');
  });

  it('labels key-tagged-only commit counts', async () => {
    await render(churn({ keyTaggedOnly: true }));
    expect(container.textContent).toContain('Key-tagged commits only');
  });

  it('marks outliers with text', async () => {
    await render(churn({ outlier: true }));
    expect(container.textContent).toContain('Outlier');
  });

  it('explains what the figures are on hover', async () => {
    await render(churn());
    expect(container.querySelector('span')?.getAttribute('title')).toContain('not Jira cycle time');
  });
});
