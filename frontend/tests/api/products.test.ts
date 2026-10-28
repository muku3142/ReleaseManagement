import { act, createElement, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { messageForReason } from '../../src/api/reasonMessages';
import { JIRA_PROJECT_KEY, parseJiraKeys, useSetProductJiraProjects } from '../../src/api/products';

async function runMutation(vars: { id: string; keys: string[] }) {
  const fetchSpy = vi
    .spyOn(globalThis, 'fetch')
    .mockResolvedValue(
      new Response(JSON.stringify({ products: [] }), { status: 200, headers: { 'Content-Type': 'application/json' } }),
    );
  (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  const queryClient = new QueryClient();
  const invalidate = vi.spyOn(queryClient, 'invalidateQueries');
  const root = createRoot(document.createElement('div'));
  function Probe() {
    const mutation = useSetProductJiraProjects();
    useEffect(() => mutation.mutate(vars), []); // eslint-disable-line react-hooks/exhaustive-deps
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
  return {
    url: String(url),
    init: init as RequestInit,
    invalidated: invalidate.mock.calls.map((c) => c[0]?.queryKey?.[0]),
  };
}

describe('product Jira project keys', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('parseJiraKeys upper-cases, splits, drops empties and de-duplicates in order', () => {
    expect(parseJiraKeys(' cnv, abc  CNV,,')).toEqual(['CNV', 'ABC']);
    expect(parseJiraKeys('')).toEqual([]);
  });

  it('JIRA_PROJECT_KEY accepts project key shapes only', () => {
    expect(JIRA_PROJECT_KEY.test('CNV')).toBe(true);
    expect(JIRA_PROJECT_KEY.test('C-1')).toBe(false);
    expect(JIRA_PROJECT_KEY.test('1AB')).toBe(false);
  });

  it('PUTs the keys and invalidates briefings alongside the existing keys', async () => {
    const { url, init, invalidated } = await runMutation({ id: 'p1', keys: ['CNV'] });
    expect(url).toBe('/api/products/p1/jira-projects');
    expect(init.method).toBe('PUT');
    expect(init.body).toBe('{"keys":["CNV"]}');
    expect(invalidated).toContain('briefings');
    expect(invalidated).toContain('roadmap');
  });

  it('has an exact message for every Phase 17.1 reason code', () => {
    const msg = (status: number, reason: string) => messageForReason({ status, reason });
    expect(msg(409, 'PRODUCT_JIRA_KEY_TAKEN')).toBe(
      'That Jira project key already belongs to another product. A key can belong to one product only.',
    );
    expect(msg(400, 'PRODUCT_JIRA_KEY_INVALID')).toBe(
      "That isn't a valid Jira project key. Keys are letters and digits, starting with a letter, for example CNV.",
    );
    expect(msg(409, 'BRIEFING_NO_JIRA_CREDENTIAL')).toBe(
      'You have no stored Jira credential. Sign in with a Jira API token first.',
    );
    expect(msg(400, 'BRIEFING_SETTINGS_INVALID')).toBe(
      'Some briefing settings are not valid. Check the highlighted fields and try again.',
    );
    expect(msg(409, 'BRIEFING_TASK_NOT_RUNNABLE')).toBe(
      "This briefing can't be run by the app. Only the three Jira briefings run here.",
    );
    expect(msg(409, 'BRIEFING_TASK_DISABLED')).toBe('Enable this briefing to run it.');
  });
});
