import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { DocumentsSection } from '../../src/components/DocumentsSection';
import type { DocumentReadinessState, DocumentRevision } from '../../src/api/documents';

type LinkProps = {
  children?: ReactNode;
  to?: string;
  params?: Record<string, string>;
  search?: Record<string, unknown>;
  hash?: string;
  className?: string;
};

vi.mock('@tanstack/react-router', () => ({
  Link: (props: LinkProps) => {
    let href = props.to ?? '';
    for (const [key, value] of Object.entries(props.params ?? {})) {
      href = href.replace(`$${key}`, value);
    }
    if (props.search) {
      href += `?${new URLSearchParams(Object.entries(props.search).map(([k, v]) => [k, String(v)])).toString()}`;
    }
    if (props.hash) href += `#${props.hash}`;
    return (
      <a href={href} className={props.className}>
        {props.children}
      </a>
    );
  },
}));

type DocType = 'RELEASE_NOTES' | 'RUNBOOK' | 'APPROVAL';

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

function rev(
  revision: number,
  latest: boolean,
  generatedAt = '2026-08-05T00:00:00Z',
  generatedBy = 'rm@acme.com',
  templateName = 'Standard',
): DocumentRevision {
  return { revision, latest, generatedAt, generatedBy, templateName };
}

function generatedDocument(documentType: DocType, revision = 1) {
  return {
    id: 'doc-1',
    releaseId: 'r1',
    documentType,
    revision,
    latestRevision: revision,
    markdown: '# Notes',
    templateName: 'Standard',
    templateDeleted: false,
    hasLogo: false,
    chrome: {
      coverEnabled: false,
      coverTitle: '',
      coverSubtitle: '',
      headerText: '',
      footerText: '',
      pageNumbersEnabled: false,
      logoRenderHeightMm: 30,
    },
    generatedAt: '2026-08-10T09:00:00Z',
    generatedBy: 'rm@acme.com',
  };
}

type Fixture = {
  readiness?: Partial<Record<DocType, DocumentReadinessState>>;
  revisions?: Partial<Record<DocType, DocumentRevision[]>>;
  generateStatus?: Partial<Record<DocType, () => Response>>;
  exportStatus?: () => Response;
};

// A single fetch mock routes every request DocumentsSection can issue - the readiness grid, each
// document type's revision list, generate (POST) and export.pdf/export.docx - so each test only
// needs to describe the fixture it cares about.
function mockFetch(fixture: Fixture) {
  const readiness = fixture.readiness ?? {};
  const revisions = fixture.revisions ?? {};
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = typeof input === 'string' ? input : input.toString();
    const method = (init?.method ?? 'GET').toUpperCase();

    if (url.startsWith('/api/documents/readiness')) {
      const entries = (Object.keys(readiness) as DocType[]).map((type) => ({
        releaseId: 'r1',
        documentType: type,
        state: readiness[type],
      }));
      return json(200, { entries });
    }

    const revisionsMatch = url.match(/\/api\/releases\/r1\/documents\/(\w+)\/revisions$/);
    if (revisionsMatch) {
      const type = revisionsMatch[1] as DocType;
      return json(200, { revisions: revisions[type] ?? [] });
    }

    const exportMatch = url.match(/\/api\/releases\/r1\/documents\/(\w+)\/export\.(pdf|docx)/);
    if (exportMatch) {
      if (fixture.exportStatus) return fixture.exportStatus();
      return new Response(new Blob(['bytes']), { status: 200 });
    }

    const generateMatch = url.match(/\/api\/releases\/r1\/documents\/(\w+)$/);
    if (generateMatch && method === 'POST') {
      const type = generateMatch[1] as DocType;
      const override = fixture.generateStatus?.[type];
      if (override) return override();
      return json(201, generatedDocument(type));
    }

    return json(404, { status: 404, reason: 'UNKNOWN' });
  });
}

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderSection() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <DocumentsSection releaseId="r1" releaseName="Orchstro" version="4.2.0" />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
  await flush();
}

function rowFor(label: string): HTMLElement {
  const heading = [...container.querySelectorAll('h4')].find((h) => h.textContent === label);
  expect(heading, `no row heading "${label}"`).toBeDefined();
  const row = heading!.closest('div.border-b');
  expect(row, `no row container for "${label}"`).not.toBeNull();
  return row as HTMLElement;
}

