import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { apiFetch, ApiError } from './client';
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
