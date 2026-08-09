import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { TemplatesRoute } from '../../src/routes/TemplatesRoute';

const navigate = vi.fn();
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
  Link: (props: { children?: ReactNode; to?: string }) => <a href={props.to}>{props.children}</a>,
}));

const RELEASE_NOTES_ACTIVE = {
  id: 't1',
  name: 'Standard release notes',
  documentType: 'RELEASE_NOTES',
  active: true,
  hasLogo: false,
  warningCount: 0,
  updatedAt: '2026-08-09T00:00:00Z',
};

const APPROVAL_WITH_WARNINGS = {
  id: 't2',
  name: 'Change approval',
  documentType: 'APPROVAL',
  active: false,
  hasLogo: false,
  warningCount: 2,
  updatedAt: '2026-08-09T00:00:00Z',
};

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
          <TemplatesRoute />
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

describe('TemplatesRoute', () => {
  beforeAll(() => {
    // jsdom implements <dialog> but not showModal/close, and the dialogs used here call both.
    HTMLDialogElement.prototype.showModal = function showModal(this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close = function close(this: HTMLDialogElement) {
      this.open = false;
    };
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

  it('shows "Loading templates…" while the query is pending', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise(() => {}));

    await renderRoute();

    expect(container.textContent).toContain('Loading templates…');
  });

  it('renders the three section headings in fixed order Release Notes → Runbook → Approval', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      json(200, { templates: [RELEASE_NOTES_ACTIVE, APPROVAL_WITH_WARNINGS] }),
    );

    await renderRoute();

    const text = container.textContent ?? '';
    const releaseNotesIndex = text.indexOf('Release Notes templates');
    const runbookIndex = text.indexOf('Runbook templates');
    const approvalIndex = text.indexOf('Approval templates');
    expect(releaseNotesIndex).toBeGreaterThan(-1);
    expect(runbookIndex).toBeGreaterThan(releaseNotesIndex);
    expect(approvalIndex).toBeGreaterThan(runbookIndex);
  });

  it("renders the active template's row with an Active pill using bg-gray-800", async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(200, { templates: [RELEASE_NOTES_ACTIVE] }));

    await renderRoute();

    const pill = [...container.querySelectorAll('span')].find((s) => s.textContent === 'Active');
    expect(pill).toBeDefined();
    expect(pill!.className).toContain('bg-gray-800');
  });

  it('renders the unresolved-placeholder warning dot only for a template with warningCount > 0', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      json(200, { templates: [RELEASE_NOTES_ACTIVE, APPROVAL_WITH_WARNINGS] }),
    );

    await renderRoute();

    const dots = container.querySelectorAll('[aria-label="Has unresolved placeholder warnings"]');
    expect(dots).toHaveLength(1);
  });

  it('renders the per-section empty state for a document type with no templates', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(200, { templates: [RELEASE_NOTES_ACTIVE] }));

    await renderRoute();

    expect(container.textContent).toContain('No Runbook templates yet.');
    expect(container.textContent).toContain('Create one to get started.');
  });

  it('opens the delete dialog for a non-active template without the active-template warning sentence', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(200, { templates: [APPROVAL_WITH_WARNINGS] }));

    await renderRoute();
    await act(async () => byText('Delete').click());

    const openDialog = [...container.querySelectorAll('dialog')].find((d) => d.open);
    const dialogText = openDialog?.textContent ?? '';
    expect(dialogText).toContain('Documents already generated from it are not affected.');
    expect(dialogText).not.toContain('Deleting it will leave no active template');
  });

  it('opens the delete dialog for the active template with the extra active-template warning sentence', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(200, { templates: [RELEASE_NOTES_ACTIVE] }));

    await renderRoute();
    await act(async () => byText('Delete').click());

    const openDialog = [...container.querySelectorAll('dialog')].find((d) => d.open);
    const dialogText = openDialog?.textContent ?? '';
    expect(dialogText).toContain(
      'This is the active Release Notes template. Deleting it will leave no active template for this document type until you mark another one active.',
    );
  });

  it('renders an alert with data-reason and a Try again button when the templates query rejects', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(500, { status: 500, reason: 'INTERNAL_ERROR' }));

    await renderRoute();
    await flush();

    const alert = container.querySelector('[role="alert"][data-reason="INTERNAL_ERROR"]');
    expect(alert).not.toBeNull();
    expect(byText('Try again')).toBeDefined();
  });

  it('opens the new-template dialog for the Runbook section with the "Create template" CTA', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(200, { templates: [] }));

    await renderRoute();
    const newTemplateButtons = [...container.querySelectorAll('button')].filter(
      (b) => b.textContent === '+ New template',
    );
    // Order matches DOCUMENT_TYPES: Release Notes, Runbook, Approval.
    await act(async () => newTemplateButtons[1].click());

    const dialog = container.querySelector('dialog');
    expect(dialog?.textContent).toContain('New Runbook template');
    const createButton = [...container.querySelectorAll('dialog button')].find(
      (b) => b.textContent === 'Create template',
    );
    expect(createButton).toBeDefined();
  });
});
