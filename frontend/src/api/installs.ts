import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, apiFetch, type ProblemDetail } from './client';
import type { PromotionOutcome } from './promotions';

// Install outcomes share the promotion outcome values, labels and colours (OUTCOME_LABELS/OUTCOME_COLOURS in promotions.ts).
export type InstallOutcome = PromotionOutcome;

export type InstallEntry = {
  id: string;
  productId: string;
  productName: string;
  clientId: string;
  clientName: string;
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

export type InstallHistory = {
  releaseId: string;
  frozen: boolean;
  inProduction: boolean;
  products: { id: string; name: string; archived: boolean }[];
  entries: InstallEntry[];
};

export type RecordInstallInput = {
  productId: string;
  clientIds: string[];
  outcome: Exclude<InstallOutcome, 'VOIDED'>;
  installedAt: string | null;
  note: string | null;
};

export type CorrectInstallInput = {
  installId: string;
  outcome: InstallOutcome;
  installedAt: string;
  note: string | null;
  reason: string;
};

export const installsQueryKey = (releaseId: string) => ['installs', releaseId] as const;

export function useInstalls(releaseId: string) {
  return useQuery({
    queryKey: installsQueryKey(releaseId),
    queryFn: () => apiFetch<InstallHistory>(`/api/releases/${releaseId}/installs`),
    retry: false,
  });
}

function useInstallMutation<TVars>(releaseId: string, request: (vars: TVars) => Promise<InstallHistory>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (history) => {
      queryClient.setQueryData(installsQueryKey(releaseId), history);
      queryClient.invalidateQueries({ queryKey: ['client'] });
      queryClient.invalidateQueries({ queryKey: ['clients'] });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useRecordInstall(releaseId: string) {
  return useInstallMutation(releaseId, (input: RecordInstallInput) =>
    apiFetch<InstallHistory>(`/api/releases/${releaseId}/installs`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),
  );
}

export function useCorrectInstall(releaseId: string) {
  return useInstallMutation(releaseId, ({ installId, ...body }: CorrectInstallInput) =>
    apiFetch<InstallHistory>(`/api/releases/${releaseId}/installs/${installId}/corrections`, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
  );
}

// D-15-10: a bulk rejection names the offending clients so the form can list them.
export function rejectedClients(error: unknown): NonNullable<ProblemDetail['clients']> {
  if (error instanceof ApiError && error.problem.reason === 'INSTALL_CLIENTS_REJECTED') {
    return error.problem.clients ?? [];
  }
  return [];
}
