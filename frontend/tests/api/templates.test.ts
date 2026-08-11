import { afterEach, describe, expect, it, vi } from 'vitest';
import { apiFetch } from '../../src/api/client';
import {
  formatMaxSize,
  templatesOfType,
  type Template,
  type TemplateListResponse,
  type TemplateSummary,
} from '../../src/api/templates';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

function summary(overrides: Partial<TemplateSummary>): TemplateSummary {
  return {
    id: '1',
    name: 'Standard release notes',
    documentType: 'RELEASE_NOTES',
    active: true,
    hasLogo: false,
    warningCount: 0,
    updatedAt: '2026-08-09T00:00:00Z',
    ...overrides,
  };
}

function template(overrides: Partial<Template>): Template {
  return {
    id: '1',
    name: 'Standard release notes',
    documentType: 'RELEASE_NOTES',
    body: '# {{release.name}}',
    active: true,
    chrome: {
      coverEnabled: false,
      coverTitle: '',
      coverSubtitle: '',
      headerText: '',
      footerText: '',
      pageNumbersEnabled: false,
      logoRenderHeightMm: 30,
    },
    groupingDimension: 'ISSUE_TYPE',
    logo: null,
    warnings: [],
    createdAt: '2026-08-09T00:00:00Z',
    createdBy: 'rm@acme.com',
    updatedAt: '2026-08-09T00:00:00Z',
    updatedBy: 'rm@acme.com',
    ...overrides,
  };
}

describe('templates API client', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('resolves a mocked 200 on GET /api/templates to { templates: [...] }', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(200, { templates: [summary({ id: '1' })] }),
    );

    const result = await apiFetch<TemplateListResponse>('/api/templates');

    expect(result.templates).toHaveLength(1);
  });

  it('POST /api/templates sends a JSON body with name and documentType and a JSON content type', async () => {
    const fetchMock = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(jsonResponse(201, template({ id: '2', name: 'New template' })));

    await apiFetch<Template>('/api/templates', {
      method: 'POST',
      body: JSON.stringify({ name: 'New template', documentType: 'RUNBOOK' }),
    });

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    const headers = new Headers(init.headers);
    expect(headers.get('Content-Type')).toBe('application/json');
    expect(JSON.parse(init.body as string)).toEqual({ name: 'New template', documentType: 'RUNBOOK' });
  });

  it('POST /api/templates/{id}/logo sends a FormData body without a Content-Type: application/json header', async () => {
    const fetchMock = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, template({ id: '1' })));

    const form = new FormData();
    form.append('file', new File(['x'], 'logo.png', { type: 'image/png' }));
    await apiFetch<Template>('/api/templates/1/logo', { method: 'POST', body: form });

    const init = fetchMock.mock.calls[0][1] as RequestInit;
    expect(init.body).toBeInstanceOf(FormData);
    const headers = new Headers(init.headers);
    expect(headers.get('Content-Type')).not.toBe('application/json');
  });

  it('resolves a mocked 204 on DELETE /api/templates/{id} to undefined rather than throwing', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(null, { status: 204 }));

    const result = await apiFetch<void>('/api/templates/1', { method: 'DELETE' });

    expect(result).toBeUndefined();
  });

  it('templatesOfType filters by document type and sorts alphabetically by name', () => {
    const templates = [
      summary({ id: '1', name: 'Zulu', documentType: 'RELEASE_NOTES' }),
      summary({ id: '2', name: 'Alpha', documentType: 'RELEASE_NOTES' }),
      summary({ id: '3', name: 'Beta', documentType: 'RUNBOOK' }),
    ];

    const result = templatesOfType(templates, 'RELEASE_NOTES');

    expect(result.map((t) => t.id)).toEqual(['2', '1']);
  });

  it('formatMaxSize(2097152) returns 2MB', () => {
    expect(formatMaxSize(2097152)).toBe('2MB');
  });
});
