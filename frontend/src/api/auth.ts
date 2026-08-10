import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { apiFetch, ApiError, type ProblemDetail } from './client';
import { clearCredentialInvalid } from './credentialStatus';

export type SessionResponse = { accountId: string; email: string; absoluteExpiry: string };
export const sessionQueryKey = ['session'] as const;

async function sessionOrNull(): Promise<SessionResponse | null> {
  try {
    return await apiFetch<SessionResponse>('/api/auth/session');
  } catch (error) {
    if (error instanceof ApiError && error.problem.status === 401) return null;
    throw error;
  }
}

export function useSession() {
  return useQuery({
    queryKey: sessionQueryKey,
    queryFn: sessionOrNull,
    staleTime: 30_000,
  });
}

export async function fetchSessionOrNull(queryClient: QueryClient): Promise<SessionResponse | null> {
  return queryClient.ensureQueryData({ queryKey: sessionQueryKey, queryFn: sessionOrNull });
}

export function useLogin() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { email: string; apiToken: string }) =>
      apiFetch<SessionResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify(vars) }),
    onSuccess: (data) => {
      clearCredentialInvalid();
      queryClient.setQueryData(sessionQueryKey, data);
    },
  });
}

export function useLogout() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: () => apiFetch<void>('/api/auth/logout', { method: 'POST' }),
    onSuccess: () => {
      clearCredentialInvalid();
      queryClient.clear();
    },
  });
}

export function useReplaceJiraToken() {
  return useMutation({
    mutationFn: (apiToken: string) =>
      apiFetch<void>('/api/auth/credential', { method: 'PUT', body: JSON.stringify({ apiToken }) }),
    onSuccess: () => clearCredentialInvalid(),
  });
}

// The only place failure text is chosen - keyed on the machine-readable `reason`
// property and never on message matching (D-04). Later phases must extend this
// table rather than adding ad-hoc error text.
const REASON_MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'Your Jira credential is no longer valid. Enter a current Jira API token.',
  ACCOUNT_MISMATCH: 'That API token belongs to a different Jira account. Sign out and sign in as that account.',
  SITE_NOT_CONFIGURED: 'No Jira Cloud site is configured yet. Set one on the Settings screen first.',
  JIRA_UNREACHABLE: 'The configured Jira site could not be reached. Check the site URL and your network, then try again.',
  JIRA_RATE_LIMITED: 'Jira is rate-limiting requests. Wait a moment and try again.',
  JIRA_FORBIDDEN: 'That Jira account is not permitted to access the configured site.',
  JIRA_ERROR: 'Jira returned an unexpected error. Try again shortly.',
  RESULT_SET_TOO_LARGE: 'This query matched too many tickets to load. Narrow your fix version or JQL filter.',
  RELEASE_NOT_FOUND: "This release doesn't exist or has been deleted.",
  MANUAL_ITEM_NOT_FOUND: "This manual item doesn't exist or has already been removed.",
  RELEASE_FROZEN: 'This release is frozen and can no longer be changed.',
  RELEASE_DUPLICATE: 'A release with this name and version already exists. Choose a different name or version.',
  REFRESH_INCOMPLETE:
    'Jira did not return every ticket on this release, so nothing was refreshed. Remove the tickets that no longer exist in Jira, or check your Jira permissions, then try again.',
  SESSION_EXPIRED: 'Your session has ended. Sign in again.',
  INVALID_SESSION: 'Your session has ended. Sign in again.',
  UNAUTHENTICATED: 'Your session has ended. Sign in again.',
  FORBIDDEN: 'That request was rejected. Reload the page and try again.',
  TEMPLATE_NOT_FOUND: "This template doesn't exist or has been deleted.",
  TEMPLATE_DUPLICATE: 'A template with this name already exists for this document type. Choose a different name.',
  TEMPLATE_MALFORMED: 'Template not saved — check the placeholders and sections in this template.',
  RENDER_BOUND_EXCEEDED:
    'Template not saved — validation took too long or produced too much content to render safely. Simplify the template (fewer nested sections or shorter loops) and try again.',
  LOGO_INVALID_TYPE:
    "This file isn't a supported image. Upload a PNG or JPEG — SVG and other formats aren't accepted.",
  LOGO_TOO_LARGE: 'This image is too large.',
  LOGO_DIMENSIONS_EXCEEDED: 'This image is too large.',
  DOCUMENT_NOT_FOUND: "This document doesn't exist yet.",
  NO_ACTIVE_TEMPLATE: 'No active template exists for this document type. Activate one from Templates before generating.',
  EXPORT_FAILED: "The document couldn't be exported. Try again, and check the server logs if it persists.",
};

export function messageForReason(problem: ProblemDetail): string {
  if (problem.reason === 'VALIDATION_FAILED') {
    const fieldMessages = problem.fieldErrors ? Object.values(problem.fieldErrors) : [];
    return fieldMessages.length > 0 ? fieldMessages.join(' ') : 'Check the values you entered.';
  }
  return REASON_MESSAGES[problem.reason] ?? 'Something went wrong. Try again, and check the server logs if it persists.';
}
