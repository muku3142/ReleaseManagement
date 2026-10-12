import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/auth';
import {
  OUTCOME_LABELS,
  useRecordPromotion,
  type PromotionHistory,
  type RecordPromotionInput,
} from '../api/promotions';

type RecordableOutcome = RecordPromotionInput['outcome'];
const RECORDABLE_OUTCOMES: RecordableOutcome[] = ['SUCCESS', 'FAILED', 'ROLLED_BACK'];

function defaultStageId(history: PromotionHistory, outcome: RecordableOutcome): string {
  const id =
    outcome === 'ROLLED_BACK' ? history.currentStageId : (history.nextStageId ?? history.currentStageId);
  return id ?? '';
}

export function RecordPromotionDialog(props: {
  open: boolean;
  releaseId: string;
  history: PromotionHistory;
  onClose: () => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const record = useRecordPromotion(props.releaseId);

  const [outcome, setOutcome] = useState<RecordableOutcome>('SUCCESS');
  const [stageId, setStageId] = useState('');
  const [deployedAt, setDeployedAt] = useState('');
  const [note, setNote] = useState('');

  // Permanently mounted, so reopening must not carry over the previous attempt's values or error.
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (props.open && !dialog.open) {
      setOutcome('SUCCESS');
      setStageId(defaultStageId(props.history, 'SUCCESS'));
      setDeployedAt('');
      setNote('');
      record.reset();
      dialog.showModal();
    }
    if (!props.open && dialog.open) dialog.close();
  }, [props.open]);

  function changeOutcome(next: RecordableOutcome) {
    setOutcome(next);
    setStageId(defaultStageId(props.history, next));
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const trimmedNote = note.trim();
    record.mutate(
      {
        stageId,
        outcome,
        ...(deployedAt !== '' && { deployedAt: new Date(deployedAt).toISOString() }),
        ...(trimmedNote !== '' && { note: trimmedNote }),
      },
      { onSuccess: props.onClose },
    );
  }

  const problem = record.isError && record.error instanceof ApiError ? record.error.problem : undefined;

  return (
    <dialog ref={dialogRef} onClose={props.onClose} className="rounded border border-gray-200 bg-white p-6">
      <h2 className="text-base font-semibold text-gray-900">Record promotion</h2>
      <form onSubmit={handleSubmit} className="mt-4 space-y-3">
        <div>
          <label htmlFor="promotionOutcome" className="block text-sm font-semibold text-gray-700">
            Outcome
          </label>
          <select
            id="promotionOutcome"
            value={outcome}
            onChange={(event) => changeOutcome(event.target.value as RecordableOutcome)}
            className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
          >
            {RECORDABLE_OUTCOMES.map((o) => (
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
            {props.history.stages.map((stage) => (
              <option key={stage.id} value={stage.id} disabled={stage.retired}>
                {stage.retired ? `${stage.name} (retired)` : stage.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="promotionDeployedAt" className="block text-sm font-semibold text-gray-700">
            Deployed at (leave empty for now)
          </label>
          <input
            id="promotionDeployedAt"
            type="datetime-local"
            value={deployedAt}
            onChange={(event) => setDeployedAt(event.target.value)}
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
            disabled={record.isPending || stageId === ''}
            className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            Record
          </button>
        </div>
      </form>
    </dialog>
  );
}
