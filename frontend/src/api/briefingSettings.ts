import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client';

export type DayCode = 'SUN' | 'MON' | 'TUE' | 'WED' | 'THU' | 'FRI' | 'SAT';
export type BriefingTaskId = 'jira_digest' | 'sprint_check' | 'weekly_status';
export type BriefingTaskSettings = {
  id: BriefingTaskId;
  name: string;
  enabled: boolean;
  days: DayCode[];
  time: string;
  staleAfterWorkingDays: number;
  dueSoonDays: number;
  backlogItems: number;
};
export type BriefingSettings = {
  claudeFeed: { dataDir: string; configFile: string; readable: boolean };
  schedulerUser: { email: string | null; isYou: boolean };
  credentialProblem: boolean;
  fallbackEnabled: boolean;
  graceMinutes: number;
  workDays: DayCode[];
  keepRuns: number;
  tasks: BriefingTaskSettings[];
};
export type BriefingSettingsUpdate = {
  graceMinutes: number;
  workDays: DayCode[];
  keepRuns: number;
  tasks: Omit<BriefingTaskSettings, 'name'>[];
};
export type BriefingRunNowResult = {
  task: string;
  name: string;
  products: number;
  attention: number;
  failed: number;
  needsCredential: number;
  skipped: number;
};

export const briefingSettingsQueryKey = ['briefing-settings'] as const;

export const DAY_CODES: readonly DayCode[] = ['SUN', 'MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT'];
export const DAY_SHORT: Record<DayCode, string> = {
  SUN: 'Sun',
  MON: 'Mon',
  TUE: 'Tue',
  WED: 'Wed',
  THU: 'Thu',
  FRI: 'Fri',
  SAT: 'Sat',
};
export const DAY_LONG: Record<DayCode, string> = {
  SUN: 'Sunday',
  MON: 'Monday',
  TUE: 'Tuesday',
  WED: 'Wednesday',
  THU: 'Thursday',
  FRI: 'Friday',
  SAT: 'Saturday',
};

export function cronSummary(days: DayCode[], time: string): string {
  const names = DAY_CODES.filter((day) => days.includes(day)).map((day) => DAY_SHORT[day]);
  return `Runs ${names.join(', ')} at ${time} Africa/Cairo.`;
}

const BASE = '/api/settings/briefings';

export function useBriefingSettings() {
  return useQuery({ queryKey: briefingSettingsQueryKey, queryFn: () => apiFetch<BriefingSettings>(BASE) });
}

export function useSaveBriefingSettings() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (update: BriefingSettingsUpdate) =>
      apiFetch<BriefingSettings>(BASE, { method: 'PUT', body: JSON.stringify(update) }),
    onSuccess: (settings) => {
      queryClient.setQueryData(briefingSettingsQueryKey, settings);
      void queryClient.invalidateQueries({ queryKey: ['briefings'] });
    },
  });
}

export function useUseMyJiraCredential() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<BriefingSettings>(`${BASE}/scheduler-user`, { method: 'PUT' }),
    onSuccess: (settings) => {
      queryClient.setQueryData(briefingSettingsQueryKey, settings);
      void queryClient.invalidateQueries({ queryKey: ['briefings'] });
    },
  });
}

export function useRunBriefingNow() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (taskId: BriefingTaskId) =>
      apiFetch<BriefingRunNowResult>(`${BASE}/tasks/${taskId}/run`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['briefings'] }),
  });
}

export function useSetBriefingFallback() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (enabled: boolean) =>
      apiFetch<BriefingSettings>(`${BASE}/fallback`, { method: 'PUT', body: JSON.stringify({ enabled }) }),
    onSuccess: (settings) => {
      queryClient.setQueryData(briefingSettingsQueryKey, settings);
      void queryClient.invalidateQueries({ queryKey: ['briefings'] });
    },
  });
}
