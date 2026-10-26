import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { BriefingSettingsSection } from '../../src/components/BriefingSettingsSection';

vi.mock('@tanstack/react-router', () => ({
  Link: (props: { children?: ReactNode; to?: string }) => <a href={props.to}>{props.children}</a>,
}));

const URL_BASE = '/api/settings/briefings';

function task(id: string, name: string, over: Record<string, unknown> = {}) {
  return {
    id,
    name,
    enabled: true,
    days: ['SUN', 'MON', 'TUE', 'WED', 'THU'],
    time: '08:44',
    staleAfterWorkingDays: 3,
    dueSoonDays: 5,
    backlogItems: 15,
    ...over,
  };
}

function settings(over: Record<string, unknown> = {}) {
  return {
    claudeFeed: { dataDir: '/briefings/data', configFile: '/briefings/config.json', readable: true },
    schedulerUser: { email: 'rm@acme.com', isYou: false },
    credentialProblem: false,
    fallbackEnabled: false,
    graceMinutes: 30,
    workDays: ['SUN', 'MON', 'TUE', 'WED', 'THU'],
    keepRuns: 60,
    tasks: [
      task('jira_digest', 'Daily Jira digest'),
      task('sprint_check', 'Sprint backlog check'),
      task('weekly_status', 'Weekly status update', { enabled: false }),
    ],
    ...over,
  };
}

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
}

type Call = { url: string; method: string; body?: unknown };
let container: HTMLDivElement;
let root: Root;
let calls: Call[];

function stub(handlers: Record<string, () => Response>) {
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const method = init?.method ?? 'GET';
    const url = String(input);
    calls.push({ url, method, body: init?.body ? JSON.parse(String(init.body)) : undefined });
    const handler = handlers[`${method} ${url}`];
    return handler ? handler() : json(404, { status: 404, reason: 'NOT_FOUND' });
  });
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

async function mount() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  await act(async () => {
    root.render(
      <QueryClientProvider client={client}>
        <BriefingSettingsSection />
      </QueryClientProvider>,
    );
  });
  await flush();
}

function byId<T extends HTMLElement>(id: string): T {
  return document.getElementById(id) as T;
}

function button(label: string): HTMLButtonElement {
  const found = [...container.querySelectorAll('button')].find((candidate) => candidate.textContent?.includes(label));
  if (!found) throw new Error(`no button ${label}`);
  return found;
}

