import { useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { OUTCOME_COLOURS, OUTCOME_LABELS, usePromotions, type PromotionEntry } from '../api/promotions';
import { LadderDots } from './LadderDots';
import { RecordPromotionDialog } from './RecordPromotionDialog';

export function PromotionSection({ releaseId, isFrozen }: { releaseId: string; isFrozen: boolean }) {
  const promotions = usePromotions(releaseId);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [correcting, setCorrecting] = useState<PromotionEntry>();

  const history = promotions.data;
  const problem =
    promotions.isError && promotions.error instanceof ApiError ? promotions.error.problem : undefined;
  const current = history?.stages.find((s) => s.id === history.currentStageId);

  return (
    <section id="history" className="mt-6 rounded border border-gray-200 bg-white p-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-900">Promotion history</h3>
        <a href="#documents" className="text-sm text-blue-600 hover:underline">
          Document revisions
        </a>
      </div>

      {promotions.isPending && <p className="mt-3 text-sm text-gray-600">Loading…</p>}

      {problem && (
        <p role="alert" data-reason={problem.reason} className="mt-3 text-sm text-red-600">
          {messageForReason(problem)}
        </p>
      )}

      {history && (
        <>
          <LadderDots stages={history.stages} currentStageId={history.currentStageId} />

          <p className="mt-2 text-sm text-gray-700">
            {current ? `Current stage: ${current.name}` : 'Not deployed yet'}
          </p>

          {isFrozen ? (
            <>
              <button
                type="button"
                onClick={() => setDialogOpen(true)}
                className="mt-3 rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
              >
                Record promotion
              </button>
              <RecordPromotionDialog
                open={dialogOpen || correcting !== undefined}
                releaseId={releaseId}
                history={history}
                correcting={correcting}
                onClose={() => {
                  setDialogOpen(false);
                  setCorrecting(undefined);
                }}
              />
            </>
          ) : (
            <p className="mt-3 text-sm text-gray-600">Freeze this release to record promotions.</p>
          )}

          {history.entries.length === 0 ? (
            <p className="mt-4 text-sm text-gray-600">No promotions recorded yet.</p>
          ) : (
            <ul className="mt-4 divide-y divide-gray-100">
              {history.entries.map((entry) => (
                <li key={entry.id} className={`py-2 text-sm ${entry.effective ? '' : 'text-gray-400'}`}>
                  <span className={entry.effective ? 'font-semibold text-gray-900' : 'font-semibold'}>
                    {entry.stageName}
                  </span>{' '}
                  <span className={entry.effective ? OUTCOME_COLOURS[entry.outcome] : ''}>
                    {OUTCOME_LABELS[entry.outcome]}
                  </span>
                  {!entry.effective && <span className="ml-2 text-xs">Superseded</span>}
                  <div className={`text-xs ${entry.effective ? 'text-gray-600' : ''}`}>
                    Deployed {new Date(entry.deployedAt).toLocaleString()} · Recorded{' '}
                    {new Date(entry.recordedAt).toLocaleString()} by {entry.recordedBy}
                  </div>
                  {entry.note && <p className={`mt-1 ${entry.effective ? 'text-gray-700' : ''}`}>{entry.note}</p>}
                  {entry.correctionReason && (
                    <p className="mt-1 text-xs">Correction: {entry.correctionReason}</p>
                  )}
                  {isFrozen && entry.effective && (
                    <button
                      type="button"
                      onClick={() => setCorrecting(entry)}
                      className="mt-1 rounded border border-gray-300 px-2 py-1 text-xs text-gray-700"
                    >
                      Correct
                    </button>
                  )}
                </li>
              ))}
            </ul>
          )}
        </>
      )}
    </section>
  );
}
