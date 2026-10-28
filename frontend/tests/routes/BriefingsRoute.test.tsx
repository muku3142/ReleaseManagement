import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ReactNode } from 'react';
import type { AppBriefingRun, BriefingRun, BriefingTaskFeed } from '../../src/api/briefings';
import { BriefingsRoute } from '../../src/routes/BriefingsRoute';

const navigate = vi.fn();
let searchParams: Record<string, unknown> = {};

vi.mock('@tanstack/react-router', () => ({
  Link: ({ children, to, hash }: { children?: ReactNode; to?: string; hash?: string }) => (
    <a href={hash ? `${to}#${hash}` : to}>{children}</a>
  ),
  useSearch: () => searchParams,
  useNavigate: () => navigate,
}));

const CNV_ID = '11111111-1111-4111-8111-111111111111';
const CORE_ID = '22222222-2222-4222-8222-222222222222';
const PRODUCTS = {
  products: [
    { id: CNV_ID, name: 'Converge', archived: false, clientCount: 0, releaseCount: 0, jiraProjectKeys: ['CNV'] },
    { id: CORE_ID, name: 'Core', archived: false, clientCount: 0, releaseCount: 0, jiraProjectKeys: ['COR'] },
  ],
};

let container: HTMLDivElement;
let root: Root;

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
});

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  act(() => root.unmount());
  container.remove();
  vi.restoreAllMocks();
  navigate.mockReset();
  searchParams = {};
});

function run(runAt: string, status: BriefingRun['status'], extra: Partial<BriefingRun> = {}): BriefingRun {
  return { runAt, status, headline: null, attention: null, h1: null, h2: null, h3: null, ...extra };
}

function feed(id: string, name: string, runs: BriefingRun[], extra: Partial<BriefingTaskFeed> = {}): BriefingTaskFeed {
  return { id, name, schedule: 'Sun 08:36 · Africa/Cairo', readError: false, latest: runs.at(-1) ?? null, runs: [...runs].reverse(), ...extra };
}

const digestRun = run('2026-10-26T05:44:12Z', 'attention', {
  headline: '3 blockers and 2 stale stories in CNV',
  attention: 5,
  h1: 'CNV-3120 blocked on bank config',
  h2: 'CNV-3001 stale',
});

function fiveTasks(): BriefingTaskFeed[] {
  return [
    feed('jira_digest', 'Daily Jira digest', [digestRun]),
    feed('sprint_check', 'Sprint backlog check', [run('2026-10-25T06:41:00Z', 'ok', { headline: 'All good' })]),
    feed('weekly_status', 'Weekly status update', [run('2026-10-22T10:41:00Z', 'failed', { headline: 'Could not finish' })]),
    feed('reg_watch', 'Payments regulation watch', []),
    feed('gulf_invest', 'Saudi & Gulf investment brief', [run('2026-10-19T06:33:00Z', 'quiet', { headline: 'Nothing new' })]),
  ];
}

async function renderRoute(reply: () => Response | Promise<Response>) {
  const json = (body: unknown) =>
    new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });
  const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (input) => {
    const url = String(input);
    if (url.includes('/api/products')) return json(PRODUCTS);
    if (url.includes('/api/settings/jira-site')) return json({ baseUrl: 'https://x.atlassian.net', updatedAt: '', updatedBy: '' });
    return reply();
  });
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <BriefingsRoute />
      </QueryClientProvider>,
    );
  });
  await act(async () => new Promise((resolve) => setTimeout(resolve, 0)));
  await act(async () => new Promise((resolve) => setTimeout(resolve, 0)));
  return fetchSpy;
}

const ok = (tasks: BriefingTaskFeed[]) => () =>
  new Response(JSON.stringify({ tasks }), { status: 200, headers: { 'Content-Type': 'application/json' } });

const card = (id: string) => container.querySelector(`[data-testid="briefing-card-${id}"]`)!;
const pill = (id: string) => card(id).querySelector('[data-status]')!.textContent;

