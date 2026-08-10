import { StrictMode, act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LogoUpload } from '../../src/components/LogoUpload';
import type { LogoLimits, LogoMetadata } from '../../src/api/templates';

const LIMITS: LogoLimits = { maxBytes: 2097152, maxWidth: 2000, maxHeight: 2000, contentTypes: ['image/png', 'image/jpeg'] };

const LOGO: LogoMetadata = {
  contentType: 'image/png',
  filename: 'logo.png',
  byteSize: 38_912,
  width: 420,
  height: 140,
  uploadedAt: '2026-08-09T00:00:00Z',
};

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

let container: HTMLDivElement;
let root: Root;

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function renderControl(logo: LogoMetadata | null, limits: LogoLimits | undefined = LIMITS) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <StrictMode>
        <QueryClientProvider client={client}>
          <LogoUpload templateId="t1" logo={logo} limits={limits} />
        </QueryClientProvider>
      </StrictMode>,
    );
  });
  await flush();
}

function fileInput(): HTMLInputElement {
  return container.querySelector<HTMLInputElement>('input[type="file"]')!;
}

function chooseFile(file: File) {
  const input = fileInput();
  Object.defineProperty(input, 'files', { value: [file], configurable: true });
  input.dispatchEvent(new Event('change', { bubbles: true }));
}

function byText<T extends HTMLElement>(tag: string, text: string): T {
  const el = [...container.querySelectorAll<T>(tag)].find((e) => e.textContent === text);
  expect(el, `no <${tag}> with text "${text}"`).toBeDefined();
  return el!;
}

describe('LogoUpload', () => {
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
  });

  it('shows the drop zone and Choose file button when no logo is uploaded', async () => {
    await renderControl(null);
    expect(container.textContent).toContain('Drag and drop a PNG or JPEG, or');
    expect(byText<HTMLButtonElement>('button', 'Choose file')).toBeDefined();
  });

  it('shows the thumbnail and metadata line when a logo is uploaded', async () => {
    await renderControl(LOGO);
    const img = container.querySelector('img')!;
    expect(img.src).toContain('/api/templates/t1/logo');
    expect(container.textContent).toContain('420×140px');
  });

  it('rejects a GIF client-side with the invalid-type sentence and issues no fetch', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    await renderControl(null);

    const file = new File(['x'], 'pic.gif', { type: 'image/gif' });
    await act(async () => chooseFile(file));
    await flush();

    expect(container.textContent).toContain(
      "This file isn't a supported image. Upload a PNG or JPEG — SVG and other formats aren't accepted.",
    );
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('rejects a 3MB PNG client-side with the too-large sentence naming the backend limit', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    await renderControl(null);

    const big = new Uint8Array(3 * 1024 * 1024);
    const file = new File([big], 'big.png', { type: 'image/png' });
    await act(async () => chooseFile(file));
    await flush();

    expect(container.textContent).toContain('This image is too large. Upload a file under 2MB.');
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it('shows the dimensions-exceeded sentence with data-reason on a server rejection', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      json(422, { status: 422, reason: 'LOGO_DIMENSIONS_EXCEEDED' }),
    );
    await renderControl(null);

    const file = new File(['x'], 'wide.png', { type: 'image/png' });
    await act(async () => chooseFile(file));
    await flush();

    const alert = container.querySelector('[role="alert"]')!;
    expect(alert.textContent).toBe('This image is too large. Maximum dimensions are 2000×2000px.');
    expect(alert.getAttribute('data-reason')).toBe('LOGO_DIMENSIONS_EXCEEDED');
  });

  it('shows the identical invalid-type sentence for a server-caught rejection as the client pre-check', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(json(415, { status: 415, reason: 'LOGO_INVALID_TYPE' }));
    await renderControl(null);

    // A file whose declared type passes the client pre-check but is rejected server-side by
    // content sniffing (e.g. an SVG renamed to .png) - T-04-52.
    const file = new File(['<svg/>'], 'fake.png', { type: 'image/png' });
    await act(async () => chooseFile(file));
    await flush();

    const alert = container.querySelector('[role="alert"]')!;
    expect(alert.textContent).toBe(
      "This file isn't a supported image. Upload a PNG or JPEG — SVG and other formats aren't accepted.",
    );
  });

  it('removes the logo with no confirmation dialog', async () => {
    let deleteCalled = false;
    vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
      if (String(input).endsWith('/api/templates/t1/logo') && init?.method === 'DELETE') {
        deleteCalled = true;
        return json(200, { id: 't1', logo: null });
      }
      return json(200, {});
    });
    await renderControl(LOGO);

    expect(container.querySelector('dialog')).toBeNull();
    await act(async () => byText<HTMLButtonElement>('button', 'Remove').click());
    await flush();

    expect(deleteCalled).toBe(true);
    expect(container.querySelector('dialog')).toBeNull();
  });

  it('shows "Uploading…" while the upload mutation is pending', async () => {
    vi.spyOn(globalThis, 'fetch').mockImplementation(() => new Promise(() => {}));
    await renderControl(null);

    const file = new File(['x'], 'logo.png', { type: 'image/png' });
    await act(async () => chooseFile(file));
    await flush();

    expect(container.textContent).toContain('Uploading…');
  });
});
