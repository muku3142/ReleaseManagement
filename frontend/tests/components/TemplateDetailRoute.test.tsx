import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { TemplateDetailRoute } from '../../src/routes/TemplateDetailRoute';

const navigate = vi.fn();
vi.mock('@tanstack/react-router', () => ({
  useParams: () => ({ templateId: 't1' }),
  useNavigate: () => navigate,
  // Never blocked in this suite - the unsaved-changes-on-navigate dialog is plan 04-08's own
  // concern, not asserted by these tests, and useBlocker must still be callable unconditionally.
  useBlocker: () => ({
    status: 'idle',
    current: undefined,
    next: undefined,
    action: undefined,
    proceed: undefined,
    reset: undefined,
  }),
  Link: (props: { children?: ReactNode; to?: string }) => <a href={props.to}>{props.children}</a>,
}));

const CHROME = {
  coverEnabled: false,
  coverTitle: '',
  coverSubtitle: '',
  headerText: '',
  footerText: '',
  pageNumbersEnabled: false,
  logoRenderHeightMm: 30,
};

function makeTemplate(overrides: Record<string, unknown> = {}) {
  return {
    id: 't1',
    name: 'Standard release notes',
    documentType: 'RELEASE_NOTES',
    body: '# {{release.name}}',
    active: true,
    chrome: CHROME,
    groupingDimension: 'ISSUE_TYPE',
    logo: null,
    warnings: [],
    createdAt: '2026-08-01T00:00:00Z',
    createdBy: 'rm@acme.com',
    updatedAt: '2026-08-01T00:00:00Z',
    updatedBy: 'rm@acme.com',
    ...overrides,
  };
}

const CATALOG = {
  entries: [
    {
      scope: 'ROOT',
      token: 'release.name',
      kind: 'VARIABLE',
      description: "The release's name.",
      tags: ['Chrome + Body'],
    },
  ],
  logoLimits: { maxBytes: 2_000_000, maxWidth: 2000, maxHeight: 2000, contentTypes: ['image/png', 'image/jpeg'] },
};

const RELEASES = [
  {
    id: 'r1',
    name: 'Sprint 42',
    version: '2026.08',
    status: 'OPEN',
    jiraProjectKey: null,
    createdAt: '2026-08-05T00:00:00Z',
    createdBy: 'rm@acme.com',
    frozenAt: null,
    frozenBy: null,
    lastRefreshedAt: null,
    itemCount: 3,
  },
  {
    id: 'r2',
    name: 'Sprint 41',
    version: '2026.07',
    status: 'OPEN',
    jiraProjectKey: null,
    createdAt: '2026-07-05T00:00:00Z',
    createdBy: 'rm@acme.com',
    frozenAt: null,
    frozenBy: null,
    lastRefreshedAt: null,
    itemCount: 5,
  },
];

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
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
          <TemplateDetailRoute />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
}

function byText(text: string): HTMLButtonElement {
  const button = [...container.querySelectorAll('button')].find((b) => b.textContent === text);
  expect(button, `no button labelled "${text}"`).toBeDefined();
  return button!;
}

function bodyTextarea(): HTMLTextAreaElement {
  return container.querySelector<HTMLTextAreaElement>('textarea[aria-label="Template body"]')!;
}

function releaseSelect(): HTMLSelectElement {
  return container.querySelector<HTMLSelectElement>('select[aria-label="Preview against"]')!;
}

function groupBySelect(): HTMLSelectElement {
  return container.querySelector<HTMLSelectElement>('select[aria-label="Group by"]')!;
}

function logoHeightInput(): HTMLInputElement {
  return container.querySelector<HTMLInputElement>('input[type="number"]')!;
}

