import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { apiFetch } from './client';

export type BriefingStatus = 'ok' | 'attention' | 'quiet' | 'failed' | 'needs_credential' | 'skipped';

export type BriefingRun = {
  runAt: string;
  status: BriefingStatus;
  headline: string | null;
  attention: number | null;
  h1: string | null;
  h2: string | null;
  h3: string | null;
};

export type BriefingItem = { key: string | null; text: string };
export type BriefingSection = { title: string; items: BriefingItem[] };

export type AppBriefingRun = {
  id: string;
  productId: string;
  productName: string;
  runAt: string;
  status: BriefingStatus;
  reason: string | null;
  trigger: 'slot' | 'manual';
  headline: string;
  summary: string | null;
  attention: number | null;
  sections: BriefingSection[];
};

export type BriefingTaskFeed = {
  id: string;
  name: string;
  schedule: string;
  readError: boolean;
  latest: BriefingRun | null;
  runs: BriefingRun[];
  appFallback?: boolean;
  appLatest?: AppBriefingRun[];
  appRuns?: AppBriefingRun[];
};

export type BriefingsResponse = { tasks: BriefingTaskFeed[] };

export const briefingsQueryKey = ['briefings'] as const;

export function useBriefings() {
  return useQuery({
    queryKey: briefingsQueryKey,
    queryFn: () => apiFetch<BriefingsResponse>('/api/briefings'),
    refetchInterval: 5 * 60_000,
    placeholderData: keepPreviousData,
  });
}
