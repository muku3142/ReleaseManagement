import { keepPreviousData, useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { apiFetch } from './client';
import type { ReadinessLookup } from './documents';
import type { PromotionOutcome, PromotionStage } from './promotions';
import { DOCUMENT_TYPES } from './templates';

// Must equal DashboardController.MAX_HISTORY_SPAN_DAYS (DashboardSpanContractTest reads this line).
export const MAX_HISTORY_SPAN_DAYS = 366;
// Must equal DashboardController.MAX_PRODUCT_FILTER (DashboardSpanContractTest reads this line).
export const MAX_PRODUCT_FILTER = 50;
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

export type DashboardStoryPoints = { total: number; estimated: number; unestimated: number; unread: number };

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
  storyPoints: DashboardStoryPoints;
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

export type DashboardDocsFilter = 'missing' | 'stale';

export type DashboardSearch = {
  stage?: string;
  from?: string;
  to?: string;
  all?: boolean;
  tab?: 'portfolio';
  products?: string[];
  docs?: DashboardDocsFilter;
  archived?: true;
};

export type DeploymentFilter = { stageId?: string; fromIso: string; toIso: string; productIds?: string[] };

export type ReleaseBrief = { releaseId: string; name: string; version: string; frozenAt: string };
export type RunBrief = { releaseId: string; name: string; version: string; installedAt: string; inProduction: boolean };
export type StatusCounts = { open: number; frozen: number; inProduction: number };
export type PortfolioProductRow = {
  id: string;
  name: string;
  archived: boolean;
  releases: StatusCounts;
  newestFrozen: ReleaseBrief | null;
  linkedClients: number;
  clientsOnNewest: number;
};
export type PortfolioTiles = {
  clientsBehind: number;
  notInstalled: number;
  activeProducts: number;
  activeClients: number;
  installsLast30Days: number;
};
export type PortfolioSummary = {
  tiles: PortfolioTiles;
  products: PortfolioProductRow[];
  totals: StatusCounts;
  unassigned: StatusCounts | null;
};
export type BehindRow = {
  clientId: string;
  clientName: string;
  clientArchived: boolean;
  productId: string;
  productName: string;
  runs: RunBrief;
  releasesBehind: number;
  newest: ReleaseBrief;
};
export type NotInstalledRow = {
  clientId: string;
  clientName: string;
  clientArchived: boolean;
  productId: string;
  productName: string;
  newest: ReleaseBrief | null;
};
export type PortfolioBehind = { behind: BehindRow[]; notInstalled: NotInstalledRow[] };
export type MatrixColumn = { id: string; name: string; archived: boolean };
export type MatrixCell = {
  clientId: string;
  productId: string;
  run: RunBrief | null;
  releasesBehind: number | null;
  newest: ReleaseBrief | null;
  upToDate: boolean;
};
export type PortfolioMatrix = { products: MatrixColumn[]; clients: MatrixColumn[]; cells: MatrixCell[] };
export type PortfolioSection = 'summary' | 'behind' | 'matrix';

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

export function parseProducts(value: unknown): string[] | undefined {
  const candidates = Array.isArray(value) ? value : typeof value === 'string' ? value.split(',') : [];
  const ids = [...new Set(candidates.filter((id): id is string => typeof id === 'string' && UUID.test(id)))];
  return ids.length > 0 ? ids.slice(0, MAX_PRODUCT_FILTER) : undefined;
}

// URL params are attacker-controllable; anything malformed falls back to the default instead of erroring.
export function parseDashboardSearch(search: Record<string, unknown>): DashboardSearch {
  const { stage, from, to, all, tab, products, docs, archived } = search;
  return {
    tab: tab === 'portfolio' ? 'portfolio' : undefined,
    products: parseProducts(products),
    docs: docs === 'missing' || docs === 'stale' ? docs : undefined,
    archived: archived === true || archived === 'true' ? true : undefined,
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

function documentStates(releaseId: string, stateOf: ReadinessLookup) {
  return DOCUMENT_TYPES.map((type) => stateOf(releaseId, type) ?? 'NOT_GENERATED');
}

// Server-computed states are only counted, never re-derived (T-05-55); NO_TEMPLATE is never counted.
export function missingDocuments(releaseId: string, stateOf: ReadinessLookup): boolean {
  return documentStates(releaseId, stateOf).some((s) => s === 'NOT_GENERATED' || s === 'STALE');
}

export function outOfDateDocuments(releaseId: string, stateOf: ReadinessLookup): boolean {
  return documentStates(releaseId, stateOf).some((s) => s === 'STALE');
}

export function paperworkCounts(releaseIds: string[], stateOf: ReadinessLookup): { needPaperwork: number; stale: number } {
  return {
    needPaperwork: releaseIds.filter((id) => missingDocuments(id, stateOf)).length,
    stale: releaseIds.filter((id) => outOfDateDocuments(id, stateOf)).length,
  };
}

export function deploymentsPath(filter: DeploymentFilter, offset: number): string {
  const params = new URLSearchParams();
  if (filter.stageId) params.set('stageId', filter.stageId);
  params.set('from', filter.fromIso);
  params.set('to', filter.toIso);
  params.set('limit', String(HISTORY_PAGE_SIZE));
  params.set('offset', String(offset));
  if (filter.productIds?.length) params.set('productIds', sortedIds(filter.productIds));
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

// Selection order must not change the cache key or the request.
export const sortedIds = (ids?: string[]) => [...(ids ?? [])].sort().join(',');

export const dashboardReleasesQueryKey = (all: boolean, productIds: string[]) =>
  ['dashboard', 'releases', all ? 'all' : 'active', sortedIds(productIds)] as const;
export const deploymentsQueryKey = (filter: DeploymentFilter) =>
  ['dashboard', 'deployments', filter.stageId ?? 'all', filter.fromIso, filter.toIso, sortedIds(filter.productIds)] as const;
export const portfolioQueryKey = (section: PortfolioSection, productIds: string[], includeArchived: boolean) =>
  ['dashboard', 'portfolio', section, sortedIds(productIds), includeArchived ? 'archived' : 'active'] as const;

export function portfolioPath(section: PortfolioSection, productIds: string[], includeArchived: boolean): string {
  const params = new URLSearchParams();
  if (productIds.length > 0) params.set('productIds', sortedIds(productIds));
  params.set('includeArchived', String(includeArchived));
  return `/api/dashboard/portfolio/${section}?${params.toString()}`;
}

export function useDashboardReleases(all: boolean, productIds: string[]) {
  const params = new URLSearchParams({ scope: all ? 'all' : 'active' });
  if (productIds.length > 0) params.set('productIds', sortedIds(productIds));
  return useQuery({
    queryKey: dashboardReleasesQueryKey(all, productIds),
    queryFn: () => apiFetch<DashboardData>(`/api/dashboard/releases?${params.toString()}`),
  });
}

// One query per section so a failing section blanks only itself.
export function usePortfolioSummary(productIds: string[], includeArchived: boolean) {
  return useQuery({
    queryKey: portfolioQueryKey('summary', productIds, includeArchived),
    queryFn: () => apiFetch<PortfolioSummary>(portfolioPath('summary', productIds, includeArchived)),
  });
}

export function usePortfolioBehind(productIds: string[], includeArchived: boolean) {
  return useQuery({
    queryKey: portfolioQueryKey('behind', productIds, includeArchived),
    queryFn: () => apiFetch<PortfolioBehind>(portfolioPath('behind', productIds, includeArchived)),
  });
}

export function usePortfolioMatrix(productIds: string[], includeArchived: boolean) {
  return useQuery({
    queryKey: portfolioQueryKey('matrix', productIds, includeArchived),
    queryFn: () => apiFetch<PortfolioMatrix>(portfolioPath('matrix', productIds, includeArchived)),
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
