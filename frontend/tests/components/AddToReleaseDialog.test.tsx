import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AddToReleaseDialog } from '../../src/components/AddToReleaseDialog';

// The component is only reachable through TicketsRoute, which is inside the router. Only
// useNavigate is used here.
const navigate = vi.fn();
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
  Link: (props: { children?: ReactNode; to?: string }) => <a href={props.to}>{props.children}</a>,
}));

const ALPHA = { id: 'p1', name: 'Alpha', archived: false, hasInstalls: false };
const BETA = { id: 'p2', name: 'Beta', archived: false, hasInstalls: false };

function release(id: string, name: string, version: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    name,
    version,
    status: 'OPEN',
    jiraProjectKey: 'ABC',
    createdAt: '2026-08-05T00:00:00Z',
    createdBy: 'rm@example.com',
    frozenAt: null,
    frozenBy: null,
    lastRefreshedAt: null,
    itemCount: 0,
    products: [ALPHA],
    ...overrides,
  };
}

const ALPHA_OPEN = release('r1', 'Sprint 42', '2026.08');
const RELEASES = [
  ALPHA_OPEN,
  release('r2', 'Beta Train', '2026.08', { products: [BETA] }),
  release('r3', 'Old Alpha', '2026.07', { status: 'FROZEN' }),
];

const CREATED = {
  release: { ...release('r9', 'Sprint 43', '2026.09'), items: [], manualItems: [], unfreezeBlockedReason: null, packagesUrl: null },
  added: 2,
  alreadyPresent: 0,
  notFound: 0,
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

type Handler = (url: string, init?: RequestInit) => Promise<Response | undefined> | Response | undefined;

// Returns every POST /api/releases body. A handler can answer a request first; anything else
// gets the releases list.
function mockApi(handler: Handler = () => undefined, releases: unknown[] = RELEASES): unknown[] {
  const bodies: unknown[] = [];
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    if (url === '/api/releases' && init?.method === 'POST') bodies.push(JSON.parse(String(init.body)));
    const answer = await handler(url, init);
    if (answer) return answer;
    return json(200, { releases });
  });
  return bodies;
}

let container: HTMLDivElement;
let root: Root;
let client: QueryClient;

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

const LOOSE_GROUPS = [{ ticketKeys: ['ABC-1', 'ABC-2'] }];

async function renderDialog(
  props: { open?: boolean; startWithForm?: boolean; groups?: { ticketKeys: string[]; bundle?: string }[] } = {},
) {
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <AddToReleaseDialog
            open={props.open ?? true}
            groups={props.groups ?? LOOSE_GROUPS}
            productId="p1"
            productName="Alpha"
            startWithForm={props.startWithForm}
            onClose={() => {}}
          />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
}

function findButton(text: string): HTMLButtonElement | undefined {
  return [...container.querySelectorAll('button')].find((b) => b.textContent === text);
}

function byText(text: string): HTMLButtonElement {
  const button = findButton(text);
  expect(button, `no button labelled "${text}"`).toBeDefined();
  return button!;
}

// The native setter bypasses React's value tracker so the input event is seen as a change.
const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;

