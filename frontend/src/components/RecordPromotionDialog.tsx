import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import {
  OUTCOME_LABELS,
  useCorrectPromotion,
  useRecordPromotion,
  type PromotionEntry,
  type PromotionHistory,
  type PromotionOutcome,
  type PromotionStage,
  type RecordPromotionInput,
} from '../api/promotions';

type RecordableOutcome = RecordPromotionInput['outcome'];
const RECORDABLE_OUTCOMES: PromotionOutcome[] = ['SUCCESS', 'FAILED', 'ROLLED_BACK'];
const CORRECTABLE_OUTCOMES: PromotionOutcome[] = [...RECORDABLE_OUTCOMES, 'VOIDED'];

function defaultStageId(history: PromotionHistory, outcome: PromotionOutcome): string {
  const id =
    outcome === 'ROLLED_BACK' ? history.currentStageId : (history.nextStageId ?? history.currentStageId);
  return id ?? '';
}

export type StageOption = { id: string; label: string; disabled: boolean };

const MAX_LISTED_SKIPS = 3;

function ladder(history: PromotionHistory): PromotionStage[] {
  return [...history.stages].sort((a, b) => a.position - b.position);
}

// D-15.1-03: the current stage (redeploy) or any later active stage; earlier stages are not offered (ROLLED_BACK goes back).
export function recordStageOptions(history: PromotionHistory): StageOption[] {
  const stages = ladder(history);
  const current = stages.find((s) => s.id === history.currentStageId);
  return stages.flatMap((stage): StageOption[] => {
    if (stage.id === current?.id) {
      return [
        stage.retired
          ? { id: stage.id, label: `${stage.name} (retired)`, disabled: true }
          : { id: stage.id, label: `${stage.name} (redeploy)`, disabled: false },
      ];
    }
    const later = current === undefined || stage.position > current.position;
    return later && !stage.retired ? [{ id: stage.id, label: stage.name, disabled: false }] : [];
  });
}

// Active stages strictly between the current stage (or the start of the ladder) and the selected one.
export function skippedStages(history: PromotionHistory, selectedId: string): PromotionStage[] {
  const stages = ladder(history);
  const selected = stages.find((s) => s.id === selectedId);
  if (!selected) return [];
  const current = stages.find((s) => s.id === history.currentStageId);
  return stages.filter(
    (s) => !s.retired && (current === undefined || s.position > current.position) && s.position < selected.position,
  );
}

export function skipNote(names: string[]): string {
  const rest = names.length - MAX_LISTED_SKIPS;
  const list = names.slice(0, MAX_LISTED_SKIPS).join(', ') + (rest > 0 ? ` and ${rest} more` : '');
  return `This skips ${names.length} stage(s): ${list}.`;
}

function recordDefaultStageId(history: PromotionHistory, outcome: PromotionOutcome): string {
  const enabled = recordStageOptions(history).filter((o) => !o.disabled);
  const preferred = defaultStageId(history, outcome);
  return enabled.find((o) => o.id === preferred)?.id ?? enabled[0]?.id ?? '';
}

const pad = (n: number) => String(n).padStart(2, '0');

