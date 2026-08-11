import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { DocumentViewerRoute } from '../../src/routes/DocumentViewerRoute';

type LinkProps = {
  children?: ReactNode;
  to?: string;
  params?: Record<string, string>;
  search?: Record<string, unknown>;
  hash?: string;
};

let searchParams: { revision?: number } = {};
const navigate = vi.fn();

vi.mock('@tanstack/react-router', () => ({
  useParams: () => ({ releaseId: 'r1', documentType: 'RELEASE_NOTES' }),
  useSearch: () => searchParams,
  useNavigate: () => navigate,
  Link: (props: LinkProps) => {
    let href = props.to ?? '';
    for (const [key, value] of Object.entries(props.params ?? {})) {
      href = href.replace(`$${key}`, value);
    }
    if (props.search) {
      href += `?${new URLSearchParams(Object.entries(props.search).map(([k, v]) => [k, String(v)])).toString()}`;
    }
    if (props.hash) href += `#${props.hash}`;
    return <a href={href}>{props.children}</a>;
  },
}));

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

const CHROME = {
  coverEnabled: true,
  coverTitle: 'Release Notes',
  coverSubtitle: 'v4.2.0',
  headerText: 'Orchstro',
  footerText: 'Confidential',
  pageNumbersEnabled: true,
  logoRenderHeightMm: 30,
};

function generatedDocument(overrides: Record<string, unknown> = {}) {
  return {
    id: 'doc-1',
    releaseId: 'r1',
    documentType: 'RELEASE_NOTES',
    revision: 1,
    latestRevision: 1,
    markdown: '# Heading\n\nBody text.',
    templateName: 'Standard',
    templateDeleted: false,
    hasLogo: false,
    chrome: CHROME,
    generatedAt: '2026-08-10T09:00:00Z',
    generatedBy: 'rm@acme.com',
    ...overrides,
  };
}

const RELEASE = {
  id: 'r1',
  name: 'Orchstro',
  version: '4.2.0',
  status: 'OPEN',
  jiraProjectKey: 'ORC',
  createdAt: '2026-08-01T00:00:00Z',
  createdBy: 'rm@acme.com',
  frozenAt: null,
  frozenBy: null,
  lastRefreshedAt: '2026-08-05T00:00:00Z',
  items: [],
  manualItems: [],
};

type Fixture = {
  documentResponse?: () => Response;
  readiness?: () => Response;
};

function mockFetch(fixture: Fixture) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = typeof input === 'string' ? input : input.toString();

    if (url.startsWith('/api/documents/readiness')) {
      if (fixture.readiness) return fixture.readiness();
      return json(200, {
        entries: [{ releaseId: 'r1', documentType: 'RELEASE_NOTES', state: 'UP_TO_DATE' }],
      });
    }
    if (url === '/api/releases/r1') {
      return json(200, RELEASE);
    }
    if (url.startsWith('/api/releases/r1/documents/RELEASE_NOTES/logo')) {
      return new Response(new Blob(['img']), { status: 200 });
    }
    if (url.includes('/export.pdf') || url.includes('/export.docx')) {
      return new Response(new Blob(['bytes']), { status: 200 });
    }
    if (url.startsWith('/api/releases/r1/documents/RELEASE_NOTES')) {
      if (fixture.documentResponse) return fixture.documentResponse();
      return json(200, generatedDocument());
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

async function renderRoute() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <DocumentViewerRoute />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
  await flush();
}

