import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch } from './client';

export type PromotionOutcome = 'SUCCESS' | 'FAILED' | 'ROLLED_BACK' | 'VOIDED';
export type PromotionStage = { id: string; name: string; position: number; production: boolean; retired: boolean };
export type PromotionEntry = {
  id: string;
  stageId: string;
  stageName: string;
  outcome: PromotionOutcome;
  deployedAt: string;
  recordedAt: string;
  recordedBy: string;
  note: string | null;
  supersedesId: string | null;
  supersededById: string | null;
  correctionReason: string | null;
  effective: boolean;
};
export type PromotionHistory = {
  stages: PromotionStage[];
  currentStageId: string | null;
  nextStageId: string | null;
  entries: PromotionEntry[];
};
export type RecordPromotionInput = {
  stageId: string;
  outcome: Exclude<PromotionOutcome, 'VOIDED'>;
  deployedAt?: string;
  note?: string;
};

export type CorrectPromotionInput = {
  promotionId: string;
  stageId: string;
  outcome: PromotionOutcome;
  deployedAt: string;
  note?: string;
  reason: string;
};

export const OUTCOME_LABELS: Record<PromotionOutcome, string> = {
  SUCCESS: 'Success',
  FAILED: 'Failed',
  ROLLED_BACK: 'Rolled back',
  VOIDED: 'Voided',
};

export const OUTCOME_COLOURS: Record<PromotionOutcome, string> = {
  SUCCESS: 'text-green-700',
  FAILED: 'text-red-700',
  ROLLED_BACK: 'text-amber-700',
  VOIDED: 'text-gray-500',
};

export const promotionsQueryKey = (releaseId: string) => ['promotions', releaseId] as const;

export function usePromotions(releaseId: string) {
  return useQuery({
    queryKey: promotionsQueryKey(releaseId),
    queryFn: () => apiFetch<PromotionHistory>(`/api/releases/${releaseId}/promotions`),
    retry: false,
  });
}

export function useCorrectPromotion(releaseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ promotionId, ...body }: CorrectPromotionInput) =>
      apiFetch<PromotionHistory>(`/api/releases/${releaseId}/promotions/${promotionId}/corrections`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    onSuccess: (history) => {
      queryClient.setQueryData(promotionsQueryKey(releaseId), history);
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}

export function useRecordPromotion(releaseId: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (input: RecordPromotionInput) =>
      apiFetch<PromotionHistory>(`/api/releases/${releaseId}/promotions`, {
        method: 'POST',
        body: JSON.stringify(input),
      }),
    onSuccess: (history) => {
      queryClient.setQueryData(promotionsQueryKey(releaseId), history);
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
    },
  });
}
