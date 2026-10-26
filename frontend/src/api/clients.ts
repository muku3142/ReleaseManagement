import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client';
import type { InstallOutcome } from './installs';

export type Client = { id: string; name: string; archived: boolean; productIds: string[] };
export type ClientList = { clients: Client[] };

export type ClientRun = {
  productId: string;
  productName: string;
  productArchived: boolean;
  hasInstalls: boolean;
  current: {
    releaseId: string;
    releaseName: string;
    releaseVersion: string;
    installedAt: string;
    inProduction: boolean;
  } | null;
};

export type ClientInstallEntry = {
  id: string;
  productId: string;
  productName: string;
  releaseId: string;
  releaseName: string;
  releaseVersion: string;
  inProduction: boolean;
  outcome: InstallOutcome;
  installedAt: string;
  recordedAt: string;
  recordedBy: string;
  note: string | null;
  supersedesId: string | null;
  supersededById: string | null;
  correctionReason: string | null;
  effective: boolean;
};

export type ClientDetail = {
  id: string;
  name: string;
  archived: boolean;
  runs: ClientRun[];
  history: ClientInstallEntry[];
};

export const clientsQueryKey = ['clients'] as const;
export const clientQueryKey = (clientId: string) => ['client', clientId] as const;

const CLIENTS_URL = '/api/clients';

export function useClients() {
  return useQuery({
    queryKey: clientsQueryKey,
    queryFn: () => apiFetch<ClientList>(CLIENTS_URL),
  });
}

export function useClient(clientId: string | undefined) {
  return useQuery({
    queryKey: clientQueryKey(clientId ?? ''),
    queryFn: () => apiFetch<ClientDetail>(`${CLIENTS_URL}/${clientId}`),
    enabled: !!clientId,
    retry: false,
  });
}

function useClientMutation<TVars>(request: (vars: TVars) => Promise<ClientList>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (list) => {
      queryClient.setQueryData(clientsQueryKey, list);
      for (const key of ['client', 'products', 'installs', 'dashboard']) {
        queryClient.invalidateQueries({ queryKey: [key] });
      }
    },
  });
}

function send(path: string, method: string, body?: unknown) {
  return apiFetch<ClientList>(`${CLIENTS_URL}${path}`, {
    method,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
}

export function useAddClient() {
  return useClientMutation((name: string) => send('', 'POST', { name }));
}

export function useRenameClient() {
  return useClientMutation(({ id, name }: { id: string; name: string }) => send(`/${id}`, 'PUT', { name }));
}

export function useArchiveClient() {
  return useClientMutation((id: string) => send(`/${id}/archive`, 'POST'));
}

export function useUnarchiveClient() {
  return useClientMutation((id: string) => send(`/${id}/unarchive`, 'POST'));
}

function useLinkMutation(clientId: string, method: 'PUT' | 'DELETE') {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (productId: string) =>
      apiFetch<ClientDetail>(`${CLIENTS_URL}/${clientId}/products/${productId}`, { method }),
    onSuccess: (detail) => {
      queryClient.setQueryData(clientQueryKey(clientId), detail);
      queryClient.invalidateQueries({ queryKey: clientsQueryKey });
      queryClient.invalidateQueries({ queryKey: ['products'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useLinkProduct(clientId: string) {
  return useLinkMutation(clientId, 'PUT');
}

export function useUnlinkProduct(clientId: string) {
  return useLinkMutation(clientId, 'DELETE');
}
