import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob, triggerDownload } from './client';
import { parseProducts, sortedIds } from './dashboard';

export type RoadmapStatus = 'IDEA' | 'PLANNED' | 'IN_PROGRESS' | 'DONE';
export type RiceField = 'REACH' | 'IMPACT' | 'CONFIDENCE' | 'EFFORT';

export type RoadmapItem = {
  id: string;
  source: 'JIRA' | 'MANUAL';
  jiraKey: string | null;
  title: string;
  jiraStatus: string | null;
  jiraSyncedAt: string | null;
  jiraUnavailable: boolean;
  product: { id: string; name: string; archived: boolean } | null;
  status: RoadmapStatus;
  targetQuarter: string | null;
  owner: string | null;
  notes: string | null;
  reach: number | null;
  impact: number | null;
  confidence: number | null;
  effort: number | null;
  scoreDisplay: string | null;
  rank: number | null;
  missing: RiceField[];
  calculated: { reach: number | null; impact: number | null; confidence: number | null };
  overrides: { reach: string | null; impact: string | null; confidence: string | null };
  calculatedFields: RiceField[];
  jiraPriority: string | null;
  readinessSignals: number | null;
  createdAt: string;
  archived: boolean;
};
export type RoadmapList = { items: RoadmapItem[] };
export type RoadmapInputs = {
  impact: number | null;
  confidence: number | null;
  effort: number | null;
};
export type RoadmapDetails = {
  title: string | null;
  status: RoadmapStatus;
  productId: string;
  targetQuarter: string | null;
  owner: string | null;
  notes: string | null;
};
export type RoadmapAddResult = {
  added: { itemId: string; jiraKey: string }[];
  alreadyPresent: { jiraKey: string; itemId: string }[];
  notFound: string[];
  rejected: { jiraKey: string; reason: string }[];
};
export type RoadmapRefreshResult = { refreshed: number; unavailable: { itemId: string; jiraKey: string }[] };
export type InputHistoryEntry = {
  field: string;
  oldValue: number | null;
  newValue: number | null;
  changedAt: string;
  changedBy: string;
  kind: 'ENTERED' | 'OVERRIDDEN' | 'RESET';
  reason: string | null;
};
export type RoadmapHistory = { entries: InputHistoryEntry[] };

// Must equal the server's cap on keys per add request (ROADMAP_TOO_MANY_ITEMS).
export const ROADMAP_MAX_KEYS = 100;

export type RoadmapSearch = { products?: string[]; done?: true; archived?: true; item?: string };
export type RoadmapFilter = { products: string[]; done: boolean; archived: boolean };

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const isTrue = (value: unknown): true | undefined => (value === true || value === 'true' ? true : undefined);

// URL params are attacker-controllable; anything malformed falls back to the default instead of erroring (T-17-10).
export function parseRoadmapSearch(search: Record<string, unknown>): RoadmapSearch {
  const { products, done, archived, item } = search;
    return {
    products: parseProducts(products),
    done: isTrue(done),
    archived: isTrue(archived),
    item: typeof item === 'string' && UUID.test(item) ? item : undefined,
  };
}

export function roadmapQueryString(filter: RoadmapFilter): string {
  const params = new URLSearchParams();
  if (filter.products.length > 0) params.set('productIds', sortedIds(filter.products));
  if (filter.done) params.set('done', 'true');
  if (filter.archived) params.set('archived', 'true');
  return params.toString().replace(/%2C/g, ',');
}

const withQuery = (path: string, filter: RoadmapFilter) => {
  const qs = roadmapQueryString(filter);
  return qs ? `${path}?${qs}` : path;
};

export const roadmapQueryKey = (filter: RoadmapFilter) =>
  ['roadmap', 'list', sortedIds(filter.products), filter.done, filter.archived] as const;
export const roadmapHistoryQueryKey = (itemId: string) => ['roadmap', 'history', itemId] as const;

const ITEMS_URL = '/api/roadmap/items';

