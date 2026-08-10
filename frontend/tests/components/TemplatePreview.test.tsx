import { StrictMode, act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { TemplatePreview } from '../../src/components/TemplatePreview';
import type { ChromeFields, ItemView, PlaceholderEntry, ReleaseView } from '../../src/api/templates';

// No cover/header/footer configured - this suite exercises only the body pipeline this plan's
// chrome extension wraps, unchanged. ChromePreview.test.tsx (plan 04-10) exercises the chrome props.
const NO_CHROME: ChromeFields = {
  coverEnabled: false,
  coverTitle: '',
  coverSubtitle: '',
  headerText: '',
  footerText: '',
  pageNumbersEnabled: false,
};

// Mirrors PlaceholderCatalog.ENTRIES's token/scope shape (plan 04-01/04-20) - the pushes() field
// is never serialized over the wire (PlaceholderEntryResponse omits it), so it is absent here too.
const ENTRIES: PlaceholderEntry[] = [
  { scope: 'ROOT', token: 'release.name', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ROOT', token: 'release.version', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ROOT', token: 'release.status', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ROOT', token: 'release.jiraProjectKey', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ROOT', token: 'release.createdAt', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ROOT', token: 'release.frozenAt', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ROOT', token: 'release.lastRefreshedAt', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ROOT', token: 'items', kind: 'SECTION', description: '', tags: [] },
  { scope: 'ROOT', token: 'groups', kind: 'SECTION', description: '', tags: [] },
  { scope: 'ITEM', token: 'title', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ITEM', token: 'isManual', kind: 'SECTION', description: '', tags: [] },
  { scope: 'ITEM', token: 'key', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ITEM', token: 'issueType', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ITEM', token: 'status', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ITEM', token: 'resolution', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ITEM', token: 'assignee', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ITEM', token: 'reporter', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ITEM', token: 'description', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ITEM', token: 'category', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'ITEM', token: 'comments', kind: 'SECTION', description: '', tags: [] },
  { scope: 'ITEM', token: 'commits', kind: 'SECTION', description: '', tags: [] },
  { scope: 'ITEM', token: 'pullRequests', kind: 'SECTION', description: '', tags: [] },
  { scope: 'GROUP', token: 'groupName', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'GROUP', token: 'items', kind: 'SECTION', description: '', tags: [] },
  { scope: 'COMMENT', token: 'text', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'COMMIT', token: 'id', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'COMMIT', token: 'message', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'COMMIT', token: 'author', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'COMMIT', token: 'url', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'PULL_REQUEST', token: 'id', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'PULL_REQUEST', token: 'title', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'PULL_REQUEST', token: 'status', kind: 'VARIABLE', description: '', tags: [] },
  { scope: 'PULL_REQUEST', token: 'url', kind: 'VARIABLE', description: '', tags: [] },
];

function ticketItem(overrides: Partial<ItemView> = {}): ItemView {
  return {
    title: 'Fix duplicate invoice lines',
    isManual: false,
    key: 'CNV-2896',
    issueType: 'Bug',
    status: 'Done',
    resolution: 'Fixed',
    assignee: 'Jane Doe',
    reporter: 'Jane Doe',
    component: '',
    description: 'Duplicate lines appear on the invoice PDF.',
    category: '',
    comments: [{ text: 'Looks good' }],
    commits: [],
    pullRequests: [],
    ...overrides,
  };
}

const CNV_2896 = ticketItem();
const CNV_2901 = ticketItem({
  title: 'Add export button',
  key: 'CNV-2901',
  issueType: 'Story',
  status: 'In Progress',
  resolution: '',
  assignee: '',
  reporter: '',
  description: '',
  comments: [],
});
const MANUAL_ITEM: ItemView = {
  title: 'Run migration V4',
  isManual: true,
  key: '',
  issueType: '',
  status: '',
  resolution: '',
  assignee: '',
  reporter: '',
  component: '',
  description: '',
  category: 'Database script',
  comments: [],
  commits: [],
  pullRequests: [],
};

function sampleView(overrides: Partial<ReleaseView> = {}): ReleaseView {
  return {
    name: 'Sample Release',
    version: '2026.08',
    status: 'OPEN',
    jiraProjectKey: 'CNV',
    createdAt: '2026-08-01T00:00:00Z',
    frozenAt: '',
    lastRefreshedAt: '2026-08-05T00:00:00Z',
    items: [CNV_2896, CNV_2901, MANUAL_ITEM],
    groups: [
      { groupName: 'Bug', items: [CNV_2896] },
      { groupName: 'Story', items: [CNV_2901] },
      { groupName: 'Database script', items: [MANUAL_ITEM] },
    ],
    ...overrides,
  };
}

let container: HTMLDivElement;
let root: Root;

async function renderPreview(body: string, view: ReleaseView | undefined, entries: PlaceholderEntry[] | undefined = ENTRIES) {
  await act(async () => {
    root.render(
      <StrictMode>
        <TemplatePreview body={body} chrome={NO_CHROME} view={view} entries={entries} logoUrl={null} />
      </StrictMode>,
    );
  });
}

describe('TemplatePreview', () => {
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

  it('renders a Mustache variable through Markdown to an h1', async () => {
    await renderPreview('# {{release.name}}', sampleView());
    expect(container.querySelector('h1')?.textContent).toBe('Sample Release');
  });

  it('renders raw HTML typed into the template body as inert literal text', async () => {
    await renderPreview('<script>alert(1)</script>', sampleView());
    expect(container.innerHTML).toContain('&lt;script&gt;');
    expect(container.innerHTML).not.toContain('<script>');
  });

  it('renders raw HTML arriving through an interpolated ticket value as inert literal text', async () => {
    const view = sampleView({ items: [ticketItem({ title: '<img src=x onerror=alert(1)>' })] });
    await renderPreview('{{#items}}{{title}}{{/items}}', view);
    expect(container.innerHTML).toContain('&lt;img');
    expect(container.innerHTML).not.toContain('<img');
  });

  it('renders Markdown bold and inline code', async () => {
    await renderPreview('**bold** and `code`', sampleView());
    expect(container.querySelector('strong')?.textContent).toBe('bold');
    expect(container.querySelector('code')?.textContent).toBe('code');
  });

  it('loops over groups, rendering one heading per group in order', async () => {
    await renderPreview('{{#groups}}## {{groupName}}\n{{/groups}}', sampleView());
    const headings = [...container.querySelectorAll('h2')].map((h) => h.textContent);
    expect(headings).toEqual(['Bug', 'Story', 'Database script']);
  });

  it('applies the isManual inverted section to distinguish ticket items from manual items', async () => {
    await renderPreview('{{#items}}{{title}}{{^isManual}} ({{key}}){{/isManual}}\n{{/items}}', sampleView());
    expect(container.textContent).toContain('Fix duplicate invoice lines (CNV-2896)');
    expect(container.textContent).toContain('Run migration V4');
    expect(container.textContent).not.toContain('Run migration V4 (');
  });

  it('treats an empty collection as falsy for an inverted section (D-04-18 idiom)', async () => {
    await renderPreview('{{#items}}{{^commits}}No linked code{{/commits}}{{/items}}', sampleView());
    expect(container.textContent).toContain('No linked code');
  });

  it('treats an empty string field as falsy for an inverted section', async () => {
    await renderPreview('{{#items}}{{^resolution}}Unresolved{{/resolution}}{{/items}}', sampleView());
    expect(container.textContent).toContain('Unresolved');
  });

  it('escapes an ampersand in an interpolated value exactly once', async () => {
    const view = sampleView({ items: [ticketItem({ title: 'Tom & Jerry' })] });
    await renderPreview('{{#items}}{{title}}{{/items}}', view);
    expect(container.innerHTML).toContain('Tom &amp; Jerry');
    expect(container.innerHTML).not.toContain('&amp;amp;');
  });

  it('marks an unknown root-scope placeholder as a conspicuous amber chip, never a blank', async () => {
    await renderPreview('{{ticket.compnent}}', sampleView());
    const code = container.querySelector('code');
    expect(code?.textContent).toBe('[[unknown: ticket.compnent]]');
    expect(code?.className).toContain('bg-amber-100');
  });

  it('does not let a root-scope token leak into item scope', async () => {
    await renderPreview('{{#items}}{{release.name}}{{/items}}', sampleView());
    expect(container.textContent).toContain('[[unknown: release.name]]');
  });

  it('shows a neutral, non-alert panel for unparseable syntax instead of erroring', async () => {
    await renderPreview('{{#items}}', sampleView());
    expect(container.textContent).toBe(
      'Preview unavailable — check your template syntax (e.g., every {{#x}} needs a matching {{/x}}).',
    );
    expect(container.querySelector('[role="alert"]')).toBeNull();
  });

  it('shows the empty-body message when the template body is blank', async () => {
    await renderPreview('', sampleView());
    expect(container.textContent).toContain("This template's body is empty.");
  });

  it('shows a truncation strip when the release exceeds the client-side preview cap', async () => {
    const items = Array.from({ length: 250 }, (_, i) => ticketItem({ key: `CNV-${3000 + i}` }));
    await renderPreview('{{#items}}{{title}}{{/items}}', sampleView({ items }));
    expect(container.textContent).toContain(
      'Preview truncated — this release has 250 items; showing the first 200.',
    );
  });

  it('shows the loading message while the release view-model has not arrived yet', async () => {
    await renderPreview('{{release.name}}', undefined);
    expect(container.textContent).toContain('Loading preview data…');
  });
});
