import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { streamSse } from './assistant';
import { ApiError, apiFetch } from './client';
import { documentQueryKey, documentRevisionsQueryKey, readinessQueryKey, type AiMode } from './documents';
import type { DocumentType } from './templates';

export type TransformMode = 'CLEAN_UP' | 'SUMMARIZE' | 'CUSTOM';

export type TransformLimits = {
  numCtx: number;
  charsPerToken: number;
  systemTokens: number;
  templateSlack: number;
  instructionMaxChars: number;
  presets: Record<'CLEAN_UP' | 'SUMMARIZE', Record<DocumentType, number>>;
};

export type TransformErrorOutcome = 'OFFLINE' | 'BUSY' | 'MODEL_MISSING' | 'DEADLINE' | 'FAILED' | 'TOO_LARGE';

export type TransformRequestBody = {
  releaseId: string;
  documentType: DocumentType;
  sourceRevision: number;
  mode: TransformMode;
  instruction: string | null;
};

export type TransformEvent =
  | { event: 'token'; data: { text: string } }
  // Clean up and Custom run section by section; sent before each section's model call.
  | { event: 'progress'; data: { section: number; sections: number } }
  // Sent when a sectioned run stops after a finished section: keep the first `keep` characters, then the original rest.
  | { event: 'remainder'; data: { keep: number; text: string } }
  | { event: 'done'; data: { finishReason: string | null; truncated: boolean; complete: boolean } }
  | {
      event: 'error';
      data: {
        outcome: TransformErrorOutcome;
        retryAfterSeconds: number | null;
        characters: number | null;
        maxCharacters: number | null;
      };
    };

export type SaveAiRevisionBody = {
  releaseId: string;
  documentType: DocumentType;
  sourceRevision: number;
  mode: AiMode;
  instruction: string | null;
  markdown: string;
};

const KNOWN_EVENTS = new Set(['token', 'progress', 'remainder', 'done', 'error']);

export function useTransformLimits(enabled: boolean) {
  return useQuery({
    queryKey: ['assistant', 'transform-limits'],
    queryFn: () => apiFetch<TransformLimits>('/api/assistant/transform/limits'),
    staleTime: 60 * 60_000,
    enabled,
  });
}

export function streamTransform(
  body: TransformRequestBody,
  signal: AbortSignal,
  onEvent: (event: TransformEvent) => void,
): Promise<void> {
  return streamSse('/api/assistant/transform', body, signal, (parsed) => {
    if (KNOWN_EVENTS.has(parsed.event)) onEvent(parsed as TransformEvent);
  });
}

export function tooLargeFrom(error: unknown): { characters: number; maxCharacters: number } | null {
  if (!(error instanceof ApiError) || error.problem.status !== 413) return null;
  const { characters, maxCharacters } = error.problem as unknown as { characters?: number; maxCharacters?: number };
  return typeof characters === 'number' && typeof maxCharacters === 'number' ? { characters, maxCharacters } : null;
}

// Saving an AI revision changes the same document state as generating one, so it invalidates the same queries.
export function useSaveAiRevision() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (body: SaveAiRevisionBody) =>
      apiFetch<{ revision: number }>('/api/assistant/documents/ai-revisions', {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    onSuccess: (_saved, vars) => {
      queryClient.invalidateQueries({ queryKey: ['releases'] });
      queryClient.invalidateQueries({ queryKey: readinessQueryKey() });
      queryClient.invalidateQueries({ queryKey: ['dashboard'] });
      queryClient.invalidateQueries({ queryKey: documentQueryKey(vars.releaseId, vars.documentType) });
      queryClient.invalidateQueries({ queryKey: documentRevisionsQueryKey(vars.releaseId, vars.documentType) });
      queryClient.invalidateQueries({ queryKey: ['assistant', 'freshness'] });
    },
  });
}
