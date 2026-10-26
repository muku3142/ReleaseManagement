import { StrictMode, act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AddToReleaseDialog } from '../../src/components/AddToReleaseDialog';

// The component is only reachable through TicketsRoute, which is inside the router. Only
// useNavigate is used here, and this suite asserts what happens when navigation never runs.
const navigate = vi.fn();
vi.mock('@tanstack/react-router', () => ({
  useNavigate: () => navigate,
  Link: (props: { children?: ReactNode; to?: string }) => <a href={props.to}>{props.children}</a>,
}));

const OPEN_RELEASE = {
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
  itemCount: 0,
};

const PRODUCTS = {
  products: [
    { id: 'p1', name: 'Core', archived: false, clientCount: 0, releaseCount: 0 },
    { id: 'p2', name: 'Legacy', archived: true, clientCount: 0, releaseCount: 0 },
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
let client: QueryClient;

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderDialog(open = true) {
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <AddToReleaseDialog
            open={open}
            ticketKeys={['CNV-1', 'CNV-2']}
            jiraProjectKey="CNV"
            onClose={() => {}}
          />
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

function productCheckbox(name: string): HTMLInputElement | undefined {
  const label = [...container.querySelectorAll('label')].find((l) => l.textContent === name);
  return label?.querySelector('input') ?? undefined;
}

async function submitCreateForm(name: string, version: string, tickProduct = true) {
  await act(async () => byText('New release…').click());
  await flush();
  if (tickProduct) await act(async () => productCheckbox('Core')!.click());
  const nameInput = container.querySelector<HTMLInputElement>('#releaseName')!;
  const versionInput = container.querySelector<HTMLInputElement>('#releaseVersion')!;
  await act(async () => {
    nameInput.value = name;
    nameInput.dispatchEvent(new Event('input', { bubbles: true }));
    versionInput.value = version;
    versionInput.dispatchEvent(new Event('input', { bubbles: true }));
  });
  await act(async () => {
    container
      .querySelector('form')!
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
  await flush();
}

describe('AddToReleaseDialog error surfacing', () => {
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

  it('renders the RELEASE_FROZEN message when adding to a release frozen in another tab', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/items')) return json(409, { status: 409, reason: 'RELEASE_FROZEN' });
      if (url === '/api/products') return json(200, PRODUCTS);
      return json(200, { releases: [OPEN_RELEASE] });
    });

    await renderDialog();
    await act(async () => byText('Add').click());
    await flush();

    const alert = container.querySelector('[data-reason="RELEASE_FROZEN"]');
    expect(alert?.textContent).toBe('This release is frozen and can no longer be changed.');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('renders the JIRA_UNREACHABLE message when the add path cannot reach Jira', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/items')) return json(503, { status: 503, reason: 'JIRA_UNREACHABLE' });
      if (url === '/api/products') return json(200, PRODUCTS);
      return json(200, { releases: [OPEN_RELEASE] });
    });

    await renderDialog();
    await act(async () => byText('Add').click());
    await flush();

    expect(container.querySelector('[data-reason="JIRA_UNREACHABLE"]')?.textContent).toBe(
      'The configured Jira site could not be reached. Check the site URL and your network, then try again.',
    );
  });

  it('renders a ticketKeys validation failure the create form has no field of its own for', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url === '/api/releases' && init?.method === 'POST') {
        return json(400, {
          status: 400,
          reason: 'VALIDATION_FAILED',
          fieldErrors: { 'ticketKeys[500]': 'size must be between 0 and 500' },
        });
      }
      if (url === '/api/products') return json(200, PRODUCTS);
      return json(200, { releases: [OPEN_RELEASE] });
    });

    await renderDialog();
    await submitCreateForm('Sprint 43', '2026.09');

    expect(container.querySelector('[data-reason="VALIDATION_FAILED"]')?.textContent).toBe(
      'size must be between 0 and 500',
    );
  });

  // The add goes through a Jira fetch and can take seconds. With no disabled state the silence
  // invites a second click, which is a second POST /items for the same selection.
  it('disables every Add button while the add is in flight', async () => {
    let releaseAdd: (() => void) | undefined;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
      const url = String(input);
      if (url.endsWith('/items')) {
        await new Promise<void>((resolve) => {
          releaseAdd = resolve;
        });
        return json(409, { status: 409, reason: 'RELEASE_FROZEN' });
      }
      if (url === '/api/products') return json(200, PRODUCTS);
      return json(200, { releases: [OPEN_RELEASE] });
    });

    await renderDialog();
    await act(async () => byText('Add').click());
    await flush();

    expect(byText('Add').disabled).toBe(true);

    await act(async () => releaseAdd!());
    await flush();

    expect(byText('Add').disabled).toBe(false);
  });

  it('reopens with a clean form rather than the previous attempt values and error', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url === '/api/releases' && init?.method === 'POST') {
        return json(409, { status: 409, reason: 'RELEASE_DUPLICATE' });
      }
      if (url === '/api/products') return json(200, PRODUCTS);
      return json(200, { releases: [OPEN_RELEASE] });
    });

    await renderDialog();
    await submitCreateForm('Sprint 43', '2026.09');
    expect(container.querySelector('[data-reason="RELEASE_DUPLICATE"]')).not.toBeNull();

    await renderDialog(false);
    await renderDialog(true);

    expect(container.querySelector('[data-reason="RELEASE_DUPLICATE"]')).toBeNull();
    expect(container.querySelector('#releaseName')).toBeNull();

    await act(async () => byText('New release…').click());
    expect(container.querySelector<HTMLInputElement>('#releaseName')!.value).toBe('');
    expect(container.querySelector<HTMLInputElement>('#releaseVersion')!.value).toBe('');
    await flush();
    expect(productCheckbox('Core')!.checked).toBe(false);
  });

  it('lists active products only and sends the ticked ids in the POST body', async () => {
    const bodies: unknown[] = [];
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url === '/api/releases' && init?.method === 'POST') {
        bodies.push(JSON.parse(String(init.body)));
        return json(409, { status: 409, reason: 'RELEASE_DUPLICATE' });
      }
      if (url === '/api/products') return json(200, PRODUCTS);
      return json(200, { releases: [OPEN_RELEASE] });
    });

    await renderDialog();
    await submitCreateForm('Sprint 43', '2026.09');

    expect(productCheckbox('Legacy')).toBeUndefined();
    expect(bodies).toHaveLength(1);
    expect((bodies[0] as { productIds: string[] }).productIds).toEqual(['p1']);
  });

  it('keeps Create & add disabled until a product is ticked', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) =>
      String(input) === '/api/products' ? json(200, PRODUCTS) : json(200, { releases: [OPEN_RELEASE] }),
    );

    await renderDialog();
    await act(async () => byText('New release…').click());
    await flush();
    const nameInput = container.querySelector<HTMLInputElement>('#releaseName')!;
    const versionInput = container.querySelector<HTMLInputElement>('#releaseVersion')!;
    // The native setter bypasses React's value tracker so the input event is seen as a change.
    const setValue = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
    await act(async () => {
      setValue.call(nameInput, 'Sprint 43');
      nameInput.dispatchEvent(new Event('input', { bubbles: true }));
      setValue.call(versionInput, '2026.09');
      versionInput.dispatchEvent(new Event('input', { bubbles: true }));
    });
    expect(byText('Create & add').disabled).toBe(true);

    await act(async () => productCheckbox('Core')!.click());
    expect(productCheckbox('Core')!.checked).toBe(true);
    expect(container.querySelector<HTMLInputElement>('#releaseName')!.value).toBe('Sprint 43');
    expect(container.querySelector<HTMLInputElement>('#releaseVersion')!.value).toBe('2026.09');
    expect(byText('Create & add').disabled).toBe(false);
  });

  it('points to the Portfolio page and blocks submit when no active product exists', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) =>
      String(input) === '/api/products' ? json(200, { products: [] }) : json(200, { releases: [OPEN_RELEASE] }),
    );

    await renderDialog();
    await submitCreateForm('Sprint 43', '2026.09', false);

    expect(container.textContent).toContain('Add a product on the Portfolio page first.');
    expect(container.querySelector('a[href="/portfolio"]')).not.toBeNull();
    expect(byText('Create & add').disabled).toBe(true);
  });

  it('renders the RELEASE_PRODUCT_REQUIRED message from a 422', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      const url = String(input);
      if (url === '/api/releases' && init?.method === 'POST') {
        return json(422, { status: 422, reason: 'RELEASE_PRODUCT_REQUIRED' });
      }
      if (url === '/api/products') return json(200, PRODUCTS);
      return json(200, { releases: [OPEN_RELEASE] });
    });

    await renderDialog();
    await submitCreateForm('Sprint 43', '2026.09');

    expect(container.querySelector('[data-reason="RELEASE_PRODUCT_REQUIRED"]')?.textContent).toBe(
      'Choose at least one product for this release.',
    );
  });

  it('does not request products until the New release form is opened', async () => {
    const spy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) =>
      String(input) === '/api/products' ? json(200, PRODUCTS) : json(200, { releases: [OPEN_RELEASE] }),
    );

    await renderDialog();

    expect(spy.mock.calls.some((c) => String(c[0]) === '/api/products')).toBe(false);
  });
});
