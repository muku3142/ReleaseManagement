import { useQuery } from '@tanstack/react-query';
import { apiFetch } from './client';

export type JiraProject = { id: string; key: string; name: string };
export type JiraSprint = { id: string; name: string; state: string };

export const jiraProjectsQueryKey = ['jira-projects'] as const;

export function useJiraProjects() {
  return useQuery({
    queryKey: jiraProjectsQueryKey,
    queryFn: () => apiFetch<JiraProject[]>('/api/jira/projects'),
  });
}

export function useJiraSprints(projectId: string) {
  return useQuery({
    queryKey: ['jira-sprints', projectId] as const,
    queryFn: () => apiFetch<JiraSprint[]>(`/api/jira/projects/${projectId}/sprints`),
    enabled: projectId !== '',
  });
}