async function fillForm(name: string, version: string) {
  const nameInput = container.querySelector<HTMLInputElement>('#releaseName')!;
  const versionInput = container.querySelector<HTMLInputElement>('#releaseVersion')!;
  await act(async () => {
    setValue.call(nameInput, name);
    nameInput.dispatchEvent(new Event('input', { bubbles: true }));
    setValue.call(versionInput, version);
    versionInput.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

async function submit() {
  await act(async () => {
    container
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
  await flush();
}

async function submitCreateForm(name: string, version: string) {
  if (findButton('New release…')) {
    await act(async () => byText('New release…').click());
    await flush();
  }
  await fillForm(name, version);
  await submit();
}

describe('AddToReleaseDialog', () => {
  beforeAll(() => {
    // jsdom implements <dialog> but not showModal/close, and the component calls both.
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
    client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
    navigate.mockReset();
  });

  describe('add mode', () => {
    it('names the product and lists only open releases of that product', async () => {
      mockApi();
      await renderDialog();

      expect(container.querySelector('h2')?.textContent).toBe('Add 2 ticket(s) to a release');
      expect(container.textContent).toContain('Product: Alpha');
      const addButtons = [...container.querySelectorAll('li button')];
      expect(addButtons.map((b) => b.getAttribute('aria-label'))).toEqual(['Add to Sprint 42 2026.08']);
      expect(addButtons[0].textContent).toBe('Add');
      expect(container.textContent).not.toContain('Beta Train');
      expect(container.textContent).not.toContain('Old Alpha');
      expect(container.querySelector('#releaseName')).toBeNull();
    });

    it('opens the form with the no-open-releases line when the product has none', async () => {
      mockApi(() => undefined, [RELEASES[1]]);
      await renderDialog();

      expect(container.textContent).toContain('Alpha has no open releases. Create one below.');
      expect(container.querySelector('#releaseName')).not.toBeNull();
      expect(byText('Create & add')).toBeDefined();
    });

    it('renders the RELEASE_FROZEN message when adding to a release frozen in another tab', async () => {
      mockApi((url) => (url.endsWith('/items') ? json(409, { status: 409, reason: 'RELEASE_FROZEN' }) : undefined));

      await renderDialog();
      await act(async () => byText('Add').click());
      await flush();

      const alert = container.querySelector('[data-reason="RELEASE_FROZEN"]');
      expect(alert?.textContent).toBe('This release is frozen and can no longer be changed.');
      expect(navigate).not.toHaveBeenCalled();
    });

    it('renders the JIRA_UNREACHABLE message when the add path cannot reach Jira', async () => {
      mockApi((url) => (url.endsWith('/items') ? json(503, { status: 503, reason: 'JIRA_UNREACHABLE' }) : undefined));

      await renderDialog();
      await act(async () => byText('Add').click());
      await flush();

      expect(container.querySelector('[data-reason="JIRA_UNREACHABLE"]')?.textContent).toBe(
        'The configured Jira site could not be reached. Check the site URL and your network, then try again.',
      );
    });

    // The add goes through a Jira fetch and can take seconds. With no disabled state the silence
    // invites a second click, which is a second POST /items for the same selection.
    it('disables every Add button while the add is in flight', async () => {
      let releaseAdd: (() => void) | undefined;
      mockApi(async (url) => {
        if (!url.endsWith('/items')) return undefined;
        await new Promise<void>((resolve) => {
          releaseAdd = resolve;
        });
        return json(409, { status: 409, reason: 'RELEASE_FROZEN' });
      });

      await renderDialog();
      await act(async () => byText('Add').click());
      await flush();

      expect(byText('Add').disabled).toBe(true);

      await act(async () => releaseAdd!());
      await flush();

      expect(byText('Add').disabled).toBe(false);
    });

    it('sends productId, not productIds or jiraProjectKey, from Create & add', async () => {
      const bodies = mockApi((url, init) =>
        url === '/api/releases' && init?.method === 'POST' ? json(201, CREATED) : undefined,
      );

      await renderDialog();
      await submitCreateForm(' Sprint 43 ', '2026.09');

      expect(bodies).toEqual([
        { name: 'Sprint 43', version: '2026.09', ticketKeys: ['ABC-1', 'ABC-2'], productId: 'p1' },
      ]);
      expect(navigate).toHaveBeenCalledWith(
        expect.objectContaining({ to: '/releases/$releaseId', params: { releaseId: 'r9' } }),
      );
    });

    it('reopens with a clean, collapsed form rather than the previous attempt values and error', async () => {
      mockApi((url, init) =>
        url === '/api/releases' && init?.method === 'POST'
          ? json(409, { status: 409, reason: 'RELEASE_DUPLICATE' })
          : undefined,
      );

      await renderDialog();
      await submitCreateForm('Sprint 43', '2026.09');
      expect(container.querySelector('[data-reason="RELEASE_DUPLICATE"]')?.textContent).toBe(
        "A release named 'Sprint 43' version '2026.09' already exists. Choose a different name or version.",
      );

      await renderDialog({ open: false });
      await renderDialog({ open: true });

      expect(container.querySelector('[data-reason="RELEASE_DUPLICATE"]')).toBeNull();
      expect(container.querySelector('#releaseName')).toBeNull();

      await act(async () => byText('New release…').click());
      expect(container.querySelector<HTMLInputElement>('#releaseName')!.value).toBe('');
      expect(container.querySelector<HTMLInputElement>('#releaseVersion')!.value).toBe('');
    });
  });

  describe('startWithForm', () => {
    it('opens straight on the create form with no release list or toggle, and focuses the name', async () => {
      mockApi();
      await renderDialog({ startWithForm: true });

      expect(container.querySelector('h2')?.textContent).toBe('New release with 2 ticket(s)');
      expect(container.textContent).toContain('Product: Alpha');
      expect(container.querySelector('li')).toBeNull();
      expect(findButton('New release…')).toBeUndefined();
      expect(document.activeElement).toBe(container.querySelector('#releaseName'));
      expect(byText('Create release')).toBeDefined();
    });

    it('keeps Create release disabled until both name and version are filled', async () => {
      mockApi();
      await renderDialog({ startWithForm: true });

      expect(byText('Create release').disabled).toBe(true);
      await fillForm('Sprint 43', '   ');
      expect(byText('Create release').disabled).toBe(true);
      await fillForm('Sprint 43', '2026.09');
      expect(byText('Create release').disabled).toBe(false);
    });

    it('shows the pending state while creating, then lands on the new release', async () => {
      let finish: (() => void) | undefined;
      const bodies = mockApi(async (url, init) => {
        if (url !== '/api/releases' || init?.method !== 'POST') return undefined;
        await new Promise<void>((resolve) => {
          finish = resolve;
        });
        return json(201, CREATED);
      });

      await renderDialog({ startWithForm: true });
      await fillForm('Sprint 43', '2026.09');
      await submit();

      const pending = container.querySelector<HTMLButtonElement>('button[type="submit"]')!;
      expect(pending.textContent).toBe('Creating release…');
      expect(pending.disabled).toBe(true);
      expect(pending.querySelector('[data-slot="spinner"]')).not.toBeNull();

      await act(async () => finish!());
      await flush();

      expect(bodies).toEqual([
        { name: 'Sprint 43', version: '2026.09', ticketKeys: ['ABC-1', 'ABC-2'], productId: 'p1' },
      ]);
      expect(navigate).toHaveBeenCalledWith(
        expect.objectContaining({
          to: '/releases/$releaseId',
          params: { releaseId: 'r9' },
          search: { added: 2, alreadyPresent: 0, notFound: 0 },
        }),
      );
    });

    it.each([
      ['PRODUCT_NO_JIRA_PROJECT', 'This product has no Jira project. Add one in Portfolio › Products.'],
      ['PRODUCT_ARCHIVED', undefined],
    ])('renders the %s backstop inline', async (reason, copy) => {
      mockApi((url, init) =>
        url === '/api/releases' && init?.method === 'POST' ? json(422, { status: 422, reason }) : undefined,
      );

      await renderDialog({ startWithForm: true });
      await fillForm('Sprint 43', '2026.09');
      await submit();

      const alert = container.querySelector(`[data-reason="${reason}"]`);
      expect(alert?.getAttribute('role')).toBe('alert');
      expect(alert?.textContent).toBeTruthy();
      if (copy) expect(alert?.textContent).toBe(copy);
      expect(navigate).not.toHaveBeenCalled();
    });

    it('renders a ticketKeys validation failure the form has no field of its own for', async () => {
      mockApi((url, init) =>
        url === '/api/releases' && init?.method === 'POST'
          ? json(400, {
              status: 400,
              reason: 'VALIDATION_FAILED',
              fieldErrors: { 'ticketKeys[500]': 'size must be between 0 and 500' },
            })
          : undefined,
      );

      await renderDialog({ startWithForm: true });
      await fillForm('Sprint 43', '2026.09');
      await submit();

      expect(container.querySelector('[data-reason="VALIDATION_FAILED"]')?.textContent).toBe(
        'size must be between 0 and 500',
      );
    });
  });

  describe('ticket groups (K-06)', () => {
    const GROUPS = [{ ticketKeys: ['ABC-1'] }, { ticketKeys: ['ABC-2', 'ABC-3'], bundle: 'Payments' }];

    it('has no free-text bundle input', async () => {
      mockApi();
      await renderDialog();
      expect(container.querySelector('#bundleName')).toBeNull();

      await renderDialog({ open: false });
      await renderDialog({ startWithForm: true });
      expect(container.querySelector('#bundleName')).toBeNull();
    });

    it('counts every ticket of every group in the heading', async () => {
      mockApi();
      await renderDialog({ groups: GROUPS });
      expect(container.querySelector('h2')?.textContent).toBe('Add 3 ticket(s) to a release');
    });

    it('sends one items request per group with Add, naming each bundle', async () => {
      const itemBodies: unknown[] = [];
      mockApi((url, init) => {
        if (!url.endsWith('/items')) return undefined;
        itemBodies.push(JSON.parse(String(init?.body)));
        return json(200, CREATED);
      });

      await renderDialog({ groups: GROUPS });
      await act(async () => byText('Add').click());
      await flush();

      expect(itemBodies).toEqual([
        { ticketKeys: ['ABC-1'] },
        { ticketKeys: ['ABC-2', 'ABC-3'], bundle: 'Payments' },
      ]);
    });

    it('creates the release with the first group, then adds the remaining groups to it', async () => {
      const requests: { url: string; body: unknown }[] = [];
      mockApi((url, init) => {
        if (init?.method !== 'POST') return undefined;
        requests.push({ url, body: JSON.parse(String(init.body)) });
        return json(url === '/api/releases' ? 201 : 200, CREATED);
      });

      await renderDialog({ startWithForm: true, groups: GROUPS });
      await fillForm('Sprint 43', '2026.09');
      await submit();

      expect(requests).toEqual([
        { url: '/api/releases', body: { name: 'Sprint 43', version: '2026.09', ticketKeys: ['ABC-1'], productId: 'p1' } },
        { url: '/api/releases/r9/items', body: { ticketKeys: ['ABC-2', 'ABC-3'], bundle: 'Payments' } },
      ]);
      expect(navigate).toHaveBeenCalledWith(
        expect.objectContaining({ params: { releaseId: 'r9' }, search: { added: 4, alreadyPresent: 0, notFound: 0 } }),
      );
    });
  });
});
