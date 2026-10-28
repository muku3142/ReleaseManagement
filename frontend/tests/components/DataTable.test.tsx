import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { createColumnHelper } from '@tanstack/react-table';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { DataTable } from '@/components/data-table/DataTable';
import { DataTableColumnHeader } from '@/components/data-table/DataTableColumnHeader';
import type { DataTableFeatures } from '@/components/data-table/features';
import { usePrefersReducedMotion } from '@/hooks/usePrefersReducedMotion';

vi.mock('@/hooks/usePrefersReducedMotion', () => ({ usePrefersReducedMotion: vi.fn(() => false) }));

const reducedMotion = vi.mocked(usePrefersReducedMotion);

type Row = { id: string; name: string; status: string; items: number };

const rows: Row[] = [
  { id: '1', name: 'Bravo', status: 'Open', items: 3 },
  { id: '2', name: 'Alpha', status: 'Frozen', items: 7 },
  { id: '3', name: 'Charlie', status: 'Open', items: 1 },
];

const helper = createColumnHelper<DataTableFeatures, Row>();

const columns = helper.columns([
  helper.accessor('name', {
    header: ({ column }) => <DataTableColumnHeader column={column} title="Name" />,
    enableHiding: false,
  }),
  helper.accessor('status', {
    header: 'Status',
    enableSorting: false,
    filterFn: 'arrHas',
    meta: { label: 'Status' },
  }),
  helper.accessor('items', {
    header: ({ column }) => <DataTableColumnHeader column={column} title="Items" />,
    meta: { label: 'Items', headerClassName: 'text-right', cellClassName: 'text-right tabular-nums' },
  }),
]);

let container: HTMLDivElement;
let root: Root;

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});

beforeEach(() => {
  reducedMotion.mockReturnValue(false);
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  document.body.innerHTML = '';
});

function render(node: ReactNode) {
  act(() => root.render(node));
}

function renderTable(props: Partial<Parameters<typeof DataTable<Row>>[0]> = {}) {
  render(
    <DataTable<Row>
      columns={columns}
      data={rows}
      getRowId={(row) => row.id}
      caption="Test releases"
      noun="releases"
      {...props}
    />,
  );
}

function firstColumnTexts(): string[] {
  return Array.from(container.querySelectorAll('tbody tr')).map((tr) => tr.querySelector('td')?.textContent ?? '');
}

function headerTexts(): string[] {
  return Array.from(container.querySelectorAll('thead th')).map((th) => th.textContent ?? '');
}

function click(element: Element) {
  act(() => {
    (element as HTMLElement).click();
  });
}

function pointerDown(element: Element) {
  act(() => {
    element.dispatchEvent(new PointerEvent('pointerdown', { bubbles: true, cancelable: true, button: 0 }));
  });
}

function sortButton(title: string): HTMLButtonElement {
  const button = container.querySelector<HTMLButtonElement>(`button[aria-label="Sort by ${title}"]`);
  expect(button).not.toBeNull();
  return button!;
}

function buttonByText(text: string, scope: ParentNode = document.body): HTMLButtonElement | undefined {
  return Array.from(scope.querySelectorAll('button')).find((b) => b.textContent?.trim() === text);
}

