import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/auth';
import {
  OUTCOME_LABELS,
  useCorrectPromotion,
  useRecordPromotion,
  type PromotionEntry,
  type PromotionHistory,
  type PromotionOutcome,
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

const pad = (n: number) => String(n).padStart(2, '0');

function toLocalInput(iso: string): string {
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
      setStageId(correcting?.stageId ?? defaultStageId(props.history, 'SUCCESS'));
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
    if (!correcting) setStageId(defaultStageId(props.history, next));
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
            className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
          >
            {props.history.stages.map((stage) => {
              const retired = stage.retired && stage.id !== correcting?.stageId;
              return (
                <option key={stage.id} value={stage.id} disabled={retired}>
                  {stage.retired ? `${stage.name} (retired)` : stage.name}
                </option>
              );
            })}
          </select>
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
