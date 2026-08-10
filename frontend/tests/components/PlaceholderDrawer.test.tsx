import { StrictMode, act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { PlaceholderDrawer } from '../../src/components/PlaceholderDrawer';
import type { PlaceholderEntry } from '../../src/api/templates';

const ENTRIES: PlaceholderEntry[] = [
  { scope: 'ROOT', token: 'release.name', kind: 'VARIABLE', description: "The release's name.", tags: ['Chrome + Body'] },
  {
    scope: 'ROOT',
    token: 'items',
    kind: 'SECTION',
    description: 'Loop over every release item, ticket-backed and manual, in one unified collection.',
    tags: ['Body only'],
  },
  {
    scope: 'ITEM',
    token: 'key',
    kind: 'VARIABLE',
    description: "The ticket's key. Empty for manual items.",
    tags: ['Body only', 'Ticket only'],
  },
  {
    scope: 'ITEM',
    token: 'category',
    kind: 'VARIABLE',
    description: "The manual item's category. Empty for ticket items.",
    tags: ['Body only', 'Manual only'],
  },
  {
    scope: 'ITEM',
    token: 'commits',
    kind: 'SECTION',
    description: 'Linked code detail. Always empty until Phase 6.',
    tags: ['Body only', 'Ticket only', 'Always empty until Phase 6'],
  },
  { scope: 'GROUP', token: 'groupName', kind: 'VARIABLE', description: "The group's label.", tags: ['Body only'] },
];

let container: HTMLDivElement;
let root: Root;

async function renderDrawer(props: {
  open?: boolean;
  loading?: boolean;
  onInsert?: (token: string) => void;
  onClose?: () => void;
}) {
  await act(async () => {
    root.render(
      <StrictMode>
        <PlaceholderDrawer
          open={props.open ?? true}
          entries={props.loading ? undefined : ENTRIES}
          onInsert={props.onInsert ?? (() => {})}
          onClose={props.onClose ?? (() => {})}
        />
      </StrictMode>,
    );
  });
}

function rowButton(token: string): HTMLButtonElement {
  const span = [...container.querySelectorAll('.font-mono')].find((s) => s.textContent === token);
  expect(span, `no row for token "${token}"`).toBeDefined();
  const button = span!.closest('button');
  expect(button).not.toBeNull();
  return button as HTMLButtonElement;
}

describe('PlaceholderDrawer', () => {
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

  it('renders all three section headings', async () => {
    await renderDrawer({});
    expect(container.textContent).toContain('Release fields');
    expect(container.textContent).toContain('Item fields');
    expect(container.textContent).toContain('Group fields');
  });

  it('places release.name under Release fields and groupName under Group fields', async () => {
    await renderDrawer({});
    const releaseHeading = [...container.querySelectorAll('button')].find((b) => b.textContent?.includes('Release fields'));
    const groupHeading = [...container.querySelectorAll('button')].find((b) => b.textContent?.includes('Group fields'));
    const releaseNameRow = rowButton('release.name');
    const groupNameRow = rowButton('groupName');
    expect(releaseHeading!.compareDocumentPosition(releaseNameRow) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(groupHeading!.compareDocumentPosition(groupNameRow) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('renders every entry as a real <button> element - the UX-01 keyboard equivalent', async () => {
    await renderDrawer({});
    for (const entry of ENTRIES) {
      expect(rowButton(entry.token).tagName).toBe('BUTTON');
    }
  });

  it('tags the commits row amber and the key row\'s Ticket only tag neutral gray', async () => {
    await renderDrawer({});
    const commitsRow = rowButton('commits');
    const amberTag = [...commitsRow.querySelectorAll('span')].find((s) => s.textContent === 'Always empty until Phase 6');
    expect(amberTag?.className).toContain('bg-amber-100');

    const keyRow = rowButton('key');
    const grayTag = [...keyRow.querySelectorAll('span')].find((s) => s.textContent === 'Ticket only');
    expect(grayTag?.className).toContain('bg-gray-100');
    expect(grayTag?.className).not.toContain('amber');
  });

  it("describes key as empty for manual items and category as empty for ticket items - copy comes from the backend constant", async () => {
    await renderDrawer({});
    expect(rowButton('key').textContent).toContain('Empty for manual items');
    expect(rowButton('category').textContent).toContain('Empty for ticket items');
  });

  it('calls onInsert with the exact token when a row is clicked', async () => {
    const onInsert = vi.fn();
    await renderDrawer({ onInsert });
    await act(async () => rowButton('release.name').click());
    expect(onInsert).toHaveBeenCalledWith('release.name');
  });

  it('calls onInsert when Enter is dispatched on a focused row', async () => {
    const onInsert = vi.fn();
    await renderDrawer({ onInsert });
    const row = rowButton('release.name');
    row.focus();
    await act(async () => {
      row.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    });
    expect(onInsert).toHaveBeenCalledWith('release.name');
  });

  it('announces the insertion via the aria-live region', async () => {
    await renderDrawer({});
    await act(async () => rowButton('release.name').click());
    const live = container.querySelector('[aria-live="polite"]');
    expect(live?.textContent).toBe('Inserted {{release.name}}');
  });

  it('filters to only the commits row when typing "commit"', async () => {
    await renderDrawer({});
    const filterInput = container.querySelector<HTMLInputElement>('input[aria-label="Filter placeholders"]')!;

    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
    await act(async () => {
      setter.call(filterInput, 'commit');
      filterInput.dispatchEvent(new Event('input', { bubbles: true }));
    });

    const tokens = [...container.querySelectorAll('.font-mono')].map((s) => s.textContent);
    expect(tokens).toEqual(['commits']);
  });

  it('closes on Escape, on outside mousedown, but not on inside mousedown', async () => {
    const onCloseEscape = vi.fn();
    await renderDrawer({ onClose: onCloseEscape });
    await act(async () => {
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    });
    expect(onCloseEscape).toHaveBeenCalledTimes(1);

    const onCloseOutside = vi.fn();
    await renderDrawer({ onClose: onCloseOutside });
    const outside = document.createElement('div');
    document.body.appendChild(outside);
    await act(async () => {
      outside.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    });
    expect(onCloseOutside).toHaveBeenCalledTimes(1);
    outside.remove();

    const onCloseInside = vi.fn();
    await renderDrawer({ onClose: onCloseInside });
    const region = container.querySelector('[role="region"]')!;
    await act(async () => {
      region.dispatchEvent(new MouseEvent('mousedown', { bubbles: true }));
    });
    expect(onCloseInside).not.toHaveBeenCalled();
  });

  it('renders no <dialog> and no backdrop element - it is non-modal', async () => {
    await renderDrawer({});
    expect(container.querySelector('dialog')).toBeNull();
    expect(container.querySelector('[class*="backdrop"]')).toBeNull();
  });

  it('shows "Loading placeholders…" when entries is undefined', async () => {
    await renderDrawer({ loading: true });
    expect(container.textContent).toContain('Loading placeholders…');
  });
});
