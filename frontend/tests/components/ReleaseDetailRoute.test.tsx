import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReleaseDetailRoute } from '../../src/routes/ReleaseDetailRoute';

const navigate = vi.fn();
vi.mock('@tanstack/react-router', () => ({
  useParams: () => ({ releaseId: 'r1' }),
  useSearch: () => ({}),
  useNavigate: () => navigate,
  Link: (props: { children?: ReactNode }) => <a href="/releases">{props.children}</a>,
}));

const RELEASE = {
  id: 'r1',
  name: 'Sprint 42',
  version: '2026.08',
  status: 'OPEN',
  jiraProjectKey: 'CNV',
  createdAt: '2026-08-05T00:00:00Z',
  createdBy: 'rm@acme.com',
  frozenAt: null,
  frozenBy: null,
  lastRefreshedAt: null,
  items: [],
  manualItems: [
    {
      id: 'm1',
      title: 'Run migration',
      description: 'Run scripts/migrate.sh',
      category: 'DATABASE_SCRIPT',
      addedAt: '2026-08-05T00:00:00Z',
    },
  ],
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
          <ReleaseDetailRoute />
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

describe('ReleaseDetailRoute', () => {
  beforeAll(() => {
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

  // showModal() makes the rest of the document inert, so an error rendered in the page content
  // sits behind the backdrop where it cannot be read - on the two irreversible actions the
  // dialog exists to make deliberate.
  it('closes the freeze confirmation on failure so the error is readable', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/freeze')) return json(409, { status: 409, reason: 'RELEASE_FROZEN' });
      return json(200, RELEASE);
    });

    await renderRoute();
    await act(async () => byText('Freeze release').click());
    expect(container.querySelector('dialog')?.open).toBe(true);

    const confirmButton = [...container.querySelectorAll('dialog button')].find(
      (b) => b.textContent === 'Freeze release',
    ) as HTMLButtonElement;
    await act(async () => confirmButton.click());
    await flush();

    expect([...container.querySelectorAll('dialog')].every((d) => !d.open)).toBe(true);
    expect(container.querySelector('[data-reason="RELEASE_FROZEN"]')?.textContent).toBe(
      'This release is frozen and can no longer be changed.',
    );
  });

  // release_manual_item has no uniqueness constraint, so submitting the prefilled form silently
  // created a second copy of the item that was being edited.
  it('reopens the manual-item form empty after an edit was abandoned', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(200, RELEASE));

    await renderRoute();
    await act(async () => byText('Edit').click());
    expect(container.querySelector<HTMLInputElement>('#manualTitle')!.value).toBe('Run migration');
    expect(byText('Save changes')).toBeDefined();

    await act(async () => byText('+ Add manual item').click());
    expect(container.querySelector('#manualTitle')).toBeNull();

    await act(async () => byText('+ Add manual item').click());

    expect(container.querySelector<HTMLInputElement>('#manualTitle')!.value).toBe('');
    expect(container.querySelector<HTMLTextAreaElement>('#manualDescription')!.value).toBe('');
    expect(container.querySelector<HTMLSelectElement>('#manualCategory')!.value).toBe('OTHER');
    expect(byText('Add item')).toBeDefined();
  });

  it('does not refetch the release it just deleted', async () => {
    const requested: string[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      requested.push(`${init?.method ?? 'GET'} ${String(input)}`);
      if (init?.method === 'DELETE') return new Response(null, { status: 204 });
      return json(200, RELEASE);
    });

    await renderRoute();
    expect(requested.filter((r) => r === 'GET /api/releases/r1')).toHaveLength(1);

    await act(async () => byText('Delete release').click());
    const confirmButton = [...container.querySelectorAll('dialog button')].find(
      (b) => b.textContent === 'Delete release',
    ) as HTMLButtonElement;
    await act(async () => confirmButton.click());
    await flush();

    expect(navigate).toHaveBeenCalledWith({ to: '/releases' });
    expect(requested.filter((r) => r === 'GET /api/releases/r1')).toHaveLength(1);
    expect(container.querySelector('[data-reason="RELEASE_NOT_FOUND"]')).toBeNull();
  });

  it('closes the delete confirmation on failure so the error is readable', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (_input, init) => {
      if (init?.method === 'DELETE') return json(409, { status: 409, reason: 'RELEASE_FROZEN' });
      return json(200, RELEASE);
    });

    await renderRoute();
    await act(async () => byText('Delete release').click());

    const confirmButton = [...container.querySelectorAll('dialog button')].find(
      (b) => b.textContent === 'Delete release',
    ) as HTMLButtonElement;
    await act(async () => confirmButton.click());
    await flush();

    expect([...container.querySelectorAll('dialog')].every((d) => !d.open)).toBe(true);
    expect(container.querySelector('[data-reason="RELEASE_FROZEN"]')).not.toBeNull();
    expect(navigate).not.toHaveBeenCalled();
  });

  // The /releases readiness links (05-10) point at `#documents` on this page - if the section
  // moves or drops its id, those links silently land nowhere.
  it('documentsSectionIsRenderedLastWithItsAnchorId', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.startsWith('/api/documents/readiness')) return json(200, { entries: [] });
      if (url.includes('/revisions')) return json(200, { revisions: [] });
      return json(200, RELEASE);
    });

    await renderRoute();
    await flush();

    const section = container.querySelector('#documents');
    expect(section).not.toBeNull();
    const wrapper = container.firstElementChild;
    expect(wrapper?.lastElementChild).toBe(section);
  });
});