export function toLocalInput(iso: string): string {
  const d = new Date(iso);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

export function RecordPromotionDialog(props: {
  open: boolean;
  releaseId: string;
  history: PromotionHistory;
  correcting?: PromotionEntry;
  onClose: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const record = useRecordPromotion(props.releaseId);
  const correct = useCorrectPromotion(props.releaseId);
  const { correcting } = props;

  const [outcome, setOutcome] = useState<PromotionOutcome>('SUCCESS');
  const [stageId, setStageId] = useState('');
  const [deployedAt, setDeployedAt] = useState('');
  const [deployedAtEdited, setDeployedAtEdited] = useState(false);
  const [note, setNote] = useState('');
  const [reason, setReason] = useState('');

  // Permanently mounted, so reopening must not carry over the previous attempt's values or error.
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (props.open && !dialog.open) {
      setOutcome(correcting?.outcome ?? 'SUCCESS');
      setStageId(correcting?.stageId ?? recordDefaultStageId(props.history, 'SUCCESS'));
      setDeployedAt(correcting ? toLocalInput(correcting.deployedAt) : '');
      setDeployedAtEdited(false);
      setNote(correcting?.note ?? '');
      setReason('');
      record.reset();
      correct.reset();
      dialog.showModal();
    }
    if (!props.open && dialog.open) dialog.close();
  }, [props.open]);

  function changeOutcome(next: PromotionOutcome) {
    setOutcome(next);
    if (!correcting) setStageId(recordDefaultStageId(props.history, next));
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const trimmedNote = note.trim();
    if (correcting) {
      correct.mutate(
        {
          promotionId: correcting.id,
          stageId,
          outcome,
          // Untouched keeps the original instant verbatim: the local input drops milliseconds and zone.
          deployedAt: deployedAtEdited ? new Date(deployedAt).toISOString() : correcting.deployedAt,
          ...(trimmedNote !== '' && { note: trimmedNote }),
          reason: reason.trim(),
        },
        { onSuccess: props.onClose },
      );
      return;
    }
    record.mutate(
      {
        stageId,
        outcome: outcome as RecordableOutcome,
        ...(deployedAt !== '' && { deployedAt: new Date(deployedAt).toISOString() }),
        ...(trimmedNote !== '' && { note: trimmedNote }),
      },
      { onSuccess: props.onClose },
    );
  }

  const active = correcting ? correct : record;
  const stageOptions = correcting ? [] : recordStageOptions(props.history);
  const hasCurrentStage = props.history.currentStageId !== null;
  const currentStageName = props.history.stages.find((st) => st.id === props.history.currentStageId)?.name;
  const skipped = correcting ? [] : skippedStages(props.history, stageId);
  const problem = active.isError && active.error instanceof ApiError ? active.error.problem : undefined;

  return (
    <dialog ref={dialogRef} onClose={props.onClose} className="rounded border border-gray-200 bg-white p-6">
      <h2 className="text-base font-semibold text-gray-900">{correcting ? 'Correct entry' : 'Record promotion'}</h2>
      <form onSubmit={handleSubmit} className="mt-4 space-y-3">
        <div>
          <label htmlFor="promotionOutcome" className="block text-sm font-semibold text-gray-700">
            Outcome
          </label>
          <select
            id="promotionOutcome"
            value={outcome}
            onChange={(event) => changeOutcome(event.target.value as PromotionOutcome)}
            className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
          >
            {(correcting ? CORRECTABLE_OUTCOMES : RECORDABLE_OUTCOMES).map((o) => (
              <option key={o} value={o}>
                {OUTCOME_LABELS[o]}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="promotionStage" className="block text-sm font-semibold text-gray-700">
            Target stage
          </label>
          <select
            id="promotionStage"
            value={stageId}
            onChange={(event) => setStageId(event.target.value)}
            aria-describedby={!correcting && hasCurrentStage ? 'promotionStageHelp' : undefined}
            className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
          >
            {correcting
              ? props.history.stages.map((stage) => {
                  const retired = stage.retired && stage.id !== correcting?.stageId;
                  return (
                    <option key={stage.id} value={stage.id} disabled={retired}>
                      {stage.retired ? `${stage.name} (retired)` : stage.name}
                    </option>
                  );
                })
              : stageOptions.map((o) => (
                  <option key={o.id} value={o.id} disabled={o.disabled}>
                    {o.label}
                  </option>
                ))}
          </select>
          {!correcting && hasCurrentStage && (
            <p id="promotionStageHelp" className="mt-1 text-xs text-gray-600">
              Choose the next stage or skip ahead. Pick {currentStageName} to record a redeploy. Earlier stages aren't
              offered; record a rollback instead.
            </p>
          )}
          {skipped.length > 0 && (
            <p role="status" className="mt-1 text-xs text-gray-600">
              {skipNote(skipped.map((st) => st.name))}
            </p>
          )}
          {!correcting && stageOptions.every((o) => o.disabled) && (
            <p className="mt-1 text-sm text-red-600">
              No active stage is available to promote to. Add or restore a stage in Settings.
            </p>
          )}
        </div>
        <div>
          <label htmlFor="promotionDeployedAt" className="block text-sm font-semibold text-gray-700">
            {correcting ? 'Deployed at' : 'Deployed at (leave empty for now)'}
          </label>
          <input
            id="promotionDeployedAt"
            type="datetime-local"
            step={correcting ? 1 : undefined}
            value={deployedAt}
            onChange={(event) => {
              setDeployedAt(event.target.value);
              setDeployedAtEdited(true);
            }}
            className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
          />
        </div>
        <div>
          <label htmlFor="promotionNote" className="block text-sm font-semibold text-gray-700">
            Note (optional)
          </label>
          <textarea
            id="promotionNote"
            maxLength={2000}
            value={note}
            onChange={(event) => setNote(event.target.value)}
            className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
          />
        </div>
        {correcting && (
          <div>
            <label htmlFor="promotionReason" className="block text-sm font-semibold text-gray-700">
              Reason for correction
            </label>
            <textarea
              id="promotionReason"
              required
              maxLength={2000}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
            />
          </div>
        )}

        {problem && (
          <p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
            {messageForReason(problem)}
          </p>
        )}

        <div className="flex justify-end gap-3">
          <button
            type="button"
            onClick={props.onClose}
            className="rounded border border-gray-300 px-4 py-2 text-sm text-gray-700"
          >
            Cancel
          </button>
          <button
            type="submit"
            disabled={active.isPending || stageId === '' || (correcting !== undefined && (reason.trim() === '' || deployedAt === ''))}
            className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {correcting ? 'Save correction' : 'Record'}
          </button>
        </div>
      </form>
    </dialog>
  );
}
