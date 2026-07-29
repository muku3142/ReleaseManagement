import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client';

export type JiraSiteConfig = { baseUrl: string; updatedAt: string; updatedBy: string };
export const jiraSiteQueryKey = ['jira-site'] as const;

export function useJiraSite() {
  return useQuery({
    queryKey: jiraSiteQueryKey,
    queryFn: () => apiFetch<JiraSiteConfig>('/api/settings/jira-site'),
  });
}

export function useUpdateJiraSite() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (baseUrl: string) =>
      apiFetch<JiraSiteConfig>('/api/settings/jira-site', { method: 'PUT', body: JSON.stringify({ baseUrl }) }),
    onSuccess: (config) => queryClient.setQueryData(jiraSiteQueryKey, config),
  });
}