// React tracks the native value setter on controlled form elements to distinguish its own
// writes from external ones; assigning `.value` directly leaves that tracker believing nothing
// changed, so the dispatched 'input' event's target.value reads correctly but React's own
// re-render can be skipped. Using the underlying prototype setter avoids that.
function setNativeValue(element: HTMLInputElement | HTMLTextAreaElement, value: string) {
  const prototype = element instanceof HTMLTextAreaElement ? HTMLTextAreaElement.prototype : HTMLInputElement.prototype;
  const setter = Object.getOwnPropertyDescriptor(prototype, 'value')!.set!;
  setter.call(element, value);
  element.dispatchEvent(new Event('input', { bubbles: true }));
}

describe('TemplateDetailRoute', () => {
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
    navigate.mockReset();
  });

  it('shows "Loading template…" while the query is pending', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise(() => {}));

    await renderRoute();

    expect(container.textContent).toContain('Loading template…');
  });

  it('renders the loaded template name, subtitle and body in the textarea', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      return json(200, makeTemplate());
    });

    await renderRoute();

    expect(container.textContent).toContain('Standard release notes');
    expect(container.textContent).toContain('Release Notes template');
    expect(bodyTextarea().value).toBe('# {{release.name}}');
  });

  it('lays out the editor pane (45%) before the preview pane (55%)', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      return json(200, makeTemplate());
    });

    await renderRoute();

    const editorPane = [...container.querySelectorAll('div')].find((d) => d.className.includes('lg:basis-[45%]'));
    const previewPane = [...container.querySelectorAll('div')].find((d) => d.className.includes('lg:basis-[55%]'));
    expect(editorPane).toBeDefined();
    expect(previewPane).toBeDefined();
    expect(editorPane!.compareDocumentPosition(previewPane!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('shows the standing preview approximation notice', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      return json(200, makeTemplate());
    });

    await renderRoute();

    expect(container.textContent).toContain(
      'Preview approximation — actual page breaks, headers, and footers are determined when the document is generated.',
    );
  });

  it('populates the release picker with two releases, defaulting to the first', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      return json(200, makeTemplate());
    });

    await renderRoute();

    const select = releaseSelect();
    expect(select.options).toHaveLength(2);
    expect(select.value).toBe('r1');
    expect(select.disabled).toBe(false);
  });

  it('shows a disabled release picker and a fallback panel when no releases exist', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: [] });
      return json(200, makeTemplate());
    });

    await renderRoute();

    const select = releaseSelect();
    expect(select.disabled).toBe(true);
    expect(container.textContent).toContain('No releases available');
    expect(container.textContent).toContain('Create a release first to preview this template against real data.');
  });

  it('shows "Unsaved changes" while typing and hides it after a successful save', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      if (url.endsWith('/api/templates/t1/content') && init?.method === 'PUT') {
        return json(200, makeTemplate({ body: '# Updated' }));
      }
      return json(200, makeTemplate());
    });

    await renderRoute();
    expect(container.textContent).not.toContain('Unsaved changes');

    await act(async () => setNativeValue(bodyTextarea(), '# Updated'));
    expect(container.textContent).toContain('Unsaved changes');

    await act(async () => byText('Save template').click());
    await flush();

    expect(container.textContent).not.toContain('Unsaved changes');
  });

  it('groupByDefaultsToIssueTypeFromTheLoadedTemplate', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      return json(200, makeTemplate({ groupingDimension: 'ISSUE_TYPE' }));
    });

    await renderRoute();

    expect(groupBySelect().value).toBe('ISSUE_TYPE');
  });

  it('changingGroupByMarksTheTemplateDirty', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      return json(200, makeTemplate());
    });

    await renderRoute();
    expect(container.textContent).not.toContain('Unsaved changes');

    await act(async () => {
      groupBySelect().value = 'COMPONENT';
      groupBySelect().dispatchEvent(new Event('change', { bubbles: true }));
    });

    expect(container.textContent).toContain('Unsaved changes');
  });

  it('savingSendsTheSelectedGroupingDimension', async () => {
    let sentBody: string | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      if (url.endsWith('/api/templates/t1/content') && init?.method === 'PUT') {
        sentBody = String(init?.body);
        return json(200, makeTemplate({ groupingDimension: 'COMPONENT' }));
      }
      return json(200, makeTemplate());
    });

    await renderRoute();
    await act(async () => {
      groupBySelect().value = 'COMPONENT';
      groupBySelect().dispatchEvent(new Event('change', { bubbles: true }));
    });

    await act(async () => byText('Save template').click());
    await flush();

    expect(sentBody).toContain('"groupingDimension":"COMPONENT"');
  });

  it('savingSendsTheEditedCoverLogoHeight', async () => {
    let sentBody: string | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      if (url.endsWith('/api/templates/t1/content') && init?.method === 'PUT') {
        sentBody = String(init?.body);
        return json(200, makeTemplate());
      }
      return json(200, makeTemplate());
    });

    await renderRoute();
    // Only one tab is mounted at a time, so the chrome form does not exist until this click.
    await act(async () => byText('Chrome & Logo').click());

    const input = logoHeightInput();
    expect(input.min).toBe('5');
    expect(input.max).toBe('120');
    expect(input.value).toBe('30');

    await act(async () => setNativeValue(input, '65'));
    await act(async () => byText('Save template').click());
    await flush();

    expect(sentBody).toContain('"logoRenderHeightMm":65');
  });

  it('groupBySelectIsKeyboardReachableAndLabelled', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      return json(200, makeTemplate());
    });

    await renderRoute();

    const select = container.querySelector<HTMLSelectElement>('[aria-label="Group by"]');
    expect(select).not.toBeNull();
    expect(select?.tagName).toBe('SELECT');
  });

  it('renders a clean status banner with no amber classes when the save has no warnings', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      if (url.endsWith('/api/templates/t1/content') && init?.method === 'PUT') {
        return json(200, makeTemplate({ warnings: [] }));
      }
      return json(200, makeTemplate());
    });

    await renderRoute();
    await act(async () => byText('Save template').click());
    await flush();

    const banner = container.querySelector('[role="status"]');
    expect(banner?.textContent).toBe('Template saved.');
    expect(banner?.className).not.toContain('amber');
  });

  it('renders the warning banner with the count and reveals the verbatim entries on Show details', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      if (url.endsWith('/api/templates/t1/content') && init?.method === 'PUT') {
        return json(200, makeTemplate({ warnings: ['ticket.compnent — Body', 'nope — Cover title'] }));
      }
      return json(200, makeTemplate());
    });

    await renderRoute();
    await act(async () => byText('Save template').click());
    await flush();

    expect(container.textContent).toContain('Template saved with 2 unknown placeholder warnings.');

    await act(async () => byText('Show details').click());

    expect(container.textContent).toContain('ticket.compnent — Body');
    expect(container.textContent).toContain('nope — Cover title');
  });

  it('blocks the save with the backend TEMPLATE_MALFORMED detail text', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      if (url.endsWith('/api/templates/t1/content') && init?.method === 'PUT') {
        return json(422, {
          status: 422,
          reason: 'TEMPLATE_MALFORMED',
          detail: 'Cover title: Partials are not supported: {{>header}}.',
        });
      }
      return json(200, makeTemplate());
    });

    await renderRoute();
    await act(async () => byText('Save template').click());
    await flush();

    const alert = container.querySelector('[role="alert"][data-reason="TEMPLATE_MALFORMED"]');
    expect(alert?.textContent).toBe('Template not saved — Cover title: Partials are not supported: {{>header}}.');
  });

  it('blocks the save with the render-bound-exceeded copy', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      if (url.endsWith('/api/templates/t1/content') && init?.method === 'PUT') {
        return json(422, { status: 422, reason: 'RENDER_BOUND_EXCEEDED' });
      }
      return json(200, makeTemplate());
    });

    await renderRoute();
    await act(async () => byText('Save template').click());
    await flush();

    const alert = container.querySelector('[role="alert"][data-reason="RENDER_BOUND_EXCEEDED"]');
    expect(alert?.textContent).toContain(
      'Simplify the template (fewer nested sections or shorter loops) and try again.',
    );
  });

  it('shows the Active pill and no Make active button for an active template', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      return json(200, makeTemplate({ active: true }));
    });

    await renderRoute();

    const pill = [...container.querySelectorAll('span')].find((s) => s.textContent === 'Active');
    expect(pill).toBeDefined();
    expect([...container.querySelectorAll('button')].some((b) => b.textContent === 'Make active')).toBe(false);
  });

  it('shows Make active and no pill for an inactive template', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      return json(200, makeTemplate({ active: false }));
    });

    await renderRoute();

    const pill = [...container.querySelectorAll('span')].find((s) => s.textContent === 'Active');
    expect(pill).toBeUndefined();
    expect(byText('Make active')).toBeDefined();
  });

  it('renames independently of the content save - no PUT /content is issued', async () => {
    const requested: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      requested.push(`${init?.method ?? 'GET'} ${url}`);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      if (url.endsWith('/api/templates/t1/name') && init?.method === 'PUT') {
        return json(200, makeTemplate({ name: 'Renamed template' }));
      }
      return json(200, makeTemplate());
    });

    await renderRoute();
    await act(async () => byText('Rename').click());

    const input = container.querySelector<HTMLInputElement>('input[aria-label="Template name"]')!;
    await act(async () => setNativeValue(input, 'Renamed template'));
    await act(async () => {
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }));
    });
    await flush();

    expect(requested.some((r) => r.endsWith('PUT /api/templates/t1/name'))).toBe(true);
    expect(requested.some((r) => r.endsWith('PUT /api/templates/t1/content'))).toBe(false);
    expect(container.textContent).toContain('Renamed template');
  });

  it('opens the placeholder drawer via the toolbar toggle, tints the button, and closes on a second click', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      if (url.endsWith('/api/templates/catalog')) return json(200, CATALOG);
      return json(200, makeTemplate());
    });

    await renderRoute();

    const toggle = byText('Placeholders');
    expect(toggle.className).not.toContain('bg-blue-50');
    expect(container.querySelector('[aria-label="Placeholders"]')).toBeNull();

    await act(async () => toggle.click());
    expect(toggle.className).toContain('bg-blue-50');
    expect(container.querySelector('[aria-label="Placeholders"]')).not.toBeNull();

    await act(async () => toggle.click());
    expect(toggle.className).not.toContain('bg-blue-50');
    expect(container.querySelector('[aria-label="Placeholders"]')).toBeNull();
  });

  it('inserts {{release.name}} at the last recorded body caret and keeps the drawer open', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/api/releases')) return json(200, { releases: RELEASES });
      if (url.endsWith('/api/templates/catalog')) return json(200, CATALOG);
      return json(200, makeTemplate({ body: '0123456789' }));
    });

    await renderRoute();

    // React's onSelect is not a direct alias for the native 'select' event (it is synthesized
    // from several other native events), so a raw dispatchEvent('select') in jsdom does not
    // reach it reliably. onBlur uses the standard native event and shares the same
    // rememberSelection handler, so focus + set the caret + blur is the deterministic way to
    // seed the "last focused field" state in a test.
    const textarea = bodyTextarea();
    textarea.focus();
    textarea.setSelectionRange(5, 5);
    await act(async () => {
      textarea.blur();
    });

    await act(async () => byText('Placeholders').click());

    const row = [...container.querySelectorAll('button')].find(
      (b) => b.querySelector('.font-mono')?.textContent === 'release.name',
    );
    expect(row, 'no release.name row found in the drawer').toBeDefined();

    await act(async () => {
      row!.click();
      await new Promise<void>((resolve) => requestAnimationFrame(() => resolve()));
    });

    expect(bodyTextarea().value).toBe('01234{{release.name}}56789');
    expect(container.querySelector('[aria-label="Placeholders"]')).not.toBeNull();
  });
});
