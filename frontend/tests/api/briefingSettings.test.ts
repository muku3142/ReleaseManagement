import { act, createElement, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  briefingSettingsQueryKey,
  cronSummary,
  useBriefingSettings,
  useRunBriefingNow,
  useSaveBriefingSettings,
  useUseMyJiraCredential,
  type BriefingSettingsUpdate,
} from '../../src/api/briefingSettings';

function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

async function run(useProbe: () => void, response: unknown) {
  const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(jsonResponse(200, response));
  (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const queryClient = new QueryClient();
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  const root = createRoot(document.createElement('div'));
  function Probe() {
    useProbe();
    return null;
  }
  await act(async () => {
    root.render(createElement(QueryClientProvider, { client: queryClient }, createElement(Probe)));
  });
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
  await act(async () => root.unmount());
  const [url, init] = fetchSpy.mock.calls.at(-1)!;
  return { url: String(url), init: (init ?? {}) as RequestInit, queryClient, invalidate };
}

const SETTINGS = { graceMinutes: 30, tasks: [] };
const UPDATE: BriefingSettingsUpdate = { graceMinutes: 30, workDays: ['SUN'], keepRuns: 60, tasks: [] };

describe('briefing settings API', () => {
  afterEach(() => vi.restoreAllMocks());

  it('loads settings under the briefing-settings key', async () => {
    const { url, queryClient } = await run(() => void useBriefingSettings(), SETTINGS);
    expect(url).toBe('/api/settings/briefings');
    expect(queryClient.getQueryData(briefingSettingsQueryKey)).toEqual(SETTINGS);
  });

  it('saves the update, caches the response and invalidates briefings', async () => {
    const { url, init, queryClient, invalidate } = await run(() => {
      const mutation = useSaveBriefingSettings();
      useEffect(() => mutation.mutate(UPDATE), []); // eslint-disable-line react-hooks/exhaustive-deps
    }, SETTINGS);
    expect(url).toBe('/api/settings/briefings');
    expect(init.method).toBe('PUT');
    expect(JSON.parse(String(init.body))).toEqual(UPDATE);
    expect(queryClient.getQueryData(briefingSettingsQueryKey)).toEqual(SETTINGS);
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['briefings'] });
  });

  it('designates the caller with no body', async () => {
    const { url, init } = await run(() => {
      const mutation = useUseMyJiraCredential();
      useEffect(() => mutation.mutate(), []); // eslint-disable-line react-hooks/exhaustive-deps
    }, SETTINGS);
    expect(url).toBe('/api/settings/briefings/scheduler-user');
    expect(init.method).toBe('PUT');
    expect(init.body).toBeUndefined();
  });

  it('runs a task now and invalidates briefings', async () => {
    const result = { task: 'sprint_check', name: 'Sprint backlog check', products: 2, attention: 1, failed: 0, needsCredential: 0, skipped: 0 };
    const { url, init, invalidate } = await run(() => {
      const mutation = useRunBriefingNow();
      useEffect(() => mutation.mutate('sprint_check'), []); // eslint-disable-line react-hooks/exhaustive-deps
    }, result);
    expect(url).toBe('/api/settings/briefings/tasks/sprint_check/run');
    expect(init.method).toBe('POST');
    expect(invalidate).toHaveBeenCalledWith({ queryKey: ['briefings'] });
  });

  it('summarises a slot in week order', () => {
    expect(cronSummary(['THU', 'SUN', 'MON', 'TUE', 'WED'], '08:44')).toBe(
      'Runs Sun, Mon, Tue, Wed, Thu at 08:44 Africa/Cairo.',
    );
  });
});