const post = (body?: unknown) =>
  ({ method: 'POST', ...(body === undefined ? {} : { body: JSON.stringify(body) }) }) satisfies RequestInit;
const put = (body: unknown) => ({ method: 'PUT', body: JSON.stringify(body) }) satisfies RequestInit;

export function useRoadmap(filter: RoadmapFilter, enabled = true) {
  return useQuery({
    queryKey: roadmapQueryKey(filter),
    queryFn: () => apiFetch<RoadmapList>(withQuery(ITEMS_URL, filter)),
    enabled,
  });
}

export function useRoadmapHistory(itemId: string, enabled: boolean) {
  return useQuery({
    queryKey: roadmapHistoryQueryKey(itemId),
    queryFn: () => apiFetch<RoadmapHistory>(`${ITEMS_URL}/${itemId}/history`),
    enabled,
  });
}

// Scores and ranks are server-computed across the whole list, so any write makes every list and history stale.
function useRoadmapMutation<TVars, TResult>(request: (vars: TVars) => Promise<TResult>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['roadmap'] }),
  });
}

export function useAddManualItem() {
  return useRoadmapMutation((body: { title: string; productId: string }) =>
    apiFetch<RoadmapItem>(ITEMS_URL, post(body)),
  );
}

export function useAddFromJira() {
  return useRoadmapMutation((keys: string[]) =>
    apiFetch<RoadmapAddResult>(`${ITEMS_URL}/jira`, post({ keys })),
  );
}

export function useAddFromRelease() {
  return useRoadmapMutation((body: { releaseId: string; ticketKeys: string[] }) =>
    apiFetch<RoadmapAddResult>(`${ITEMS_URL}/from-release`, post(body)),
  );
}

export type CalculatedField = 'REACH' | 'IMPACT' | 'CONFIDENCE';

export function useOverrideInput() {
  return useRoadmapMutation(
    ({ itemId, field, value, reason }: { itemId: string; field: CalculatedField; value: number; reason: string }) =>
      apiFetch<RoadmapItem>(`${ITEMS_URL}/${itemId}/overrides/${field}`, put({ value, reason })),
  );
}

export function useResetOverride() {
  return useRoadmapMutation(({ itemId, field }: { itemId: string; field: CalculatedField }) =>
    apiFetch<RoadmapItem>(`${ITEMS_URL}/${itemId}/overrides/${field}`, { method: 'DELETE' }),
  );
}

export function useSetInputs() {
  return useRoadmapMutation(({ itemId, inputs }: { itemId: string; inputs: RoadmapInputs }) =>
    apiFetch<RoadmapItem>(`${ITEMS_URL}/${itemId}/inputs`, put(inputs)),
  );
}

export function useUpdateDetails() {
  return useRoadmapMutation(({ itemId, details }: { itemId: string; details: RoadmapDetails }) =>
    apiFetch<RoadmapItem>(`${ITEMS_URL}/${itemId}/details`, put(details)),
  );
}

export function useArchiveItem() {
  return useRoadmapMutation((itemId: string) => apiFetch<void>(`${ITEMS_URL}/${itemId}/archive`, post()));
}

export function useRestoreItem() {
  return useRoadmapMutation((itemId: string) => apiFetch<void>(`${ITEMS_URL}/${itemId}/restore`, post()));
}

export function useRefreshAll() {
  return useRoadmapMutation(() => apiFetch<RoadmapRefreshResult>('/api/roadmap/refresh', post()));
}

export function useRefreshItem() {
  return useRoadmapMutation((itemId: string) =>
    apiFetch<RoadmapRefreshResult>(`${ITEMS_URL}/${itemId}/refresh`, post()),
  );
}

function localDay(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

// A mutation, not a query: exporting is an action with pending/error states and its file must never be cached.
export function useExportRoadmapCsv() {
  return useMutation({
    mutationFn: async (filter: RoadmapFilter) => {
      const blob = await apiFetchBlob(withQuery('/api/roadmap/export.csv', filter));
      triggerDownload(blob, `roadmap-${localDay(new Date())}.csv`);
    },
  });
}
