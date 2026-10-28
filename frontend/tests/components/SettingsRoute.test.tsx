import { StrictMode, act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { toast } from 'sonner';
import { SettingsRoute } from '../../src/routes/SettingsRoute';

vi.mock('sonner', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const ADO_URL = '/api/settings/azure-devops';
const UNCONFIGURED = { configured: false, organization: null, expiresOn: null, state: null };

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

let container: HTMLDivElement;
let root: Root;
let putBodies: Record<string, unknown>[];

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function stubFetch(putResponse: Response, adoGet: unknown = UNCONFIGURED, otherPuts: Record<string, Response> = {}) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    if (init?.method === 'PUT' && otherPuts[url]) return otherPuts[url];
    if (url === ADO_URL && init?.method === 'PUT') {
      putBodies.push(JSON.parse(String(init.body)));
      return putResponse;
    }
    if (url === ADO_URL) return json(200, adoGet);
    if (url === '/api/environment-ladder') return json(200, { stages: [] });
    return json(404, { status: 404, reason: 'SITE_NOT_CONFIGURED' });
  });
}

async function renderRoute() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <SettingsRoute />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
}

function setValue(id: string, value: string) {
  const input = container.querySelector<HTMLInputElement>(`#${id}`)!;
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
  return act(async () => {
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

async function submitAdoForm() {
  await submitFormOf('adoOrganization');
}

async function submitFormOf(inputId: string) {
  const form = container.querySelector<HTMLInputElement>(`#${inputId}`)!.closest('form')!;
  await act(async () => {
    form.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
  });
  await flush();
}

describe('SettingsRoute Azure DevOps section', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    putBodies = [];
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
    vi.mocked(toast.success).mockClear();
  });

  it('tells the user to select one organization and masks the token input', async () => {
    stubFetch(json(200, UNCONFIGURED));
    await renderRoute();

    expect(container.textContent).toContain('select one organization');
    expect(container.querySelector('#adoToken')?.getAttribute('type')).toBe('password');
  });

  it('renders the Environment ladder section', async () => {
    stubFetch(json(200, UNCONFIGURED));
    await renderRoute();

    expect(container.textContent).toContain('Environment ladder');
  });

  it('sends organization, token and expiry on submit', async () => {
    stubFetch(json(200, { ...UNCONFIGURED, configured: true, organization: 'acme', expiresOn: '2027-01-01', state: 'OK' }));
    await renderRoute();

    await setValue('adoOrganization', 'acme');
    await setValue('adoToken', 'pat-123');
    await setValue('adoExpiresOn', '2027-01-01');
    await submitAdoForm();

    expect(putBodies).toEqual([{ organization: 'acme', token: 'pat-123', expiresOn: '2027-01-01' }]);
    expect(toast.success).toHaveBeenCalledWith('Azure DevOps settings saved.');
    expect(container.textContent).not.toContain('Azure DevOps settings saved.');
    expect(container.querySelector<HTMLInputElement>('#adoToken')!.value).toBe('');
  });

  it('omits a blank token from the body', async () => {
    stubFetch(json(200, UNCONFIGURED));
    await renderRoute();

    await setValue('adoOrganization', 'acme');
    await setValue('adoExpiresOn', '2027-01-01');
    await submitAdoForm();

    expect(putBodies).toEqual([{ organization: 'acme', expiresOn: '2027-01-01' }]);
  });

  it('shows the mapped message for a rejected token', async () => {
    stubFetch(json(422, { status: 422, reason: 'ADO_UNAUTHORIZED' }));
    await renderRoute();

    await setValue('adoOrganization', 'acme');
    await setValue('adoToken', 'bad');
    await setValue('adoExpiresOn', '2027-01-01');
    await submitAdoForm();

    const alert = container.querySelector('[data-reason="ADO_UNAUTHORIZED"]');
    expect(alert?.textContent).toContain('Azure DevOps rejected this token');
    expect(toast.success).not.toHaveBeenCalled();
  });

  it('confirms a saved Jira site with a toast instead of inline text', async () => {
    const site = { baseUrl: 'https://acme.atlassian.net', updatedAt: '2026-01-01T00:00:00Z', updatedBy: 'rm' };
    stubFetch(json(200, UNCONFIGURED), UNCONFIGURED, { '/api/settings/jira-site': json(200, site) });
    await renderRoute();

    await setValue('baseUrl', site.baseUrl);
    await submitFormOf('baseUrl');

    expect(toast.success).toHaveBeenCalledWith('Jira site saved.');
    expect(container.textContent).not.toContain('Saved.');
  });

  it('confirms a replaced token with a toast and clears the input', async () => {
    stubFetch(json(200, UNCONFIGURED), UNCONFIGURED, { '/api/auth/credential': new Response(null, { status: 204 }) });
    await renderRoute();

    await setValue('apiToken', 'new-token');
    await submitFormOf('apiToken');

    expect(toast.success).toHaveBeenCalledWith('Token replaced.');
    expect(container.querySelector<HTMLInputElement>('#apiToken')!.value).toBe('');
    expect(container.querySelector('[role="status"]')).toBeNull();
  });
});
