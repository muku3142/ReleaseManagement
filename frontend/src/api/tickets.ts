import { useMutation } from '@tanstack/react-query';
import { apiFetch } from './client';

export type Ticket = {
  key: string;
  summary: string;
  issueType: string;
  status: string;
  resolution: string | null;
  assignee: string | null;
  reporter: string;
  fixVersions: string[];
  description: string;
  comments: string[];
  commentTotal: number;
};

export const STATUS_CATEGORIES = ['TO_DO', 'IN_PROGRESS', 'DONE'] as const;
export type StatusCategory = (typeof STATUS_CATEGORIES)[number];

export const STATUS_CATEGORY_LABELS: Record<StatusCategory, string> = {
  TO_DO: 'To Do',
  IN_PROGRESS: 'In Progress',
  DONE: 'Done',
};

// JIRA-07: a release is cut from finished work, so the screen opens on Done only. The server
// applies the same default independently, so this is the UI agreeing with it, not the only guard.
export const DEFAULT_STATUS_CATEGORIES: StatusCategory[] = ['DONE'];

export type TicketSearchCriteria =
  | {
      mode: 'fixVersion';
      projectId: string;
      fixVersionId: string;
      statusCategories: StatusCategory[];
    }
  | { mode: 'jql'; jql: string };

export type TicketSearchResponse = { tickets: Ticket[] };

// The backend rejects a body carrying both filter modes, so the discriminated union is
// narrowed to exactly one shape here rather than sending optional fields and hoping.
// statusCategories is sent only on the picker path — on the JQL path the backend forwards the
// query verbatim, so sending it would imply a filter that is not actually applied.
export function toSearchBody(criteria: TicketSearchCriteria): Record<string, unknown> {
  return criteria.mode === 'jql'
    ? { jql: criteria.jql }
    : {
        projectId: criteria.projectId,
        fixVersionId: criteria.fixVersionId,
        statusCategories: criteria.statusCategories,
      };
}

export function useTicketSearch() {
  return useMutation({
    mutationFn: (criteria: TicketSearchCriteria) =>
      apiFetch<TicketSearchResponse>('/api/tickets/search', {
        method: 'POST',
        body: JSON.stringify(toSearchBody(criteria)),
      }),
  });
}
