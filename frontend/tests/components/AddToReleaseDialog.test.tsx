import { StrictMode, act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { AddToReleaseDialog } from '../../src/components/AddToReleaseDialog';

// The component is only reachable through TicketsRoute, which is inside the router. Only
// useNavigate is used here, and this suite asserts what happens when navigation never runs.
const navigate = vi.fn();
vi.mock('@tanstack/react-router', () => ({ useNavigate: () => navigate }));

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

async function submitCreateForm(name: string, version: string) {
  await act(async () => byText('New release…').click());
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
  });
});
