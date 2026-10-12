import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client';

export type LadderStage = {
  id: string;
  name: string;
  position: number;
  production: boolean;
  retired: boolean;
  inUse: boolean;
};
export type EnvironmentLadder = { stages: LadderStage[] };
export const ladderQueryKey = ['environment-ladder'] as const;

const LADDER_URL = '/api/environment-ladder';

export function useEnvironmentLadder() {
  return useQuery({
    queryKey: ladderQueryKey,
    queryFn: () => apiFetch<EnvironmentLadder>(LADDER_URL),
  });
}

function useLadderMutation<TVars>(request: (vars: TVars) => Promise<EnvironmentLadder>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (ladder) => {
      queryClient.setQueryData(ladderQueryKey, ladder);
      // A rename, reorder or retire relabels every release's history and ladder dots (D-07-03).
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      return queryClient.invalidateQueries({ queryKey: ['promotions'] });
    },
  });
}

function send(path: string, method: string, body?: unknown) {
  return apiFetch<EnvironmentLadder>(`${LADDER_URL}${path}`, {
    method,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
}

export function useAddStage() {
  return useLadderMutation((name: string) => send('/stages', 'POST', { name }));
}

export function useRenameStage() {
  return useLadderMutation(({ id, name }: { id: string; name: string }) => send(`/stages/${id}`, 'PUT', { name }));
}

export function useReorderStages() {
  return useLadderMutation((stageIds: string[]) => send('/order', 'PUT', { stageIds }));
}

export function useRetireStage() {
  return useLadderMutation((id: string) => send(`/stages/${id}/retire`, 'POST'));
}

export function useRestoreStage() {
  return useLadderMutation((id: string) => send(`/stages/${id}/restore`, 'POST'));
}

export function useMoveProductionStage() {
  return useLadderMutation((stageId: string) => send('/production-stage', 'PUT', { stageId }));
}

export function useDeleteStage() {
  return useLadderMutation((id: string) => send(`/stages/${id}`, 'DELETE'));
}
