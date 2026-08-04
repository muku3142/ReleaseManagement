import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, apiFetch } from '../../src/api/client';
import {
  DEFAULT_STATUS_CATEGORIES,
  toSearchBody,
  type TicketSearchResponse,
} from '../../src/api/tickets';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('ticket search client', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('sends only the picker fields on the project path', () => {
    const body = toSearchBody({
      mode: 'project',
      projectId: '10023',
      sprintId: '10891',
      statusCategories: ['DONE'],
    });

    expect(body).toEqual({ projectId: '10023', sprintId: '10891', statusCategories: ['DONE'] });
    expect('jql' in body).toBe(false);
  });

  it('omits sprintId entirely when no sprint is selected', () => {
    const body = toSearchBody({
      mode: 'project',
      projectId: '10023',
      sprintId: null,
      statusCategories: ['DONE'],
    });

    expect(body).toEqual({ projectId: '10023', statusCategories: ['DONE'] });
    expect('sprintId' in body).toBe(false);
  });

  it('sends only the jql field on the advanced path, with no implied status filter', () => {
    const body = toSearchBody({ mode: 'jql', jql: 'project = CNV' });

    expect(body).toEqual({ jql: 'project = CNV' });
    expect('projectId' in body).toBe(false);
    expect('statusCategories' in body).toBe(false);
  });

  it('treats an empty ticket list as a success, not an error', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, { tickets: [] }));

    const result = await apiFetch<TicketSearchResponse>('/api/tickets/search', {
      method: 'POST',
      body: JSON.stringify({ projectId: '10023', sprintId: '10891' }),
    });

    expect(result.tickets).toEqual([]);
  });

  it('surfaces RESULT_SET_TOO_LARGE as an ApiError', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(422, { status: 422, reason: 'RESULT_SET_TOO_LARGE' }),
    );

    await expect(
      apiFetch<TicketSearchResponse>('/api/tickets/search', {
        method: 'POST',
        body: JSON.stringify({ projectId: '10023', sprintId: '10891' }),
      }),
    ).rejects.toSatisfy((error) => error instanceof ApiError && error.problem.reason === 'RESULT_SET_TOO_LARGE');
  });

  it('surfaces JIRA_UNREACHABLE as an ApiError', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      jsonResponse(503, { status: 503, reason: 'JIRA_UNREACHABLE' }),
    );

    await expect(
      apiFetch<TicketSearchResponse>('/api/tickets/search', {
        method: 'POST',
        body: JSON.stringify({ projectId: '10023', sprintId: '10891' }),
      }),
    ).rejects.toSatisfy((error) => error instanceof ApiError && error.problem.reason === 'JIRA_UNREACHABLE');
  });

  it('defaults the status filter to Done only', () => {
    expect(DEFAULT_STATUS_CATEGORIES).toEqual(['DONE']);
  });

  it('preserves an explicitly empty status set rather than dropping it', () => {
    const body = toSearchBody({
      mode: 'project',
      projectId: '10023',
      sprintId: '10891',
      statusCategories: [],
    });

    expect(body.statusCategories).toEqual([]);
    expect('statusCategories' in body).toBe(true);
  });
});
