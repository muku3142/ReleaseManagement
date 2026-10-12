import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { apiFetch } from './client';
import type { ReadinessLookup } from './documents';
import type { PromotionOutcome, PromotionStage } from './promotions';
import { DOCUMENT_TYPES } from './templates';

// Must equal DashboardController.MAX_HISTORY_SPAN_DAYS (DashboardSpanContractTest reads this line).
export const MAX_HISTORY_SPAN_DAYS = 366;
export const DEFAULT_HISTORY_DAYS = 30;
export const HISTORY_PAGE_SIZE = 50;

const DAY_MS = 86_400_000;
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const ISO_DAY = /^(\d{4})-(\d{2})-(\d{2})$/;

export type DashboardSummary = {
  visibleReleases: number;
  totalReleases: number;
  medianCreatedToFrozenSeconds: number | null;
  createdToFrozenCount: number;
  medianFrozenToProductionSeconds: number | null;
  frozenToProductionCount: number;
  releasesPerStage: { stageId: string; count: number }[];
  notPromoted: number;
};

export type DashboardComposition = {
  tickets: number;
  manual: number;
  linked: number;
  noLink: number;
  failed: number;
  noData: number;
};

export type DashboardChurn = {
  medianCommitsToDone: number | null;
  commitNoData: number;
  reopens: number;
  reopenNoData: number;
  postDoneShare: number | null;
  outliers: number;
  keyTagged: number;
};

export type DashboardReleaseRow = {
  id: string;
  name: string;
  version: string;
  status: string;
  createdAt: string;
  frozenAt: string | null;
  currentStageId: string | null;
  shippedAt: string | null;
  lastFailedStageId: string | null;
  createdToFrozenSeconds: number | null;
  frozenToProductionSeconds: number | null;
  composition: DashboardComposition;
  churn: DashboardChurn;
};

export type DashboardData = {
  summary: DashboardSummary;
  stages: PromotionStage[];
  releases: DashboardReleaseRow[];
};

export type DeploymentEntry = {
  id: string;
  releaseId: string;
  releaseName: string;
  releaseVersion: string;
  stageId: string;
  stageName: string;
  stageRetired: boolean;
  outcome: PromotionOutcome;
  deployedAt: string;
  recordedBy: string;
  note: string | null;
  corrected: boolean;
};

export type DeploymentPage = { entries: DeploymentEntry[]; hasMore: boolean };

export type DashboardSearch = { stage?: string; from?: string; to?: string; all?: boolean };

export type DeploymentFilter = { stageId?: string; fromIso: string; toIso: string };

export type HistoryRange = { ok: true; fromIso: string; toIso: string } | { ok: false; message: string };

function parseDay(value: unknown): Date | undefined {
  if (typeof value !== 'string') return undefined;
  const match = ISO_DAY.exec(value);
  if (!match) return undefined;
  const [y, m, d] = [Number(match[1]), Number(match[2]), Number(match[3])];
  const date = new Date(y, m - 1, d);
  const roundTrips = date.getFullYear() === y && date.getMonth() === m - 1 && date.getDate() === d;
  return roundTrips ? date : undefined;
}

// URL params are attacker-controllable; anything malformed falls back to the default instead of erroring.
export function parseDashboardSearch(search: Record<string, unknown>): DashboardSearch {
  const { stage, from, to, all } = search;
  return {
    stage: typeof stage === 'string' && UUID.test(stage) ? stage : undefined,
    from: parseDay(from) ? (from as string) : undefined,
    to: parseDay(to) ? (to as string) : undefined,
    all: all === true || all === 'true' ? true : undefined,
  };
}

