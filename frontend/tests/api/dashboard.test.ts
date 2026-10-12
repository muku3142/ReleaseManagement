import { describe, expect, it } from 'vitest';
import {
  MAX_HISTORY_SPAN_DAYS,
  defaultHistoryDates,
  deploymentsPath,
  formatDuration,
  historyRange,
  mergeDeploymentPages,
  nextDeploymentOffset,
  paperworkCounts,
  parseDashboardSearch,
  type DeploymentEntry,
  type DeploymentPage,
} from '../../src/api/dashboard';
import type { DocumentReadinessState } from '../../src/api/documents';

const UUID = '3f2b8c1e-7a4d-4e6b-9c1a-2d5e8f0a1b3c';

function entry(id: string): DeploymentEntry {
  return {
    id,
    releaseId: 'r',
    releaseName: 'n',
    releaseVersion: '1',
    stageId: 's',
    stageName: 'S',
    stageRetired: false,
    outcome: 'SUCCESS',
    deployedAt: '2026-09-01T00:00:00Z',
    recordedBy: 'u',
    note: null,
    corrected: false,
  };
}

function page(ids: string[], hasMore = false): DeploymentPage {
  return { entries: ids.map(entry), hasMore };
}

describe('parseDashboardSearch', () => {
  it('keeps well-formed values', () => {
    expect(parseDashboardSearch({ stage: UUID, from: '2026-09-01', to: '2026-09-30', all: 'true' })).toEqual({
      stage: UUID,
      from: '2026-09-01',
      to: '2026-09-30',
      all: true,
    });
  });

  it('drops malformed values to undefined', () => {
    expect(parseDashboardSearch({ stage: 'x', from: '2026-13-45', to: 'yesterday', all: 'yes' })).toEqual({
      stage: undefined,
      from: undefined,
      to: undefined,
      all: undefined,
    });
  });

  it('rejects non-string inputs', () => {
    expect(parseDashboardSearch({ stage: 5, from: ['2026-09-01'], to: {} }).from).toBeUndefined();
  });
});

describe('historyRange', () => {
  it('accepts a single day as a half-open range in the browser zone', () => {
    expect(historyRange('2026-09-01', '2026-09-01')).toEqual({
      ok: true,
      fromIso: new Date(2026, 8, 1).toISOString(),
      toIso: new Date(2026, 8, 2).toISOString(),
    });
  });

  it('rejects from after to', () => {
    expect(historyRange('2026-09-02', '2026-09-01')).toEqual({
      ok: false,
      message: 'Start date must be on or before end date.',
    });
  });

  it('accepts 366 inclusive days and rejects 367', () => {
    expect(MAX_HISTORY_SPAN_DAYS).toBe(366);
    expect(historyRange('2025-01-01', '2026-01-01').ok).toBe(true);
    expect(historyRange('2025-01-01', '2026-01-02')).toEqual({ ok: false, message: 'Choose a range of 366 days or less.' });
  });
});

describe('defaultHistoryDates', () => {
  it('spans 30 days inclusive of today', () => {
    expect(defaultHistoryDates(new Date(2026, 9, 12))).toEqual({ from: '2026-09-13', to: '2026-10-12' });
  });
});

describe('formatDuration', () => {
  it.each([
    [5400, '1.5 h'],
    [43200, '12 h'],
    [129600, '1.5 d'],
    [1296000, '15 d'],
    [null, '—'],
  ])('formats %s as %s', (seconds, expected) => {
    expect(formatDuration(seconds)).toBe(expected);
  });
});

describe('paperworkCounts', () => {
  it('counts releases needing paperwork and stale ones, treating missing as not generated', () => {
    const states: Record<string, DocumentReadinessState> = {
      'r1:RELEASE_NOTES': 'STALE',
      'r1:RUNBOOK': 'UP_TO_DATE',
      'r1:APPROVAL': 'UP_TO_DATE',
      'r2:RELEASE_NOTES': 'UP_TO_DATE',
      'r2:RUNBOOK': 'UP_TO_DATE',
      'r2:APPROVAL': 'NO_TEMPLATE',
      'r3:RELEASE_NOTES': 'UP_TO_DATE',
      'r3:APPROVAL': 'UP_TO_DATE',
    };
    const lookup = (id: string, type: string) => states[`${id}:${type}`];
    expect(paperworkCounts(['r1', 'r2', 'r3'], lookup)).toEqual({ needPaperwork: 2, stale: 1 });
  });
});

describe('deployment paging', () => {
  it('merges pages dropping repeated ids', () => {
    const merged = mergeDeploymentPages([page(['a', 'b']), page(['b', 'c'])]);
    expect(merged.map((e) => e.id)).toEqual(['a', 'b', 'c']);
  });

  it('offsets by rows loaded while more remain', () => {
    const first = Array.from({ length: 50 }, (_, i) => `x${i}`);
    const second = Array.from({ length: 50 }, (_, i) => `y${i}`);
    const pages = [page(first, true), page(second, true)];
    expect(nextDeploymentOffset(pages[1], pages)).toBe(100);
    expect(nextDeploymentOffset(page(second, false), pages)).toBeUndefined();
  });

  it('encodes the query and omits an absent stage', () => {
    const filter = { stageId: 's1', fromIso: '2026-09-01T00:00:00.000Z', toIso: '2026-10-01T00:00:00.000Z' };
    const params = new URLSearchParams(deploymentsPath(filter, 50).split('?')[1]);
    expect(Object.fromEntries(params)).toEqual({
      stageId: 's1',
      from: filter.fromIso,
      to: filter.toIso,
      limit: '50',
      offset: '50',
    });
    expect(deploymentsPath({ fromIso: 'a', toIso: 'b' }, 0)).not.toContain('stageId');
  });
});
