import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { RoadmapAddResult } from '../../src/roadmap/RoadmapAddResult';
import type { RoadmapAddResult as AddResult } from '../../src/api/roadmap';

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, search }: { children?: ReactNode; to: string; search?: { item: string; done: boolean; archived: boolean } }) => (
    <a href={search ? `${to}?item=${search.item}` : to} data-search={search ? JSON.stringify(search) : undefined}>
      {children}
    </a>
  ),
}));

let container: HTMLDivElement;
let root: Root;

const added = (n: number) => Array.from({ length: n }, (_, i) => ({ itemId: `a${i}`, jiraKey: `CNV-${i + 1}` }));
const dups = (n: number) => Array.from({ length: n }, (_, i) => ({ itemId: `d${i}`, jiraKey: `DUP-${i + 1}` }));

async function show(result: Partial<AddResult>, showRoadmapLink = false) {
  await act(async () => {
    root.render(
      <RoadmapAddResult result={{ added: [], alreadyPresent: [], notFound: [], ...result }} showRoadmapLink={showRoadmapLink} />,
    );
  });
}

describe('RoadmapAddResult', () => {
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

  it('reportsTheAddedCountAndTheUnscoredHint', async () => {
    await show({ added: added(3) });
    expect(container.textContent).toContain('Added 3 items to the roadmap.');
    expect(container.textContent).toContain('They have no scores yet. Set Reach, Impact, Confidence and Effort to rank them.');
  });

  it('usesTheSingularForOneItem', async () => {
    await show({ added: added(1) });
    expect(container.textContent).toContain('Added 1 item to the roadmap.');
  });

  it('namesADuplicateAndLinksToIt', async () => {
    await show({ added: added(1), alreadyPresent: [{ jiraKey: 'CNV-2', itemId: 'item-2' }] });
    expect(container.textContent).toContain('CNV-2 is already on the roadmap.');
    expect(container.querySelector('a')?.getAttribute('href')).toBe('/roadmap?item=item-2');
    expect(container.querySelector('a')?.textContent).toBe('View it');
    expect(container.querySelector('a')?.getAttribute('data-search')).toContain('"done":true,"archived":true');
  });

  it('namesFiveDuplicatesAndCountsTheRest', async () => {
    await show({ added: added(1), alreadyPresent: dups(7) });
    expect(container.textContent).toContain('DUP-5 is already on the roadmap.');
    expect(container.textContent).not.toContain('DUP-6 is already');
    expect(container.textContent).toContain('and 2 more already on the roadmap.');
  });

  it('saysNothingWasAddedForOneDuplicate', async () => {
    await show({ alreadyPresent: [{ jiraKey: 'CNV-2', itemId: 'item-2' }] });
    expect(container.textContent).toContain('Nothing added. CNV-2 is already on the roadmap.');
  });

  it('saysNothingWasAddedForSeveralDuplicates', async () => {
    await show({ alreadyPresent: dups(3) });
    expect(container.textContent).toContain('Nothing added. All 3 are already on the roadmap.');
  });

  it('namesKeysThatWereNotFound', async () => {
    await show({ notFound: ['CNV-9'] });
    expect(container.textContent).toContain('CNV-9 was not found.');
  });

  it('showsTheRoadmapLinkOnlyWhenAskedAndSomethingWasAdded', async () => {
    await show({ added: added(1) }, true);
    expect([...container.querySelectorAll('a')].some((a) => a.textContent === 'Go to Roadmap')).toBe(true);
    await show({ alreadyPresent: dups(1) }, true);
    expect([...container.querySelectorAll('a')].some((a) => a.textContent === 'Go to Roadmap')).toBe(false);
  });
});