describe('DataTable core', () => {
  it('labels a keyboard-scrollable region with the sr-only caption', () => {
    renderTable();
    const caption = container.querySelector('caption')!;
    expect(caption.textContent).toBe('Test releases');
    expect(caption.classList).toContain('sr-only');
    const region = container.querySelector('[role="region"]')!;
    expect(region.getAttribute('aria-labelledby')).toBe(caption.id);
    expect(region.getAttribute('tabindex')).toBe('0');
    expect(region.classList).toContain('max-h-[70vh]');
    expect(region.querySelector('table')).not.toBeNull();
  });

  it('renders sticky column headers with sort buttons', () => {
    renderTable();
    const ths = Array.from(container.querySelectorAll('thead th'));
    expect(ths).toHaveLength(3);
    for (const th of ths) {
      expect(th.getAttribute('scope')).toBe('col');
      expect(th.classList).toContain('sticky');
      expect(th.classList).toContain('top-0');
      expect(th.classList).toContain('bg-card');
    }
    expect(sortButton('Name')).toBeTruthy();
    expect(ths[2].classList).toContain('text-right');
  });

  it('keeps server order and aria-sort none until sorted', () => {
    renderTable();
    expect(firstColumnTexts()).toEqual(['Bravo', 'Alpha', 'Charlie']);
    const ths = container.querySelectorAll('thead th');
    expect(ths[0].getAttribute('aria-sort')).toBe('none');
    expect(ths[1].hasAttribute('aria-sort')).toBe(false);
    expect(ths[2].getAttribute('aria-sort')).toBe('none');
  });

  it('cycles ascending, descending and unsorted', () => {
    renderTable();
    const nameTh = () => container.querySelectorAll('thead th')[0];

    click(sortButton('Name'));
    expect(firstColumnTexts()).toEqual(['Alpha', 'Bravo', 'Charlie']);
    expect(nameTh().getAttribute('aria-sort')).toBe('ascending');

    click(sortButton('Name'));
    expect(firstColumnTexts()).toEqual(['Charlie', 'Bravo', 'Alpha']);
    expect(nameTh().getAttribute('aria-sort')).toBe('descending');

    click(sortButton('Name'));
    expect(firstColumnTexts()).toEqual(['Bravo', 'Alpha', 'Charlie']);
    expect(nameTh().getAttribute('aria-sort')).toBe('none');
  });

  it('sorts numeric columns ascending first too', () => {
    renderTable();
    click(sortButton('Items'));
    expect(firstColumnTexts()).toEqual(['Charlie', 'Bravo', 'Alpha']);
  });

  it('hides a column from the Columns menu', () => {
    renderTable();
    pointerDown(buttonByText('Columns', container)!);
    const items = Array.from(document.querySelectorAll('[role="menuitemcheckbox"]'));
    expect(items.map((item) => item.textContent)).toEqual(['Status', 'Items']);
    expect(items.every((item) => item.getAttribute('aria-checked') === 'true')).toBe(true);

    click(items[1]);
    expect(headerTexts()).toEqual(['Name', 'Status']);
    expect(container.querySelectorAll('tbody tr')[0].querySelectorAll('td')).toHaveLength(2);
  });

  it('pins the first column and bands rows on hover', () => {
    renderTable({ stickyFirstColumn: true });
    const firstTh = container.querySelector('thead th')!;
    expect(firstTh.classList).toContain('left-0');
    const tr = container.querySelector('tbody tr')!;
    expect(tr.classList).toContain('group');
    expect(tr.classList).toContain('hover:bg-primary/5');
    expect(tr.classList).not.toContain('hover:bg-muted');
    const firstTd = tr.querySelector('td')!;
    expect(firstTd.className).toContain('group-hover:bg-[color-mix(');
    expect(firstTd.className).toContain('var(--card)');
    expect(firstTd.classList).toContain('sticky');
    expect(firstTd.classList).toContain('left-0');
    expect(firstTd.classList).toContain('bg-card');
    expect(tr.querySelectorAll('td')[1].classList).not.toContain('sticky');
  });

  it('shows the header and five skeleton rows while loading', () => {
    renderTable({ isLoading: true });
    const status = container.querySelector('[role="status"]')!;
    expect(status.getAttribute('aria-busy')).toBe('true');
    expect(status.querySelector('.sr-only')?.textContent).toBe('Loading releases…');
    expect(status.querySelectorAll('thead th')).toHaveLength(3);
    expect(status.querySelectorAll('tbody tr')).toHaveLength(5);
    expect(status.querySelectorAll('[data-slot="skeleton"]')).toHaveLength(15);
  });

  it('renders the empty state inside the container when there is no data', () => {
    renderTable({ data: [], emptyState: <p data-testid="empty">No releases yet.</p> });
    const empty = container.querySelector('[data-testid="empty"]')!;
    expect(empty.textContent).toBe('No releases yet.');
    expect(empty.closest('[role="region"]')).not.toBeNull();
    expect(empty.closest('td')?.getAttribute('colspan')).toBe('3');
  });
});

const statusFilter = {
  columnId: 'status',
  title: 'Status',
  options: [
    { value: 'Open', label: 'Open' },
    { value: 'Frozen', label: 'Frozen' },
  ],
};

function facetButton(): HTMLButtonElement {
  const button = container.querySelector<HTMLButtonElement>('[data-slot="faceted-filter-trigger"]');
  expect(button).not.toBeNull();
  return button!;
}

function openFacet() {
  click(facetButton());
}

function option(label: string): HTMLElement {
  const found = Array.from(document.querySelectorAll<HTMLElement>('[role="option"]')).find((el) =>
    el.textContent?.startsWith(label),
  );
  expect(found).toBeDefined();
  return found!;
}

function liveRegion(): Element {
  const region = container.querySelector('[aria-live="polite"]');
  expect(region).not.toBeNull();
  return region!;
}

