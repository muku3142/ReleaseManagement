import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client';

export type ReleaseStatus = 'OPEN' | 'FROZEN';

export const MANUAL_ITEM_CATEGORIES = [
  'CONFIG_CHANGE',
  'DATABASE_SCRIPT',
  'INFRASTRUCTURE',
  'OTHER',
] as const;
export type ManualItemCategory = (typeof MANUAL_ITEM_CATEGORIES)[number];

// D-03-03 fixes these four in code on both sides; the labels are the UI spec's exact copy.
export const MANUAL_ITEM_CATEGORY_LABELS: Record<ManualItemCategory, string> = {
  CONFIG_CHANGE: 'Config change',
  DATABASE_SCRIPT: 'Database script',
  INFRASTRUCTURE: 'Infrastructure',
  OTHER: 'Other',
};

export type ReleaseSummary = {
  id: string;
  name: string;
  version: string;
  status: ReleaseStatus;
  jiraProjectKey: string | null;
  createdAt: string;
  createdBy: string;
  frozenAt: string | null;
  frozenBy: string | null;
  lastRefreshedAt: string | null;
  itemCount: number;
};

export type ReleaseItem = {
  key: string;
  summary: string;
  issueType: string;
  status: string;
  resolution: string | null;
  assignee: string | null;
  reporter: string | null;
  fixVersions: string[];
  description: string;
  comments: string[];
  commentTotal: number;
  addedAt: string;
};

export type ManualItem = {
  id: string;
  title: string;
  description: string;
  category: ManualItemCategory;
  addedAt: string;
};

export type Release = Omit<ReleaseSummary, 'itemCount'> & {
  items: ReleaseItem[];
  manualItems: ManualItem[];
};

export type ReleaseSearchResponse = { releases: ReleaseSummary[] };

export const releasesQueryKey = (query: string) => ['releases', query] as const;
export const releaseQueryKey = (releaseId: string) => ['release', releaseId] as const;

// The query string is encoded rather than interpolated raw: a release name may legitimately
// contain a space, & or # and those would otherwise change the URL's meaning.
export function releaseSearchPath(query: string): string {
  return query === '' ? '/api/releases' : `/api/releases?query=${encodeURIComponent(query)}`;
}

export function useReleases(query: string) {
  return useQuery({
    queryKey: releasesQueryKey(query),
    queryFn: () => apiFetch<ReleaseSearchResponse>(releaseSearchPath(query)),
  });
}

export function useRelease(releaseId: string) {
  return useQuery({
    queryKey: releaseQueryKey(releaseId),
    queryFn: () => apiFetch<Release>(`/api/releases/${releaseId}`),
    // A 404 is a permanent answer for a fixed id - retrying it three times only delays the
    // RELEASE_NOT_FOUND message the UI spec requires.
    retry: false,
  });
}

// notFound: requested keys Jira did not return. Without it a 3-ticket push that resolved one
// ticket reads as a plain "1 ticket(s) added" success (D-03-13).
export type ReleaseAddResult = {
  release: Release;
  added: number;
  alreadyPresent: number;
  notFound: number;
};

export type CreateReleaseInput = {
  name: string;
  version: string;
  jiraProjectKey: string | null;
  ticketKeys: string[];
};

// D-03-02: one request creates the release and adds the selection, so a failed add can never
// leave an orphan empty release behind. There is deliberately no create-then-add client path.
export function useCreateRelease() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: CreateReleaseInput) =>
      apiFetch<ReleaseAddResult>('/api/releases', {
        method: 'POST',
        body: JSON.stringify({
          name: input.name,
          version: input.version,
          ...(input.jiraProjectKey ? { jiraProjectKey: input.jiraProjectKey } : {}),
          ticketKeys: input.ticketKeys,
        }),
      }),
    onSuccess: (result) => {
      queryClient.setQueryData(releaseQueryKey(result.release.id), result.release);
      queryClient.invalidateQueries({ queryKey: ['releases'] });
    },
  });
}

export function useAddTicketsToRelease() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { releaseId: string; ticketKeys: string[] }) =>
      apiFetch<ReleaseAddResult>(`/api/releases/${vars.releaseId}/items`, {
        method: 'POST',
        body: JSON.stringify({ ticketKeys: vars.ticketKeys }),
      }),
    onSuccess: (result) => {
      queryClient.setQueryData(releaseQueryKey(result.release.id), result.release);
      queryClient.invalidateQueries({ queryKey: ['releases'] });
    },
  });
}

// D-03-02: a frozen release is not a valid target. The server returns releases newest-first, so
// the most recently created one - the likeliest target of the current push - is already at the
// top and no client-side sort is applied.
export function openReleases(releases: ReleaseSummary[]): ReleaseSummary[] {
  return releases.filter((r) => r.status === 'OPEN');
}

// Shared success handling for every mutation that returns the whole Release - the backend
// returns it precisely so no follow-up read is needed.
function useReleaseMutation<TVars>(request: (vars: TVars) => Promise<Release>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (release) => {
      queryClient.setQueryData(releaseQueryKey(release.id), release);
      queryClient.invalidateQueries({ queryKey: ['releases'] });
    },
  });
}

export function useRefreshRelease() {
  return useReleaseMutation((releaseId: string) =>
    apiFetch<Release>(`/api/releases/${releaseId}/refresh`, { method: 'POST' }),
  );
}

// D-03-10: freeze is one-way. No reverse mutation exists or should be added here.
export function useFreezeRelease() {
  return useReleaseMutation((releaseId: string) =>
    apiFetch<Release>(`/api/releases/${releaseId}/freeze`, { method: 'POST' }),
  );
}

export function useRemoveTicketItem() {
  return useReleaseMutation((vars: { releaseId: string; ticketKey: string }) =>
    apiFetch<Release>(`/api/releases/${vars.releaseId}/items/${encodeURIComponent(vars.ticketKey)}`, {
      method: 'DELETE',
    }),
  );
}

export function useAddManualItem() {
  return useReleaseMutation(
    (vars: { releaseId: string; title: string; description: string; category: ManualItemCategory }) =>
      apiFetch<Release>(`/api/releases/${vars.releaseId}/manual-items`, {
        method: 'POST',
        body: JSON.stringify({ title: vars.title, description: vars.description, category: vars.category }),
      }),
  );
}

export function useUpdateManualItem() {
  return useReleaseMutation(
    (vars: {
      releaseId: string;
      manualItemId: string;
      title: string;
      description: string;
      category: ManualItemCategory;
    }) =>
      apiFetch<Release>(`/api/releases/${vars.releaseId}/manual-items/${vars.manualItemId}`, {
        method: 'PUT',
        body: JSON.stringify({ title: vars.title, description: vars.description, category: vars.category }),
      }),
  );
}

export function useRemoveManualItem() {
  return useReleaseMutation((vars: { releaseId: string; manualItemId: string }) =>
    apiFetch<Release>(`/api/releases/${vars.releaseId}/manual-items/${vars.manualItemId}`, {
      method: 'DELETE',
    }),
  );
}

// DELETE /api/releases/{id} returns 204 with no body, so this is the one mutation that cannot
// reuse useReleaseMutation.
export function useDeleteRelease() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (releaseId: string) => apiFetch<void>(`/api/releases/${releaseId}`, { method: 'DELETE' }),
    onSuccess: (_data, releaseId) => {
      queryClient.removeQueries({ queryKey: releaseQueryKey(releaseId) });
      queryClient.invalidateQueries({ queryKey: ['releases'] });
    },
  });
}
