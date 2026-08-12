import { StrictMode, act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { TemplatePreview } from '../../src/components/TemplatePreview';
import type { ChromeFields, ItemView, PlaceholderEntry, ReleaseView } from '../../src/api/templates';

// Same fixture shape as plan 04-09's TemplatePreview.test.tsx, per this plan's own read_first list.
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
    comments: [],
    commits: [],
    pullRequests: [],
    ...overrides,
  };
}

function sampleView(overrides: Partial<ReleaseView> = {}): ReleaseView {
  return {
    name: 'Sample Release',
    version: '2026.08',
    status: 'OPEN',
    jiraProjectKey: 'CNV',
    createdAt: '2026-08-01T00:00:00Z',
    frozenAt: '',
    lastRefreshedAt: '2026-08-05T00:00:00Z',
    items: [ticketItem()],
    groups: [],
    ...overrides,
  };
}

function chrome(overrides: Partial<ChromeFields> = {}): ChromeFields {
  return {
    coverEnabled: false,
    coverTitle: '',
    coverSubtitle: '',
    headerText: '',
    footerText: '',
    pageNumbersEnabled: false,
    logoRenderHeightMm: 30,
    ...overrides,
  };
}

let container: HTMLDivElement;
let root: Root;

async function renderPreview(props: {
  body?: string;
  chromeFields: ChromeFields;
  view?: ReleaseView;
  logoUrl?: string | null;
}) {
  await act(async () => {
    root.render(
      <StrictMode>
        <TemplatePreview
          body={props.body ?? '# Body'}
          chrome={props.chromeFields}
          view={props.view ?? sampleView()}
          entries={ENTRIES}
          logoUrl={props.logoUrl ?? null}
        />
      </StrictMode>,
    );
  });
}

describe('TemplatePreview chrome rendering', () => {
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

  it('renders no shadow-sm cover block when coverEnabled is false', async () => {
    await renderPreview({ chromeFields: chrome({ coverEnabled: false }) });
    expect(container.querySelector('.shadow-sm')).toBeNull();
  });

  it('renders a shadow-sm cover block when coverEnabled is true', async () => {
    await renderPreview({ chromeFields: chrome({ coverEnabled: true }) });
    expect(container.querySelector('.shadow-sm')).not.toBeNull();
  });

  it('resolves the cover title placeholder into a text-2xl element', async () => {
    await renderPreview({
      chromeFields: chrome({ coverEnabled: true, coverTitle: '{{release.name}} release notes' }),
    });
    const el = container.querySelector('.text-2xl');
    expect(el?.textContent).toBe('Sample Release release notes');
  });

  it('resolves the cover subtitle placeholder into a text-gray-600 element', async () => {
    await renderPreview({
      chromeFields: chrome({ coverEnabled: true, coverSubtitle: 'Version {{release.version}}' }),
    });
    const el = container.querySelector('.text-gray-600');
    expect(el?.textContent).toBe('Version 2026.08');
  });

  it('resolves the header text placeholder into a bg-gray-100 strip', async () => {
    await renderPreview({ chromeFields: chrome({ headerText: 'Runbook · {{release.name}}' }) });
    const el = container.querySelector('.bg-gray-100');
    expect(el?.textContent).toContain('Runbook · Sample Release');
  });

  it('renders a border-t footer strip when footerText is non-empty, none when empty', async () => {
    await renderPreview({ chromeFields: chrome({ footerText: 'Confidential' }) });
    expect(container.querySelector('.border-t')).not.toBeNull();

    await renderPreview({ chromeFields: chrome({ footerText: '' }) });
    expect(container.querySelector('.border-t')).toBeNull();
  });

  it('renders exactly one italic "Page X of Y" element when page numbering is on', async () => {
    await renderPreview({ chromeFields: chrome({ headerText: 'Header', pageNumbersEnabled: true }) });
    const matches = [...container.querySelectorAll('.italic')].filter((el) => el.textContent === 'Page X of Y');
    expect(matches).toHaveLength(1);
  });

  it('marks an unknown chrome placeholder as an amber chip, same as the body', async () => {
    await renderPreview({ chromeFields: chrome({ coverEnabled: true, coverTitle: '{{nope}}' }) });
    const code = container.querySelector('code');
    expect(code?.textContent).toBe('[[unknown: nope]]');
    expect(code?.className).toContain('bg-amber-100');
  });

  it('renders the cover logo image when logoUrl is set, none when null', async () => {
    await renderPreview({ chromeFields: chrome({ coverEnabled: true }), logoUrl: '/api/templates/t1/logo' });
    expect(container.querySelector('img.mx-auto')).not.toBeNull();

    await renderPreview({ chromeFields: chrome({ coverEnabled: true }), logoUrl: null });
    expect(container.querySelector('img.mx-auto')).toBeNull();
  });

  it('sizes the cover logo from chrome.logoRenderHeightMm, in mm, not a fixed Tailwind step', async () => {
    await renderPreview({
      chromeFields: chrome({ coverEnabled: true, logoRenderHeightMm: 55 }),
      logoUrl: '/api/templates/t1/logo',
    });

    const img = container.querySelector<HTMLImageElement>('img')!;
    expect(img.style.maxHeight).toBe('55mm');
    // The old fixed cap must not be reachable by any path: a class-based max-height would silently
    // win over or fight with the inline style depending on specificity.
    expect(img.className).not.toContain('max-h-');
  });
});