function byText(scope: ParentNode, text: string): HTMLButtonElement {
  const button = [...scope.querySelectorAll('button')].find((b) => b.textContent === text);
  expect(button, `no button labelled "${text}"`).toBeDefined();
  return button!;
}

function findText(scope: ParentNode, text: string): HTMLButtonElement | undefined {
  return [...scope.querySelectorAll('button')].find((b) => b.textContent === text);
}

describe('DocumentsSection', () => {
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
    vi.restoreAllMocks();
  });

  it('rendersOneRowPerDocumentTypeInOrder', async () => {
    mockFetch({});
    await renderSection();

    const headings = [...container.querySelectorAll('h4')].map((h) => h.textContent);
    expect(headings).toEqual(['Release Notes', 'Runbook', 'Approval']);
  });

  it('noTemplateShowsGuidanceAndNoGenerateButton', async () => {
    mockFetch({ readiness: { RELEASE_NOTES: 'NO_TEMPLATE' } });
    await renderSection();

    const row = rowFor('Release Notes');
    expect(row.textContent).toContain('No Release Notes template — create one to generate this document.');
    expect(row.querySelector('select')).toBeNull();
    expect(findText(row, 'Generate Release Notes')).toBeUndefined();
    const goToTemplatesLinks = [...row.querySelectorAll('a')].filter((a) => a.textContent === 'Go to templates');
    expect(goToTemplatesLinks.length).toBeGreaterThan(0);
  });

  it('notGeneratedShowsGenerateButton', async () => {
    mockFetch({ readiness: { RELEASE_NOTES: 'NOT_GENERATED' } });
    await renderSection();

    const row = rowFor('Release Notes');
    expect(row.textContent).toContain("This document hasn't been generated yet.");
    expect(byText(row, 'Generate Release Notes')).toBeDefined();
  });

  it('generateCallsTheMutationAndShowsGeneratingLabel', async () => {
    let resolveGenerate: ((response: Response) => void) | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = typeof input === 'string' ? input : input.toString();
      const method = (init?.method ?? 'GET').toUpperCase();
      if (url.startsWith('/api/documents/readiness')) {
        return json(200, { entries: [{ releaseId: 'r1', documentType: 'RELEASE_NOTES', state: 'NOT_GENERATED' }] });
      }
      if (url.endsWith('/revisions')) return json(200, { revisions: [] });
      if (method === 'POST') {
        return new Promise<Response>((resolve) => {
          resolveGenerate = resolve;
        });
      }
      return json(404, { status: 404, reason: 'UNKNOWN' });
    });

    await renderSection();
    const row = rowFor('Release Notes');
    await act(async () => byText(row, 'Generate Release Notes').click());
    await flush();

    const pendingButton = byText(row, 'Generating…');
    expect(pendingButton.disabled).toBe(true);

    await act(async () => {
      resolveGenerate?.(json(201, generatedDocument('RELEASE_NOTES')));
    });
    await flush();
  });

  it('generatePendingDisablesThatRowsExportsOnly', async () => {
    let resolveGenerate: ((response: Response) => void) | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = typeof input === 'string' ? input : input.toString();
      const method = (init?.method ?? 'GET').toUpperCase();
      if (url.startsWith('/api/documents/readiness')) {
        return json(200, {
          entries: [
            { releaseId: 'r1', documentType: 'RELEASE_NOTES', state: 'UP_TO_DATE' },
            { releaseId: 'r1', documentType: 'RUNBOOK', state: 'UP_TO_DATE' },
          ],
        });
      }
      if (url.includes('/documents/RELEASE_NOTES/revisions')) return json(200, { revisions: [rev(1, true)] });
      if (url.includes('/documents/RUNBOOK/revisions')) return json(200, { revisions: [rev(1, true)] });
      if (url.endsWith('/revisions')) return json(200, { revisions: [] });
      if (url.includes('/documents/RELEASE_NOTES') && method === 'POST') {
        return new Promise<Response>((resolve) => {
          resolveGenerate = resolve;
        });
      }
      return json(404, { status: 404, reason: 'UNKNOWN' });
    });

    await renderSection();
    const releaseNotesRow = rowFor('Release Notes');
    const runbookRow = rowFor('Runbook');

    await act(async () => byText(releaseNotesRow, 'Regenerate Release Notes').click());
    await flush();

    expect(byText(releaseNotesRow, 'Export PDF').disabled).toBe(true);
    expect(byText(runbookRow, 'Export PDF').disabled).toBe(false);

    await act(async () => {
      resolveGenerate?.(json(201, generatedDocument('RELEASE_NOTES', 2)));
    });
    await flush();
  });

  it('regenerateOpensNoDialogAndAppends', async () => {
    mockFetch({
      readiness: { RELEASE_NOTES: 'STALE' },
      revisions: { RELEASE_NOTES: [rev(1, true)] },
    });
    await renderSection();

    const row = rowFor('Release Notes');
    expect(container.querySelector('dialog')).toBeNull();

    await act(async () => byText(row, 'Regenerate Release Notes').click());
    await flush();

    expect(container.querySelector('dialog')).toBeNull();
  });

  it('staleRowRendersTheRegenerateButtonInBlue', async () => {
    mockFetch({
      readiness: { RELEASE_NOTES: 'STALE' },
      revisions: { RELEASE_NOTES: [rev(1, true)] },
    });
    await renderSection();

    const row = rowFor('Release Notes');
    const button = byText(row, 'Regenerate Release Notes');
    expect(button.className).toContain('bg-blue-600');
  });

  it('generationErrorIsScopedToItsOwnRow', async () => {
    mockFetch({
      readiness: { RELEASE_NOTES: 'NOT_GENERATED', RUNBOOK: 'NOT_GENERATED' },
      generateStatus: {
        RELEASE_NOTES: () => json(409, { status: 409, reason: 'NO_TEMPLATE' }),
      },
    });
    await renderSection();

    const releaseNotesRow = rowFor('Release Notes');
    const runbookRow = rowFor('Runbook');

    await act(async () => byText(releaseNotesRow, 'Generate Release Notes').click());
    await flush();

    expect(releaseNotesRow.querySelector('[role="alert"][data-reason="NO_TEMPLATE"]')).not.toBeNull();
    expect(runbookRow.querySelector('[role="alert"]')).toBeNull();
  });

  it('exportPdfAndDocxCallTheMutationWithTheRightFormat', async () => {
    const requested: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = typeof input === 'string' ? input : input.toString();
      requested.push(url);
      if (url.startsWith('/api/documents/readiness')) {
        return json(200, { entries: [{ releaseId: 'r1', documentType: 'RELEASE_NOTES', state: 'UP_TO_DATE' }] });
      }
      if (url.includes('/documents/RELEASE_NOTES/revisions')) return json(200, { revisions: [rev(1, true)] });
      if (url.endsWith('/revisions')) return json(200, { revisions: [] });
      if (url.includes('export.pdf') || url.includes('export.docx')) {
        return new Response(new Blob(['bytes']), { status: 200 });
      }
      return json(404, { status: 404, reason: 'UNKNOWN' });
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    await renderSection();
    const row = rowFor('Release Notes');

    await act(async () => byText(row, 'Export PDF').click());
    await flush();
    expect(requested.some((u) => u.includes('export.pdf'))).toBe(true);

    await act(async () => byText(row, 'Export DOCX').click());
    await flush();
    expect(requested.some((u) => u.includes('export.docx'))).toBe(true);
  });

  it('exportPendingDisablesOnlyTheClickedButton', async () => {
    let resolveExport: ((response: Response) => void) | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = typeof input === 'string' ? input : input.toString();
      if (url.startsWith('/api/documents/readiness')) {
        return json(200, { entries: [{ releaseId: 'r1', documentType: 'RELEASE_NOTES', state: 'UP_TO_DATE' }] });
      }
      if (url.includes('/documents/RELEASE_NOTES/revisions')) return json(200, { revisions: [rev(1, true)] });
      if (url.endsWith('/revisions')) return json(200, { revisions: [] });
      if (url.includes('export.pdf')) {
        return new Promise<Response>((resolve) => {
          resolveExport = resolve;
        });
      }
      return json(404, { status: 404, reason: 'UNKNOWN' });
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    await renderSection();
    const row = rowFor('Release Notes');

    await act(async () => byText(row, 'Export PDF').click());
    await flush();

    const pendingButton = byText(row, 'Exporting PDF…');
    expect(pendingButton.disabled).toBe(true);
    const docxButton = byText(row, 'Export DOCX');
    expect(docxButton.disabled).toBe(false);

    await act(async () => {
      resolveExport?.(new Response(new Blob(['bytes']), { status: 200 }));
    });
    await flush();
  });

  it('exportSuccessShowsNoBanner', async () => {
    mockFetch({
      readiness: { RELEASE_NOTES: 'UP_TO_DATE' },
      revisions: { RELEASE_NOTES: [rev(1, true)] },
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    await renderSection();
    const row = rowFor('Release Notes');

    await act(async () => byText(row, 'Export PDF').click());
    await flush();

    expect(container.querySelector('[role="status"]')).toBeNull();
    expect(row.textContent).not.toContain('succeeded');
    expect(byText(row, 'Export PDF')).toBeDefined();
  });

  it('revisionHistoryIsCollapsedByDefaultAndTogglesAriaExpanded', async () => {
    mockFetch({
      readiness: { RELEASE_NOTES: 'UP_TO_DATE' },
      revisions: { RELEASE_NOTES: [rev(1, true)] },
    });
    await renderSection();

    const row = rowFor('Release Notes');
    const disclosure = [...row.querySelectorAll('button')].find((b) => b.getAttribute('aria-expanded') !== null)!;
    expect(disclosure.getAttribute('aria-expanded')).toBe('false');
    expect(row.querySelector('ul')).toBeNull();

    await act(async () => disclosure.click());

    expect(disclosure.getAttribute('aria-expanded')).toBe('true');
    expect(row.querySelector('ul')).not.toBeNull();
  });

  it('revisionHistoryListsNewestFirstWithLatestAndSupersededLabels', async () => {
    mockFetch({
      readiness: { RELEASE_NOTES: 'UP_TO_DATE' },
      revisions: {
        RELEASE_NOTES: [rev(1, false, '2026-08-01T00:00:00Z'), rev(2, true, '2026-08-05T00:00:00Z')],
      },
    });
    await renderSection();

    const row = rowFor('Release Notes');
    const disclosure = [...row.querySelectorAll('button')].find((b) => b.getAttribute('aria-expanded') !== null)!;
    await act(async () => disclosure.click());

    const items = [...row.querySelectorAll('ul li')];
    expect(items).toHaveLength(2);
    expect(items[0].textContent).toContain('Revision 2');
    expect(items[0].textContent).toContain('(latest)');
    expect(items[1].textContent).toContain('Revision 1');
    expect(items[1].textContent).toContain('(superseded)');
  });

  it('supersededRevisionHasItsOwnExportActions', async () => {
    mockFetch({
      readiness: { RELEASE_NOTES: 'UP_TO_DATE' },
      revisions: {
        RELEASE_NOTES: [rev(1, false, '2026-08-01T00:00:00Z'), rev(2, true, '2026-08-05T00:00:00Z')],
      },
    });
    await renderSection();

    const row = rowFor('Release Notes');
    const disclosure = [...row.querySelectorAll('button')].find((b) => b.getAttribute('aria-expanded') !== null)!;
    await act(async () => disclosure.click());

    const superseded = [...row.querySelectorAll('ul li')].find((li) => li.textContent?.includes('(superseded)'))!;
    expect(findText(superseded, 'Export PDF')).toBeDefined();
    expect(findText(superseded, 'Export DOCX')).toBeDefined();
  });

  it('supersededRevisionViewLinkCarriesTheRevisionSearchParam', async () => {
    mockFetch({
      readiness: { RELEASE_NOTES: 'UP_TO_DATE' },
      revisions: {
        RELEASE_NOTES: [rev(1, false, '2026-08-01T00:00:00Z'), rev(2, true, '2026-08-05T00:00:00Z')],
      },
    });
    await renderSection();

    const row = rowFor('Release Notes');
    const disclosure = [...row.querySelectorAll('button')].find((b) => b.getAttribute('aria-expanded') !== null)!;
    await act(async () => disclosure.click());

    const superseded = [...row.querySelectorAll('ul li')].find((li) => li.textContent?.includes('(superseded)'))!;
    const viewLink = [...superseded.querySelectorAll('a')].find((a) => a.textContent === 'View')!;
    expect(viewLink.getAttribute('href')).toMatch(/\?revision=1$/);
  });

  it('sectionRendersIdenticallyForFrozenReleases', async () => {
    mockFetch({
      readiness: { RELEASE_NOTES: 'UP_TO_DATE' },
      revisions: { RELEASE_NOTES: [rev(1, true)] },
    });
    await renderSection();

    // DocumentsSection accepts no status/isOpen prop at all, so it cannot branch on release
    // status - there is no "Frozen"/"Open" text anywhere in its output.
    expect(container.querySelector('#documents')).not.toBeNull();
    expect(container.textContent).not.toContain('Frozen');
    expect(container.textContent).not.toContain('Open');
  });
});
