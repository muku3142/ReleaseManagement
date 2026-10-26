import { act, createElement } from 'react';
import { createRoot } from 'react-dom/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { useBriefings, type BriefingStatus } from '../../src/api/briefings';
import { attentionText, formatCairo, worstStatus } from '../../src/briefings/briefingFormat';

describe('briefings API hook', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('F1: useBriefings calls /api/briefings once and returns typed data', async () => {
    const body = {
      tasks: [
        {
          id: 'jira_digest',
          name: 'Daily Jira digest',
          schedule: 'Sun-Thu 08:44 · Africa/Cairo',
          readError: false,
          latest: { runAt: '2026-10-26T05:44:12Z', status: 'attention', headline: 'h', attention: 5, h1: null, h2: null, h3: null },
          runs: [],
        },
      ],
    };
    const fetchSpy = vi
      .spyOn(globalThis, 'fetch')
      .mockResolvedValue(new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
    let status: BriefingStatus | undefined;
    function Probe() {
      status = useBriefings().data?.tasks[0].latest?.status;
      return null;
    }
    const root = createRoot(document.createElement('div'));
    await act(async () => {
      root.render(createElement(QueryClientProvider, { client: new QueryClient() }, createElement(Probe)));
    });
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    await act(async () => root.unmount());
    expect(fetchSpy).toHaveBeenCalledTimes(1);
    expect(String(fetchSpy.mock.calls[0][0])).toBe('/api/briefings');
    expect(status).toBe('attention');
  });
});

describe('briefing format helpers', () => {
  it('formats times in Africa/Cairo across the DST change', () => {
    expect(formatCairo('2026-10-26T05:44:12Z')).toBe('Mon 26 Oct, 08:44');
    expect(formatCairo('2026-11-01T06:44:00Z')).toBe('Sun 1 Nov, 08:44');
    expect(formatCairo('nope')).toBe('Unknown time');
  });

  it('picks the worst status', () => {
    expect(worstStatus(['ok', 'failed', 'attention'])).toBe('failed');
    expect(worstStatus([])).toBeNull();
  });

  it('words the attention count', () => {
    expect(attentionText(1)).toBe('1 item needs attention');
    expect(attentionText(3)).toBe('3 items need attention');
  });
});
