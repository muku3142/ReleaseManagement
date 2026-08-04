import { useQuery } from '@tanstack/react-query';
import { apiFetch } from './client';

export type JiraProject = { id: string; key: string; name: string };
export type JiraVersion = { id: string; name: string };

export const jiraProjectsQueryKey = ['jira-projects'] as const;

export function useJiraProjects() {
  return useQuery({
    queryKey: jiraProjectsQueryKey,
    queryFn: () => apiFetch<JiraProject[]>('/api/jira/projects'),
  });
}

export function useJiraVersions(projectId: string) {
  return useQuery({
    queryKey: ['jira-versions', projectId] as const,
    queryFn: () => apiFetch<JiraVersion[]>(`/api/jira/projects/${projectId}/versions`),
    enabled: projectId !== '',
  });
}
