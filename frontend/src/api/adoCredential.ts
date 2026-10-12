import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client';

export type AdoCredentialState = 'OK' | 'EXPIRING' | 'EXPIRED';
export type AdoCredential = {
  configured: boolean;
  organization: string | null;
  expiresOn: string | null;
  state: AdoCredentialState | null;
  updatedAt: string | null;
  updatedBy: string | null;
};
export type UpdateAdoCredential = { organization: string; token?: string; expiresOn: string };
export const adoCredentialQueryKey = ['ado-credential'] as const;

export function useAdoCredential() {
  return useQuery({
    queryKey: adoCredentialQueryKey,
    queryFn: () => apiFetch<AdoCredential>('/api/settings/azure-devops'),
  });
}

export function useUpdateAdoCredential() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ organization, token, expiresOn }: UpdateAdoCredential) =>
      apiFetch<AdoCredential>('/api/settings/azure-devops', {
        method: 'PUT',
        body: JSON.stringify({ organization, expiresOn, ...(token ? { token } : {}) }),
      }),
    onSuccess: (credential) => queryClient.setQueryData(adoCredentialQueryKey, credential),
  });
}