describe('DocumentViewerRoute', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    searchParams = {};
    navigate.mockClear();
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
  });

  it('rendersTheLatestRevisionWhenNoRevisionParamIsPresent', async () => {
    mockFetch({});
    await renderRoute();

    expect(container.querySelector('h1')).not.toBeNull();
    expect(container.textContent).not.toContain('# Heading');
  });

  it('rendersCoverHeaderAndFooterFromTheStoredChrome', async () => {
    mockFetch({});
    await renderRoute();

    expect(container.textContent).toContain('Release Notes');
    expect(container.textContent).toContain('v4.2.0');
    expect(container.textContent).toContain('Orchstro');
    expect(container.textContent).toContain('Confidential');
  });

  it('rendersTheLogoOnlyWhenTheSnapshotHasOne', async () => {
    mockFetch({ documentResponse: () => json(200, generatedDocument({ hasLogo: false })) });
    await renderRoute();
    expect(container.querySelector('img')).toBeNull();

    await act(async () => root.unmount());
    container.remove();
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);

    mockFetch({ documentResponse: () => json(200, generatedDocument({ hasLogo: true })) });
    await renderRoute();
    expect(container.querySelector('img')).not.toBeNull();
  });

  it('supersededRevisionShowsTheBannerNamingBothRevisions', async () => {
    searchParams = { revision: 1 };
    mockFetch({
      documentResponse: () => json(200, generatedDocument({ revision: 1, latestRevision: 3 })),
    });
    await renderRoute();

    expect(container.textContent).toContain("You're viewing Revision 1");
    expect(container.textContent).toContain('superseded by Revision 3');
    const viewLatest = [...container.querySelectorAll('a')].find((a) => a.textContent === 'View latest');
    expect(viewLatest).toBeDefined();
  });

  it('latestRevisionShowsNoSupersededBanner', async () => {
    mockFetch({ documentResponse: () => json(200, generatedDocument({ revision: 1, latestRevision: 1 })) });
    await renderRoute();

    expect(container.textContent).not.toContain('superseded by');
  });

  it('templateDeletedShowsTheNoteAboveTheBanner', async () => {
    searchParams = { revision: 1 };
    mockFetch({
      documentResponse: () =>
        json(200, generatedDocument({ revision: 1, latestRevision: 2, templateDeleted: true })),
    });
    await renderRoute();

    const note = container.textContent ?? '';
    const noteIdx = note.indexOf('has since been deleted');
    const bannerIdx = note.indexOf("You're viewing Revision");
    expect(noteIdx).toBeGreaterThan(-1);
    expect(bannerIdx).toBeGreaterThan(-1);
    expect(noteIdx).toBeLessThan(bannerIdx);
  });

  it('revisionPickerListsEveryRevisionNewestFirstAndMarksTheLatest', async () => {
    mockFetch({ documentResponse: () => json(200, generatedDocument({ revision: 3, latestRevision: 3 })) });
    await renderRoute();

    const select = container.querySelector('select[aria-label="Revision"]') as HTMLSelectElement;
    const options = [...select.querySelectorAll('option')].map((o) => o.textContent);
    expect(options).toEqual(['Revision 3 (latest)', 'Revision 2', 'Revision 1']);
  });

  it('changingTheRevisionPickerNavigatesWithTheChosenRevision', async () => {
    mockFetch({ documentResponse: () => json(200, generatedDocument({ revision: 3, latestRevision: 3 })) });
    await renderRoute();

    const select = container.querySelector('select[aria-label="Revision"]') as HTMLSelectElement;
    await act(async () => {
      select.value = '2';
      select.dispatchEvent(new Event('change', { bubbles: true }));
    });

    expect(navigate).toHaveBeenCalledWith(expect.objectContaining({ search: { revision: 2 } }));
  });

  it('documentNotFoundRendersTheAlertWithNoTryAgain', async () => {
    mockFetch({ documentResponse: () => json(404, { status: 404, reason: 'DOCUMENT_NOT_FOUND' }) });
    await renderRoute();

    const alerts = container.querySelectorAll('[role="alert"]');
    expect(alerts).toHaveLength(1);
    expect(alerts[0].getAttribute('data-reason')).toBe('DOCUMENT_NOT_FOUND');
    const tryAgain = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Try again');
    expect(tryAgain).toBeUndefined();
  });

  it('genericLoadFailureRendersTheAlertWithTryAgain', async () => {
    mockFetch({ documentResponse: () => json(500, { status: 500, reason: 'UNKNOWN' }) });
    await renderRoute();

    const alert = container.querySelector('[role="alert"]');
    expect(alert).not.toBeNull();
    const tryAgain = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Try again');
    expect(tryAgain).toBeDefined();
  });

  it('parityHintIsHiddenUntilBothFormatsHaveBeenExported', async () => {
    mockFetch({});
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    await renderRoute();

    expect(container.textContent).not.toContain('Visual parity check');

    const pdfButton = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Export PDF')!;
    await act(async () => pdfButton.click());
    await flush();
    expect(container.textContent).not.toContain('Visual parity check');

    const docxButton = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Export DOCX')!;
    await act(async () => docxButton.click());
    await flush();
    expect(container.textContent).toContain('Visual parity check');
  });

  it('exportsUseTheRevisionBeingViewedNotTheLatest', async () => {
    searchParams = { revision: 1 };
    const requested: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = typeof input === 'string' ? input : input.toString();
      requested.push(url);
      if (url.startsWith('/api/documents/readiness')) {
        return json(200, { entries: [{ releaseId: 'r1', documentType: 'RELEASE_NOTES', state: 'UP_TO_DATE' }] });
      }
      if (url === '/api/releases/r1') return json(200, RELEASE);
      if (url.includes('export.pdf')) return new Response(new Blob(['bytes']), { status: 200 });
      if (url.startsWith('/api/releases/r1/documents/RELEASE_NOTES')) {
        return json(200, generatedDocument({ revision: 1, latestRevision: 3 }));
      }
      return json(404, { status: 404, reason: 'UNKNOWN' });
    });
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});

    await renderRoute();
    const pdfButton = [...container.querySelectorAll('button')].find((b) => b.textContent === 'Export PDF')!;
    await act(async () => pdfButton.click());
    await flush();

    expect(requested.some((u) => u.includes('export.pdf') && u.includes('revision=1'))).toBe(true);
  });
});