function localDay(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

export function defaultHistoryDates(today: Date = new Date()): { from: string; to: string } {
  const from = new Date(today.getFullYear(), today.getMonth(), today.getDate() - (DEFAULT_HISTORY_DAYS - 1));
  return { from: localDay(from), to: localDay(today) };
}

// Half-open [start of from-day, start of the day after to-day) in the browser zone.
export function historyRange(from: string, to: string): HistoryRange {
  const start = parseDay(from);
  const lastDay = parseDay(to);
  if (!start || !lastDay) return { ok: false, message: 'Enter a valid start and end date.' };
  if (start > lastDay) return { ok: false, message: 'Start date must be on or before end date.' };
  const end = new Date(lastDay.getFullYear(), lastDay.getMonth(), lastDay.getDate() + 1);
  if (Math.round((end.getTime() - start.getTime()) / DAY_MS) > MAX_HISTORY_SPAN_DAYS) {
    return { ok: false, message: `Choose a range of ${MAX_HISTORY_SPAN_DAYS} days or less.` };
  }
  return { ok: true, fromIso: start.toISOString(), toIso: end.toISOString() };
}

function roundedValue(x: number): number {
  return x < 10 ? Number(x.toFixed(1)) : Math.round(x);
}

export function formatDuration(seconds: number | null): string {
  if (seconds === null) return '—';
  const hours = seconds / 3600;
  return hours >= 24 ? `${roundedValue(hours / 24)} d` : `${roundedValue(hours)} h`;
}

// Counts server-computed states and never re-derives staleness (T-05-55).
export function paperworkCounts(releaseIds: string[], stateOf: ReadinessLookup): { needPaperwork: number; stale: number } {
  let needPaperwork = 0;
  let stale = 0;
  for (const id of releaseIds) {
    const states = DOCUMENT_TYPES.map((type) => stateOf(id, type) ?? 'NOT_GENERATED');
    if (states.some((s) => s === 'NOT_GENERATED' || s === 'STALE')) needPaperwork += 1;
    if (states.some((s) => s === 'STALE')) stale += 1;
  }
  return { needPaperwork, stale };
}

export function deploymentsPath(filter: DeploymentFilter, offset: number): string {
  const params = new URLSearchParams();
  if (filter.stageId) params.set('stageId', filter.stageId);
  params.set('from', filter.fromIso);
  params.set('to', filter.toIso);
  params.set('limit', String(HISTORY_PAGE_SIZE));
  params.set('offset', String(offset));
  return `/api/dashboard/deployments?${params.toString()}`;
}

// OFFSET can shift when a promotion is recorded between page loads, so a row id already shown is dropped.
export function mergeDeploymentPages(pages: DeploymentPage[]): DeploymentEntry[] {
  const seen = new Set<string>();
  return pages
    .flatMap((page) => page.entries)
    .filter((entry) => !seen.has(entry.id) && seen.add(entry.id));
}

export function nextDeploymentOffset(lastPage: DeploymentPage, pages: DeploymentPage[]): number | undefined {
  return lastPage.hasMore ? pages.reduce((total, page) => total + page.entries.length, 0) : undefined;
}

export const dashboardReleasesQueryKey = (all: boolean) => ['dashboard', 'releases', all ? 'all' : 'active'] as const;
export const deploymentsQueryKey = (filter: DeploymentFilter) =>
  ['dashboard', 'deployments', filter.stageId ?? 'all', filter.fromIso, filter.toIso] as const;

export function useDashboardReleases(all: boolean) {
  return useQuery({
    queryKey: dashboardReleasesQueryKey(all),
    queryFn: () => apiFetch<DashboardData>(`/api/dashboard/releases?scope=${all ? 'all' : 'active'}`),
  });
}

const NO_FILTER: DeploymentFilter = { fromIso: '', toIso: '' };

// A null filter means the typed range is invalid: no request is sent and the previous results stay.
export function useDeploymentHistory(filter: DeploymentFilter | null) {
  return useInfiniteQuery({
    queryKey: deploymentsQueryKey(filter ?? NO_FILTER),
    queryFn: ({ pageParam }) => apiFetch<DeploymentPage>(deploymentsPath(filter ?? NO_FILTER, pageParam)),
    initialPageParam: 0,
    getNextPageParam: nextDeploymentOffset,
    enabled: filter !== null,
    placeholderData: keepPreviousData,
  });
}
