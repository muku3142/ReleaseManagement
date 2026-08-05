import { useQuery } from '@tanstack/react-query';
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
