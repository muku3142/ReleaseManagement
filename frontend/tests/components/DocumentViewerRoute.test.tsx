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
let documentTypeParam = 'RELEASE_NOTES';
const navigate = vi.fn();

vi.mock('../../src/components/AiAssistPanel', () => ({
  AiAssistPanel: (props: {
    document: { revision: number };
    children: ReactNode;
    onResultOpenChange: (open: boolean) => void;
    onSaved: (revision: number) => void;
  }) => (
    <div data-testid="panel" data-revision={props.document.revision}>
      <button type="button" onClick={() => props.onResultOpenChange(true)}>
        open-result
      </button>
      <button type="button" onClick={() => props.onSaved(5)}>
        save-result
      </button>
      {props.children}
    </div>
  ),
}));

vi.mock('@tanstack/react-router', () => ({
  useParams: () => ({ releaseId: 'r1', documentType: documentTypeParam }),
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
    aiAssisted: false,
    aiMode: null,
    aiInstruction: null,
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
    if (url.startsWith(`/api/releases/r1/documents/${documentTypeParam}`)) {
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
    documentTypeParam = 'RELEASE_NOTES';
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

  describe('AI-assisted revisions', () => {
    const aiDoc = (overrides: Record<string, unknown> = {}) =>
      generatedDocument({
        aiAssisted: true,
        aiMode: 'CLEAN_UP',
        aiInstruction: 'Tidy <b>this</b> up',
        generatedBy: 'amr@acme.com',
        markdown: 'Body ![tracker](http://evil.example/x.png)',
        ...overrides,
      });

    it('aGeneratedRevisionShowsNoAiBannerAndKeepsImages', async () => {
      mockFetch({ documentResponse: () => json(200, generatedDocument({ markdown: '![pic](http://x.example/a.png)' })) });
      await renderRoute();

      expect(container.querySelector('#ai-revision-banner')).toBeNull();
      expect(container.textContent).not.toContain('Verify steps before use.');
      expect(container.querySelector('img')).not.toBeNull();
    });

    it('anAiRunbookRevisionShowsBothBannersAfterTheSupersededBannerWithImagesBlocked', async () => {
      documentTypeParam = 'RUNBOOK';
      searchParams = { revision: 2 };
      mockFetch({ documentResponse: () => json(200, aiDoc({ documentType: 'RUNBOOK', revision: 2, latestRevision: 3 })) });
      await renderRoute();

      const banner = container.querySelector('#ai-revision-banner') as HTMLElement;
      expect(banner.getAttribute('role')).toBe('note');
      expect(banner.tabIndex).toBe(-1);
      expect(banner.textContent).toContain('AI-assisted revision.');
      expect(banner.textContent).toContain('This revision was Cleaned up with the AI model and saved by amr@acme.com.');
      const details = banner.querySelector('details') as HTMLDetailsElement;
      expect(details.open).toBe(false);
      expect(details.textContent).toContain('Instruction used');
      expect(details.textContent).toContain('Tidy <b>this</b> up');
      expect(details.querySelector('b')).toBeNull();

      const text = container.textContent ?? '';
      expect(text).toContain('Verify steps before use.');
      expect(text.indexOf('superseded by')).toBeLessThan(text.indexOf('AI-assisted revision.'));
      expect(text.indexOf('AI-assisted revision.')).toBeLessThan(text.indexOf('Verify steps before use.'));
      expect(container.querySelector('img')).toBeNull();
      expect(text).toContain('[image: tracker]');
    });

    it('aCustomReleaseNotesRevisionSaysCustomAndHasNoVerifyBanner', async () => {
      mockFetch({ documentResponse: () => json(200, aiDoc({ aiMode: 'CUSTOM' })) });
      await renderRoute();

      expect(container.querySelector('#ai-revision-banner')?.textContent).toContain('edited with a custom instruction');
      expect(container.textContent).not.toContain('Verify steps before use.');
    });

    it('theBannersAreNotInsideTheRenderedDocumentBody', async () => {
      mockFetch({ documentResponse: () => json(200, aiDoc()) });
      await renderRoute();

      const panel = container.querySelector('[data-testid="panel"]') as HTMLElement;
      expect(panel.textContent).not.toContain('AI-assisted revision.');
    });

    it('disablesTheRevisionSelectWhileAResultIsOpen', async () => {
      mockFetch({});
      await renderRoute();
      const select = () => container.querySelector('select[aria-label="Revision"]') as HTMLSelectElement;
      expect(select().disabled).toBe(false);

      await act(async () => ([...container.querySelectorAll('button')].find((b) => b.textContent === 'open-result') as HTMLButtonElement).click());

      expect(select().disabled).toBe(true);
      expect(select().title).toBe('Save or discard the AI result first.');
    });

    it('onSavedClearsTheRevisionParamAndFocusesTheBannerOnceTheNewRevisionLoads', async () => {
      searchParams = { revision: 4 };
      mockFetch({
        documentResponse: () =>
          searchParams.revision === undefined
            ? json(200, aiDoc({ revision: 5, latestRevision: 5 }))
            : json(200, generatedDocument({ revision: 4, latestRevision: 4 })),
      });
      navigate.mockImplementation(() => {
        searchParams = {};
      });
      await renderRoute();
      expect(container.querySelector('#ai-revision-banner')).toBeNull();

      await act(async () => ([...container.querySelectorAll('button')].find((b) => b.textContent === 'save-result') as HTMLButtonElement).click());
      expect(navigate).toHaveBeenCalledWith(expect.objectContaining({ search: { revision: undefined } }));

      await renderRoute();
      const banner = container.querySelector('#ai-revision-banner');
      expect(banner).not.toBeNull();
      expect(document.activeElement).toBe(banner);
    });
  });
});
