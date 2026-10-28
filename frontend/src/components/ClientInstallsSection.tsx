import { useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useCorrectInstall, useInstalls, type InstallEntry } from '../api/installs';
import { OUTCOME_COLOURS, OUTCOME_LABELS } from '../api/promotions';
import { SMALL_BUTTON } from './EnvironmentLadderSection';
import { ConfirmDialog } from './ConfirmDialog';
import { RecordInstallDialog } from './RecordInstallDialog';

function groupByProduct(entries: InstallEntry[]): { productId: string; productName: string; entries: InstallEntry[] }[] {
  const groups: { productId: string; productName: string; entries: InstallEntry[] }[] = [];
  for (const entry of entries) {
    const group = groups.find((g) => g.productId === entry.productId);
    if (group) group.entries.push(entry);
    else groups.push({ productId: entry.productId, productName: entry.productName, entries: [entry] });
  }
  return groups;
}

export function ClientInstallsSection({ releaseId, isFrozen }: { releaseId: string; isFrozen: boolean }) {
  const installs = useInstalls(releaseId);
  const voidInstall = useCorrectInstall(releaseId);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [correcting, setCorrecting] = useState<InstallEntry>();
  const [voiding, setVoiding] = useState<InstallEntry>();
  const [voidReason, setVoidReason] = useState('');
  const [announcement, setAnnouncement] = useState('');
  const opener = useRef<HTMLElement | null>(null);

  const history = installs.data;
  const problem = installs.isError && installs.error instanceof ApiError ? installs.error.problem : undefined;
  const voidProblem =
    voidInstall.isError && voidInstall.error instanceof ApiError ? voidInstall.error.problem : undefined;

  function remember() {
    opener.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  }

  function closeDialog() {
    setDialogOpen(false);
    setCorrecting(undefined);
    opener.current?.focus();
  }

  function startVoid(entry: InstallEntry) {
    remember();
    voidInstall.reset();
    setVoidReason('');
    setVoiding(entry);
  }

  function cancelVoid() {
    setVoiding(undefined);
    opener.current?.focus();
  }

  function confirmVoid() {
    if (!voiding) return;
    voidInstall.mutate(
      {
        installId: voiding.id,
        outcome: 'VOIDED',
        installedAt: voiding.installedAt,
        note: voiding.note,
        reason: voidReason.trim(),
      },
      {
        onSuccess: () => {
          setVoiding(undefined);
          setAnnouncement('Install voided.');
          opener.current?.focus();
        },
      },
    );
  }

  function row(entry: InstallEntry, showProduct: boolean) {
    const voided = entry.outcome === 'VOIDED';
    return (
      <li key={entry.id} className={`py-2 text-sm ${entry.effective ? '' : 'text-gray-400'}`}>
        <span
          className={`font-semibold ${entry.effective ? 'text-gray-900' : 'line-through'}`}
        >
          {entry.clientName}
        </span>{' '}
        {showProduct && <span className="text-xs">{entry.productName} </span>}
        {entry.effective && (
          <span className={voided ? 'text-gray-500' : OUTCOME_COLOURS[entry.outcome]}>
            {voided ? 'Voided' : OUTCOME_LABELS[entry.outcome]}
          </span>
        )}
        {!entry.effective && <span className="ml-2 text-xs">Superseded</span>}
        <div className={`text-xs ${entry.effective ? 'text-gray-600' : ''}`}>
          Installed {new Date(entry.installedAt).toLocaleString()} · Recorded{' '}
          {new Date(entry.recordedAt).toLocaleString()} by {entry.recordedBy}
          {history && !history.inProduction && <span className="text-amber-700"> · not in Production</span>}
        </div>
        {entry.note && <p className={`mt-1 ${entry.effective ? 'text-gray-700' : ''}`}>{entry.note}</p>}
        {entry.correctionReason && <p className="mt-1 text-xs">Correction: {entry.correctionReason}</p>}
        {isFrozen && entry.effective && !voided && (
          <div className="mt-1 flex gap-2">
            <button
              type="button"
              aria-label={`Correct install for ${entry.clientName}`}
              className={SMALL_BUTTON}
              onClick={() => {
                remember();
                setCorrecting(entry);
              }}
            >
              Correct
            </button>
            <button
              type="button"
              aria-label={`Void install for ${entry.clientName}`}
              className={SMALL_BUTTON}
              onClick={() => startVoid(entry)}
            >
              Void
            </button>
          </div>
        )}
      </li>
    );
  }

  const groups = history ? groupByProduct(history.entries) : [];
  const grouped = groups.length > 1;

  return (
    <section id="installs" className="mt-6 rounded border border-gray-200 bg-white p-4">
      <div className="flex items-center justify-between">
        <h3 className="text-base font-semibold text-gray-900">Client installs</h3>
        <a href="#history" className="text-sm text-blue-600 hover:underline">
          Promotion history
        </a>
      </div>
      <p role="status" aria-live="polite" className="sr-only">
        {announcement}
      </p>

      {installs.isPending && <p className="mt-3 text-sm text-gray-600">Loading…</p>}

      {problem && (
        <p role="alert" data-reason={problem.reason} className="mt-3 text-sm text-red-600">
          {messageForReason(problem)}
        </p>
      )}

      {history && !isFrozen && (
        <p className="mt-3 text-sm text-gray-600">Freeze this release to record client installs.</p>
      )}

      {history && isFrozen && history.products.length === 0 && (
        <p className="mt-3 text-sm text-gray-600">
          This release is Unassigned. Add the products it covers (Edit products above) before recording installs.
        </p>
      )}

      {history && isFrozen && history.products.length > 0 && (
        <>
          {!history.inProduction && (
            <div className="mt-3 rounded border border-amber-600 bg-amber-50 p-3 text-sm text-amber-700">
              <p className="font-semibold">Not in Production yet.</p>
              <p>
                This release hasn't been promoted to Production. You can still record installs; they will be marked
                'not in Production'.
              </p>
            </div>
          )}
          <button
            type="button"
            onClick={() => {
              remember();
              setDialogOpen(true);
            }}
            className="mt-3 rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white"
          >
            Record install
          </button>
          <RecordInstallDialog
            open={dialogOpen || correcting !== undefined}
            releaseId={releaseId}
            history={history}
            correcting={correcting}
            onClose={closeDialog}
            onDone={(message) => {
              setAnnouncement(message);
              closeDialog();
            }}
          />

          {voidProblem && (
            <p role="alert" data-reason={voidProblem.reason} className="mt-3 text-sm text-red-600">
              {messageForReason(voidProblem)}
            </p>
          )}

          {history.entries.length === 0 ? (
            <p className="mt-4 text-sm text-gray-600">No client installs recorded yet.</p>
          ) : grouped ? (
            groups.map((group) => (
              <div key={group.productId} className="mt-4">
                <p className="text-xs font-semibold text-gray-600">{group.productName}</p>
                <ul className="divide-y divide-gray-100">{group.entries.map((e) => row(e, false))}</ul>
              </div>
            ))
          ) : (
            <ul className="mt-4 divide-y divide-gray-100">{history.entries.map((e) => row(e, true))}</ul>
          )}

          <ConfirmDialog
            open={voiding !== undefined}
            title="Void install"
            body={`Void the install of ${voiding?.productName ?? ''} for ${voiding?.clientName ?? ''}? The original stays visible as superseded. This can't be undone, but you can record a new install afterwards.`}
            confirmLabel="Void install"
            confirmClassName="rounded bg-red-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
            pending={voidInstall.isPending}
            confirmDisabled={voidReason.trim() === ''}
            onConfirm={confirmVoid}
            onCancel={cancelVoid}
          >
            <textarea
              aria-label="Reason for voiding"
              required
              maxLength={2000}
              value={voidReason}
              onChange={(event) => setVoidReason(event.target.value)}
              className="mt-3 w-full rounded border border-gray-300 px-3 py-2 text-sm"
            />
          </ConfirmDialog>
        </>
      )}
    </section>
  );
}