describe('DataTable toolbar and faceted filters', () => {
  it('orders the page slot, the facets and the Columns menu', () => {
    renderTable({ facetedFilters: [statusFilter], toolbar: <input aria-label="Search" /> });
    const toolbarRow = container.querySelector('input[aria-label="Search"]')!.parentElement!;
    const children = Array.from(toolbarRow.children);
    expect(children[0].tagName).toBe('INPUT');
    expect(children[1]).toBe(facetButton());
    expect(children[children.length - 1].textContent).toBe('Columns');
    expect(children[children.length - 1].classList).toContain('ml-auto');
  });

  it('lists each option with its match count', () => {
    renderTable({ facetedFilters: [statusFilter] });
    expect(facetButton().textContent).toBe('Status');
    openFacet();
    expect(option('Open').textContent).toBe('Open2');
    expect(option('Frozen').textContent).toBe('Frozen1');
  });

  it('filters rows, shows the value badge and announces the count', () => {
    renderTable({ facetedFilters: [statusFilter] });
    expect(liveRegion().textContent).toBe('');
    openFacet();
    click(option('Frozen'));
    expect(firstColumnTexts()).toEqual(['Alpha']);
    const badges = facetButton().querySelectorAll('[data-slot="badge"]');
    expect(Array.from(badges).map((b) => b.textContent)).toEqual(['Frozen']);
    expect(badges[0].getAttribute('data-variant')).toBe('neutral');
    expect(liveRegion().textContent).toBe('1 releases shown.');
  });

  it('summarises three or more selections', () => {
    const filter = { ...statusFilter, options: [...statusFilter.options, { value: 'Closed', label: 'Closed' }] };
    renderTable({ facetedFilters: [filter] });
    openFacet();
    click(option('Open'));
    click(option('Frozen'));
    click(option('Closed'));
    expect(facetButton().querySelectorAll('[data-slot="badge"]')).toHaveLength(1);
    expect(facetButton().textContent).toContain('3 selected');
  });

  it('clears one facet from the popover', () => {
    renderTable({ facetedFilters: [statusFilter] });
    openFacet();
    click(option('Open'));
    expect(firstColumnTexts()).toEqual(['Bravo', 'Charlie']);
    click(option('Clear filter'));
    expect(firstColumnTexts()).toEqual(['Bravo', 'Alpha', 'Charlie']);
    expect(liveRegion().textContent).toBe('');
  });

  it('shows Clear filters only while a facet is active', () => {
    renderTable({ facetedFilters: [statusFilter] });
    expect(buttonByText('Clear filters', container)).toBeUndefined();
    openFacet();
    click(option('Open'));
    const clear = buttonByText('Clear filters', container)!;
    expect(clear).toBeDefined();
    click(clear);
    expect(firstColumnTexts()).toEqual(['Bravo', 'Alpha', 'Charlie']);
    expect(buttonByText('Clear filters', container)).toBeUndefined();
  });

  it('offers Clear filters when filters leave no rows and returns focus to the first facet', () => {
    const filter = { ...statusFilter, options: [...statusFilter.options, { value: 'Closed', label: 'Closed' }] };
    renderTable({ facetedFilters: [filter] });
    openFacet();
    click(option('Closed'));
    const tbody = container.querySelector('tbody')!;
    expect(tbody.textContent).toContain('No releases match these filters.');
    expect(liveRegion().textContent).toBe('0 releases shown.');

    const reset = buttonByText('Clear filters', tbody)!;
    expect(reset).toBeDefined();
    click(reset);
    expect(firstColumnTexts()).toEqual(['Bravo', 'Alpha', 'Charlie']);
    expect(document.activeElement).toBe(facetButton());
  });
});

describe('DataTable row motion and click', () => {
  const bodyRows = () => Array.from(container.querySelectorAll<HTMLTableRowElement>('tbody tr'));

  it('rows without onRowClick have no pointer cursor and no accent bar', () => {
    renderTable();
    const tr = bodyRows()[0];
    expect(tr.classList).not.toContain('cursor-pointer');
    expect(tr.querySelector('td')!.className).not.toContain('var(--primary)');
  });

  it('calls onRowClick for plain cells only and draws the accent bar', () => {
    const onRowClick = vi.fn();
    const linkColumns = helper.columns([
      helper.accessor('name', { header: 'Name', cell: (info) => <a href="#x">{info.getValue()}</a> }),
      helper.accessor('status', { header: 'Status' }),
    ]);
    renderTable({ columns: linkColumns, onRowClick });
    const tr = bodyRows()[0];
    expect(tr.classList).toContain('cursor-pointer');
    expect(tr.querySelector('td')!.className).toContain('group-hover:shadow-[inset_2px_0_0_var(--primary)]');

    click(tr.querySelectorAll('td')[1]);
    expect(onRowClick).toHaveBeenCalledWith(rows[0]);

    onRowClick.mockClear();
    click(tr.querySelector('a')!);
    expect(onRowClick).not.toHaveBeenCalled();
  });

  it('combines the accent bar with the sticky border in one shadow', () => {
    renderTable({ stickyFirstColumn: true, onRowClick: () => {} });
    expect(bodyRows()[0].querySelector('td')!.className).toContain(
      'group-hover:shadow-[inset_2px_0_0_var(--primary),inset_-1px_0_0_var(--border)]',
    );
  });

  it('staggers rows in on the first render only', () => {
    renderTable();
    const first = bodyRows();
    expect(first.every((tr) => tr.classList.contains('motion-safe:animate-row-in'))).toBe(true);
    expect(first.map((tr) => tr.style.animationDelay)).toEqual(['0ms', '30ms', '60ms']);

    click(sortButton('Name'));
    expect(bodyRows().some((tr) => tr.className.includes('animate-row-in'))).toBe(false);
  });

  it('never staggers skeleton rows', () => {
    renderTable({ isLoading: true });
    expect(bodyRows().some((tr) => tr.className.includes('animate-row-in'))).toBe(false);
  });

  it('never staggers under reduced motion', () => {
    reducedMotion.mockReturnValue(true);
    renderTable();
    expect(bodyRows().some((tr) => tr.className.includes('animate-row-in'))).toBe(false);
  });
});