async function type(id: string, value: string) {
  const input = byId<HTMLInputElement>(id);
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')!.set!;
  await act(async () => {
    setter.call(input, value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
  });
}

async function click(element: HTMLElement) {
  await act(async () => element.click());
  await flush();
}

const puts = () => calls.filter((call) => call.method === 'PUT' && call.url === URL_BASE);

describe('BriefingSettingsSection', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    calls = [];
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
    window.location.hash = '';
  });

  it('shows a load error with Try again', async () => {
    stub({ [`GET ${URL_BASE}`]: () => json(500, { status: 500, reason: 'UNKNOWN' }) });
    await mount();
    expect(container.textContent).toContain('Try again');
    expect(container.textContent).toContain("Couldn't load briefing settings");
  });

  it('shows the read-only feed paths with no text inputs', async () => {
    stub({ [`GET ${URL_BASE}`]: () => json(200, settings()) });
    await mount();
    expect(byId('briefings')).not.toBeNull();
    expect(container.textContent).toContain('/briefings/data');
    expect(container.textContent).toContain('Readable');
    expect(container.textContent).toContain('Set by RM_BRIEFINGS_DIR on the server.');
    expect(container.querySelector('input[type="text"]')).toBeNull();
  });

  it('flags an unreadable feed and a credential problem', async () => {
    const body = settings({
      claudeFeed: { dataDir: '/d', configFile: '/c', readable: false },
      credentialProblem: true,
      schedulerUser: { email: null, isYou: false },
    });
    stub({ [`GET ${URL_BASE}`]: () => json(200, body) });
    await mount();
    expect(container.textContent).toContain('Not readable. The app treats Claude as unavailable');
    expect(container.textContent).toContain('Not set');
    expect(container.textContent).toContain("The scheduler's Jira credential is missing or expired.");
  });

  it('designates the signed-in user on click', async () => {
    let current = settings();
    stub({
      [`GET ${URL_BASE}`]: () => json(200, current),
      [`PUT ${URL_BASE}/scheduler-user`]: () => {
        current = settings({ schedulerUser: { email: 'me@acme.com', isYou: true } });
        return json(200, current);
      },
    });
    await mount();
    await click(button('Use my Jira credential'));
    expect(container.textContent).toContain('You are now the scheduler user.');
    expect(container.textContent).toContain('You are the scheduler user.');
    expect(calls.some((call) => call.url === `${URL_BASE}/scheduler-user` && call.method === 'PUT')).toBe(true);
  });

  it('shows the app fallback switch off by default', async () => {
    stub({ [`GET ${URL_BASE}`]: () => json(200, settings()) });
    await mount();
    const toggle = container.querySelector<HTMLButtonElement>('[role="switch"]')!;
    expect(toggle.getAttribute('aria-checked')).toBe('false');
    expect(byId('briefing-fallback-label').textContent).toBe('Claude unavailable: run briefings in the app');
    expect(toggle.parentElement?.textContent).toContain('Off');
  });

  it('turns the app fallback on immediately, without Save settings', async () => {
    stub({
      [`GET ${URL_BASE}`]: () => json(200, settings()),
      [`PUT ${URL_BASE}/fallback`]: () => json(200, settings({ fallbackEnabled: true })),
    });
    await mount();
    await click(container.querySelector<HTMLButtonElement>('[role="switch"]')!);
    const put = calls.find((call) => call.method === 'PUT' && call.url === `${URL_BASE}/fallback`);
    expect(put?.body).toEqual({ enabled: true });
    const toggle = container.querySelector<HTMLButtonElement>('[role="switch"]')!;
    expect(toggle.getAttribute('aria-checked')).toBe('true');
    expect(toggle.parentElement?.textContent).toContain('On');
    expect(puts()).toHaveLength(0);
  });

  it('shows the reason message when the switch fails', async () => {
    stub({
      [`GET ${URL_BASE}`]: () => json(200, settings()),
      [`PUT ${URL_BASE}/fallback`]: () => json(400, { status: 400, reason: 'BRIEFING_SETTINGS_INVALID' }),
    });
    await mount();
    await click(container.querySelector<HTMLButtonElement>('[role="switch"]')!);
    const alert = container.querySelector('[data-reason="BRIEFING_SETTINGS_INVALID"]');
    expect(alert?.textContent).toBe('Some briefing settings are not valid. Check the highlighted fields and try again.');
    expect(container.querySelector('[role="switch"]')?.getAttribute('aria-checked')).toBe('false');
  });

  it('shows the no-credential rejection', async () => {
    stub({
      [`GET ${URL_BASE}`]: () => json(200, settings()),
      [`PUT ${URL_BASE}/scheduler-user`]: () => json(422, { status: 422, reason: 'BRIEFING_NO_JIRA_CREDENTIAL' }),
    });
    await mount();
    await click(button('Use my Jira credential'));
    const alert = container.querySelector('[data-reason="BRIEFING_NO_JIRA_CREDENTIAL"]');
    expect(alert?.textContent).toBe('You have no stored Jira credential. Sign in with a Jira API token first.');
  });

  it.each([
    ['briefing-grace', '0', 'Enter a whole number of minutes from 1 to 240.'],
    ['briefing-grace', '241', 'Enter a whole number of minutes from 1 to 240.'],
    ['briefing-keep', '9', 'Enter a whole number from 10 to 500.'],
    ['briefing-jira_digest-stale', '31', 'Enter a whole number from 1 to 30.'],
    ['briefing-jira_digest-due', '61', 'Enter a whole number from 1 to 60.'],
    ['briefing-sprint_check-backlog', '101', 'Enter a whole number from 1 to 100.'],
    ['briefing-jira_digest-time', '', 'Enter a time, for example 08:44.'],
  ])('rejects %s = "%s" client-side, sends nothing and focuses it', async (id, value, message) => {
    stub({ [`GET ${URL_BASE}`]: () => json(200, settings()) });
    await mount();
    await type(id, value);
    await click(button('Save settings'));
    expect(container.textContent).toContain(message);
    expect(byId(id).getAttribute('aria-invalid')).toBe('true');
    expect(document.activeElement).toBe(byId(id));
    expect(puts()).toHaveLength(0);
  });

  it('requires a day only for an enabled task', async () => {
    stub({ [`GET ${URL_BASE}`]: () => json(200, settings()) });
    await mount();
    for (const day of ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday']) {
      const box = container.querySelectorAll<HTMLInputElement>(`input[aria-label="${day}"]`)[1];
      await click(box);
    }
    await click(button('Save settings'));
    expect(container.textContent).toContain('Choose at least one day.');
    expect(puts()).toHaveLength(0);
  });

  it('disables Save until changed, then sends one PUT with the whole form', async () => {
    let current = settings();
    stub({
      [`GET ${URL_BASE}`]: () => json(200, current),
      [`PUT ${URL_BASE}`]: () => {
        current = settings({ graceMinutes: 45 });
        return json(200, current);
      },
    });
    await mount();
    expect(button('Save settings').disabled).toBe(true);
    await type('briefing-grace', '45');
    expect(button('Save settings').disabled).toBe(false);
    await click(button('Save settings'));
    expect(puts()).toHaveLength(1);
    const body = puts()[0].body as { graceMinutes: number; keepRuns: number; tasks: { id: string }[] };
    expect(body.graceMinutes).toBe(45);
    expect(body.keepRuns).toBe(60);
    expect(body.tasks.map((entry) => entry.id)).toEqual(['jira_digest', 'sprint_check', 'weekly_status']);
    expect(container.textContent).toContain('Settings saved.');
    expect(button('Save settings').disabled).toBe(true);
  });

  it('places server field errors under the matching control', async () => {
    stub({
      [`GET ${URL_BASE}`]: () => json(200, settings()),
      [`PUT ${URL_BASE}`]: () =>
        json(400, { status: 400, reason: 'VALIDATION_FAILED', fieldErrors: { 'tasks[1].time': 'Time is not valid.' } }),
    });
    await mount();
    await type('briefing-grace', '40');
    await click(button('Save settings'));
    const describedBy = byId('briefing-sprint_check-time').getAttribute('aria-describedby');
    expect(document.getElementById(describedBy!)?.textContent).toBe('Time is not valid.');
    expect(container.querySelector('[data-reason="VALIDATION_FAILED"]')).not.toBeNull();
  });

  it('shows the live slot summary', async () => {
    stub({ [`GET ${URL_BASE}`]: () => json(200, settings()) });
    await mount();
    expect(container.textContent).toContain('Runs Sun, Mon, Tue, Wed, Thu at 08:44 Africa/Cairo.');
  });

  it('blocks Run briefing now while dirty and when the task is disabled', async () => {
    stub({ [`GET ${URL_BASE}`]: () => json(200, settings()) });
    await mount();
    const runs = () => [...container.querySelectorAll('button')].filter((b) => b.textContent === 'Run briefing now');
    expect(runs()[2].getAttribute('aria-disabled')).toBe('true');
    expect(container.textContent).toContain('Enable this briefing to run it.');
    await type('briefing-grace', '45');
    expect(runs()[0].getAttribute('aria-disabled')).toBe('true');
    expect(container.textContent).toContain('Save your changes first. Run briefing now uses the saved settings.');
    await click(runs()[0]);
    expect(calls.some((call) => call.url.endsWith('/run'))).toBe(false);
  });

  it('runs a task and reports the counts with a link', async () => {
    stub({
      [`GET ${URL_BASE}`]: () => json(200, settings()),
      [`POST ${URL_BASE}/tasks/sprint_check/run`]: () =>
        json(200, { task: 'sprint_check', name: 'Sprint backlog check', products: 2, attention: 1, failed: 0, needsCredential: 1, skipped: 0 }),
    });
    await mount();
    const runs = [...container.querySelectorAll('button')].filter((b) => b.textContent === 'Run briefing now');
    await click(runs[1]);
    expect(container.textContent).toContain(
      "Sprint backlog check ran for 2 products. 1 need attention. 1 need a credential. Use 'Use my Jira credential' above.",
    );
    const link = [...container.querySelectorAll('a')].find((a) => a.textContent === 'Open Briefings');
    expect(link?.getAttribute('href')).toBe('/briefings');
  });

  it('shows a run failure', async () => {
    stub({
      [`GET ${URL_BASE}`]: () => json(200, settings()),
      [`POST ${URL_BASE}/tasks/sprint_check/run`]: () => json(409, { status: 409, reason: 'BRIEFING_TASK_DISABLED' }),
    });
    await mount();
    const runs = [...container.querySelectorAll('button')].filter((b) => b.textContent === 'Run briefing now');
    await click(runs[1]);
    expect(container.querySelector('[data-reason="BRIEFING_TASK_DISABLED"]')?.textContent).toBe(
      "Couldn't run Sprint backlog check — Enable this briefing to run it. Try again.",
    );
  });

  it('focuses the heading when the hash is #briefings', async () => {
    window.location.hash = '#briefings';
    stub({ [`GET ${URL_BASE}`]: () => json(200, settings()) });
    await mount();
    expect(document.activeElement).toBe(byId('briefings-heading'));
  });
});