describe('BriefingsRoute', () => {
  it('F2: renders a card per task in order with the right pill, bullets and empty copy', async () => {
    await renderRoute(ok(fiveTasks()));
    const ids = Array.from(container.querySelectorAll('[data-testid^="briefing-card-"]')).map((c) =>
      c.getAttribute('data-testid'),
    );
    expect(ids).toEqual(['jira_digest', 'sprint_check', 'weekly_status', 'reg_watch', 'gulf_invest'].map((i) => `briefing-card-${i}`));
    expect(['jira_digest', 'sprint_check', 'weekly_status', 'reg_watch', 'gulf_invest'].map(pill)).toEqual([
      'Needs attention',
      'All clear',
      'Run failed',
      'Not run yet',
      'Quiet',
    ]);
    const digest = card('jira_digest');
    expect(digest.textContent).toContain('5 items need attention');
    expect(digest.textContent).toContain('Last run Mon 26 Oct, 08:44 Cairo');
    expect(Array.from(digest.querySelectorAll('li')).map((li) => li.textContent)).toEqual([
      'CNV-3120 (opens in a new tab) blocked on bank config',
      'CNV-3001 (opens in a new tab) stale',
    ]);
    expect(card('reg_watch').textContent).toContain('No result yet. This briefing runs Sun 08:36 · Africa/Cairo.');
    expect(card('weekly_status').textContent).toContain("Claude's last run for this briefing failed.");
  });

  it('F3: summary line counts attention and failed cards, and the history is newest first', async () => {
    await renderRoute(ok(fiveTasks()));
    expect(container.textContent).toContain(
      '1 of 5 briefings needs attention · latest run Mon 26 Oct, 08:44 Cairo · 1 failed',
    );
    expect(container.querySelector('caption')?.textContent).toBe('Run history, newest first');
    const rows = Array.from(container.querySelectorAll('tbody tr'));
    expect(rows.map((r) => r.querySelector('td')?.textContent)).toEqual([
      'Daily Jira digest',
      'Sprint backlog check',
      'Weekly status update',
      'Saudi & Gulf investment brief',
    ]);
    expect(rows[0].getAttribute('data-source')).toBe('claude');
    expect(rows[0].querySelectorAll('td')[1].textContent).toBe('—');
    expect(rows[0].querySelectorAll('td')[2].textContent).toBe('Claude');
    expect(rows[0].querySelectorAll('td')[6].textContent).toBe('5');
    expect(rows[1].querySelectorAll('td')[6].textContent).toBe('—');
  });

  it('F3: summary line variants for zero attention and no runs', async () => {
    await renderRoute(ok([feed('jira_digest', 'Daily Jira digest', [run('2026-10-26T05:44:12Z', 'ok')])]));
    expect(container.textContent).toContain('No briefing needs attention · latest run Mon 26 Oct, 08:44 Cairo');
    act(() => root.unmount());
    root = createRoot(container);
    await renderRoute(ok([feed('jira_digest', 'Daily Jira digest', [])]));
    expect(container.textContent).toContain('No briefings have run yet');
    expect(container.textContent).toContain('No runs yet');
  });

  it('F3: shows 50 history rows, then the rest on Show more', async () => {
    const runs = Array.from({ length: 60 }, (_, i) =>
      run(new Date(Date.UTC(2026, 9, 1) + i * 3_600_000).toISOString(), 'ok', { headline: `run ${i}` }),
    );
    await renderRoute(ok([feed('jira_digest', 'Daily Jira digest', runs)]));
    expect(container.querySelectorAll('tbody tr')).toHaveLength(50);
    expect(container.textContent).toContain('Showing 50 of 60 runs.');
    const button = Array.from(container.querySelectorAll('button')).find((b) => b.textContent === 'Show more')!;
    await act(async () => button.click());
    expect(container.querySelectorAll('tbody tr')).toHaveLength(60);
    expect(container.textContent).toContain('Showing 60 of 60 runs.');
    expect(Array.from(container.querySelectorAll('button')).some((b) => b.textContent === 'Show more')).toBe(false);
  });

  it('F4: a failed request shows the error with Try again, which refetches', async () => {
    let calls = 0;
    const fetchSpy = await renderRoute(() => {
      calls += 1;
      return calls === 1
        ? new Response(JSON.stringify({ status: 500, reason: 'UNKNOWN' }), {
            status: 500,
            headers: { 'Content-Type': 'application/problem+json' },
          })
        : ok(fiveTasks())();
    });
    expect(container.querySelector('[role="alert"]')).not.toBeNull();
    const retry = Array.from(container.querySelectorAll('button')).find((b) => b.textContent === 'Try again')!;
    await act(async () => retry.click());
    await act(async () => new Promise((resolve) => setTimeout(resolve, 0)));
    expect(fetchSpy.mock.calls.filter(([url]) => String(url).includes('/api/briefings'))).toHaveLength(2);
    expect(container.querySelector('[data-testid="briefing-card-jira_digest"]')).not.toBeNull();
  });

  it('F4: an unreadable file shows the Unreadable pill and the read error copy', async () => {
    await renderRoute(ok([feed('sprint_check', 'Sprint backlog check', [], { readError: true })]));
    expect(pill('sprint_check')).toBe('Unreadable');
    expect(card('sprint_check').textContent).toContain("Couldn't read this briefing's file");
  });

  it('renders markup in briefing text as literal text', async () => {
    const hostile = feed('jira_digest', 'Daily Jira digest', [
      run('2026-10-26T05:44:12Z', 'attention', {
        headline: '<img src=x onerror=alert(1)>',
        attention: 1,
        h1: 'javascript:alert(1)',
      }),
    ]);
    await renderRoute(ok([hostile]));
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('a')).toBeNull();
    expect(card('jira_digest').textContent).toContain('<img src=x onerror=alert(1)>');
    expect(card('jira_digest').textContent).toContain('javascript:alert(1)');
  });

  describe('app fallback', () => {
    function appRun(over: Partial<AppBriefingRun>): AppBriefingRun {
      return {
        id: 'a1',
        productId: CNV_ID,
        productName: 'Converge',
        runAt: '2026-10-26T05:50:00Z',
        status: 'attention',
        reason: null,
        trigger: 'slot',
        headline: '3 blockers in CNV',
        summary: null,
        attention: 3,
        sections: [
          { title: 'Blockers', items: [{ key: 'CNV-7', text: 'CNV-7 waits on ISO-20022 sign-off' }] },
          { title: 'Due soon or overdue', items: [] },
        ],
        ...over,
      };
    }

    const coreCredential = appRun({
      id: 'a2',
      productId: CORE_ID,
      productName: 'Core',
      status: 'needs_credential',
      reason: 'NEEDS_CREDENTIAL',
      attention: null,
      sections: [],
      headline: 'Needs a credential',
    });

    function withApp(app: AppBriefingRun[], appRuns: AppBriefingRun[] = app) {
      return [
        feed('jira_digest', 'Daily Jira digest', [run('2026-10-26T05:44:12Z', 'failed', { headline: 'Claude died' })], {
          appFallback: true,
          appLatest: app,
          appRuns,
        }),
        feed('reg_watch', 'Payments regulation watch', [], { appFallback: false, appLatest: app, appRuns: [] }),
      ];
    }

    it('shows Claude first, then one details row per product with the worst pill and summed count', async () => {
      await renderRoute(ok(withApp([appRun({}), coreCredential])));
      const digest = card('jira_digest');
      expect(pill('jira_digest')).toBe('Run failed');
      expect(digest.textContent).toContain('3 items need attention');
      expect(digest.textContent).toContain("Claude's last run for this briefing failed.");
      expect(digest.textContent).toContain("Claude's result for this slot is missing or failed, so the app ran it.");
      const rows = digest.querySelectorAll('details');
      expect(rows).toHaveLength(2);
      expect(rows[0].open).toBe(false);
      expect(rows[0].querySelector('summary')!.textContent).toContain('Converge');
      expect(rows[1].querySelector('summary')!.textContent).toContain('Core');
      expect(card('reg_watch').querySelector('details')).toBeNull();
    });

    it('renders sections, empty sections, linked keys and the status bodies', async () => {
      await renderRoute(
        ok(
          withApp([
            appRun({}),
            coreCredential,
            appRun({ id: 'a3', productId: 'p3', productName: 'Zed', status: 'skipped', reason: 'NO_JIRA_PROJECT', attention: null, sections: [], headline: 'Skipped: no Jira project' }),
            appRun({ id: 'a4', productId: 'p4', productName: 'Yak', status: 'failed', reason: 'RUN_FAILED', attention: null, sections: [], headline: 'Failed' }),
          ]),
        ),
      );
      const text = card('jira_digest').textContent!;
      expect(text).toContain('Blockers');
      expect(text).toContain('Nothing to report.');
      expect(text).toContain("The scheduler's Jira credential is missing or expired. Choose 'Use my Jira credential' in Settings > Briefings.");
      expect(text).toContain('No Jira project is set for this product, so the app skipped it.');
      expect(text).toContain('This run failed. The next scheduled run will try again.');
      const anchors = Array.from(card('jira_digest').querySelectorAll('a'));
      expect(anchors.map((a) => a.getAttribute('href'))).toEqual(
        expect.arrayContaining(['https://x.atlassian.net/browse/CNV-7', '/settings#briefings', '/portfolio']),
      );
      expect(anchors.some((a) => a.textContent === 'Set a Jira project')).toBe(true);
      expect(anchors.filter((a) => a.getAttribute('href')?.includes('ISO'))).toHaveLength(0);
    });

    it('opens a single app row by default', async () => {
      await renderRoute(ok(withApp([appRun({})])));
      expect(card('jira_digest').querySelector('details')!.open).toBe(true);
    });

    it('shows the page credential notice only when an app row needs a credential', async () => {
      const claudeOk = feed('jira_digest', 'Daily Jira digest', [run('2026-10-26T05:44:12Z', 'ok')], {
        appFallback: true,
        appLatest: [coreCredential],
        appRuns: [coreCredential],
      });
      await renderRoute(ok([claudeOk]));
      expect(container.querySelector('[role="status"]')!.textContent).toContain('Settings > Briefings');
      expect(pill('jira_digest')).toBe('Needs credential');
      expect(container.textContent).toContain('· 1 need a credential');
      act(() => root.unmount());
      root = createRoot(container);
      await renderRoute(ok(withApp([appRun({})])));
      expect(container.querySelector('[role="status"]')).toBeNull();
    });

    it('merges Claude and app runs newest first with Product and Source columns', async () => {
      const covered = appRun({ id: 'c', runAt: '2026-10-26T05:40:00Z', status: 'skipped', reason: 'CLAUDE_COVERED', attention: null, headline: 'Skipped: Claude covered this slot', sections: [] });
      await renderRoute(ok(withApp([appRun({})], [appRun({}), coreCredential, covered])));
      const rows = Array.from(container.querySelectorAll('tbody tr'));
      expect(rows.map((r) => r.getAttribute('data-source'))).toEqual(['app', 'app', 'claude', 'app']);
      expect(rows[0].querySelectorAll('td')[1].textContent).toBe('Converge');
      expect(rows[0].querySelectorAll('td')[2].textContent).toBe('App fallback');
      expect(rows[1].querySelectorAll('td')[6].textContent).toBe('—');
      expect(rows[3].querySelectorAll('td')[5].textContent).toBe('Skipped: Claude covered this slot');
      expect(card('jira_digest').textContent).not.toContain('Claude covered this slot');
    });

    it('narrows app rows by the products filter but keeps Claude rows', async () => {
      searchParams = { products: [CNV_ID] };
      await renderRoute(ok(withApp([appRun({}), coreCredential])));
      expect(card('jira_digest').querySelectorAll('details')).toHaveLength(1);
      expect(container.querySelector('[role="status"]')).toBeNull();
      expect(Array.from(container.querySelectorAll('tbody tr')).map((r) => r.getAttribute('data-source'))).toEqual(['app', 'claude']);
    });

    it('shows the empty filter panel and Clear filter when only app rows were hidden', async () => {
      searchParams = { products: [CNV_ID] };
      const only = [
        feed('jira_digest', 'Daily Jira digest', [], { appFallback: true, appLatest: [coreCredential], appRuns: [coreCredential] }),
      ];
      await renderRoute(ok(only));
      expect(container.textContent).toContain('No runs match this filter');
      const clear = Array.from(container.querySelectorAll('button')).find((b) => b.textContent === 'Clear filter')!;
      await act(async () => clear.click());
      expect(navigate).toHaveBeenCalledWith({ to: '/briefings', search: { products: undefined }, replace: true });
    });
  });
});
