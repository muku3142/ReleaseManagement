import { StrictMode, act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { SettingsRoute } from '../../src/routes/SettingsRoute';

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

function stubFetch(putResponse: Response, adoGet: unknown = UNCONFIGURED) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = String(input);
    if (url === ADO_URL && init?.method === 'PUT') {
      putBodies.push(JSON.parse(String(init.body)));
      return putResponse;
    }
    if (url === ADO_URL) return json(200, adoGet);
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
  const form = container.querySelector<HTMLInputElement>('#adoOrganization')!.closest('form')!;
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
  });

  it('tells the user to select one organization and masks the token input', async () => {
    stubFetch(json(200, UNCONFIGURED));
    await renderRoute();

    expect(container.textContent).toContain('select one organization');
    expect(container.querySelector('#adoToken')?.getAttribute('type')).toBe('password');
  });

  it('sends organization, token and expiry on submit', async () => {
    stubFetch(json(200, { ...UNCONFIGURED, configured: true, organization: 'acme', expiresOn: '2027-01-01', state: 'OK' }));
    await renderRoute();

    await setValue('adoOrganization', 'acme');
    await setValue('adoToken', 'pat-123');
    await setValue('adoExpiresOn', '2027-01-01');
    await submitAdoForm();

    expect(putBodies).toEqual([{ organization: 'acme', token: 'pat-123', expiresOn: '2027-01-01' }]);
    expect(container.textContent).toContain('Azure DevOps settings saved.');
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
  });
});
