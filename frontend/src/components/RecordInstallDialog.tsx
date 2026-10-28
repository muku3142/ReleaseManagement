import { useEffect, useRef, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useClients } from '../api/clients';
import {
  rejectedClients,
  useCorrectInstall,
  useRecordInstall,
  type InstallEntry,
  type InstallHistory,
  type InstallOutcome,
} from '../api/installs';
import { OUTCOME_LABELS } from '../api/promotions';
import { SMALL_BUTTON } from './EnvironmentLadderSection';
import { toLocalInput } from './RecordPromotionDialog';

type RecordableOutcome = Exclude<InstallOutcome, 'VOIDED'>;
const RECORDABLE_OUTCOMES: RecordableOutcome[] = ['SUCCESS', 'FAILED', 'ROLLED_BACK'];
const MAX_NAMED_CLIENTS = 5;
const FIELD = 'w-full rounded border border-gray-300 px-3 py-2 text-sm';
const REJECTION_SUFFIX = { ARCHIVED: ' (archived)', NOT_LINKED: ' (not linked)' } as const;

export function RecordInstallDialog(props: {
  open: boolean;
  releaseId: string;
  history: InstallHistory;
  correcting?: InstallEntry;
  onClose: () => void;
  onDone: (message: string) => void;
}) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const record = useRecordInstall(props.releaseId);
  const correct = useCorrectInstall(props.releaseId);
  const clients = useClients();
  const { correcting, history } = props;

  const [productId, setProductId] = useState('');
  const [selected, setSelected] = useState<string[]>([]);
  const [outcome, setOutcome] = useState<RecordableOutcome>('SUCCESS');
  const [installedAt, setInstalledAt] = useState('');
  const [installedAtEdited, setInstalledAtEdited] = useState(false);
  const [note, setNote] = useState('');
  const [reason, setReason] = useState('');

  // Permanently mounted, so reopening must not carry over the previous attempt's values or error.
  useEffect(() => {
    const dialog = dialogRef.current;
    if (!dialog) return;
    if (props.open && !dialog.open) {
      const selectable = history.products.filter((p) => !p.archived);
      setProductId(correcting?.productId ?? (selectable.length === 1 ? selectable[0].id : ''));
      setSelected([]);
      setOutcome(correcting && correcting.outcome !== 'VOIDED' ? correcting.outcome : 'SUCCESS');
      setInstalledAt(correcting ? toLocalInput(correcting.installedAt) : '');
      setInstalledAtEdited(false);
      setNote(correcting?.note ?? '');
      setReason('');
      record.reset();
      correct.reset();
      dialog.showModal();
    }
    if (!props.open && dialog.open) dialog.close();
  }, [props.open]);

  const product = history.products.find((p) => p.id === productId);
  const linked = (clients.data?.clients ?? [])
    .filter((c) => !c.archived && c.productIds.includes(productId))
    .sort((a, b) => a.name.localeCompare(b.name));

  const active = correcting ? correct : record;
  const problem = active.isError && active.error instanceof ApiError ? active.error.problem : undefined;
  const rejected = rejectedClients(active.error);
  const rejectedIds = new Set(rejected.map((r) => r.clientId));
  const inFuture = installedAt !== '' && new Date(installedAt).getTime() > Date.now();
  const count = selected.length;

  function toggle(id: string, checked: boolean) {
    setSelected((current) => (checked ? [...current, id] : current.filter((s) => s !== id)));
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    const trimmedNote = note.trim() || null;
    if (correcting) {
      correct.mutate(
        {
          installId: correcting.id,
          outcome,
          // Untouched keeps the original instant verbatim: the local input drops milliseconds and zone.
          installedAt: installedAtEdited ? new Date(installedAt).toISOString() : correcting.installedAt,
          note: trimmedNote,
          reason: reason.trim(),
        },
        { onSuccess: () => props.onDone('Correction saved.') },
      );
      return;
    }
    record.mutate(
      {
        productId,
        clientIds: selected,
        outcome,
        installedAt: installedAt === '' ? null : new Date(installedAt).toISOString(),
        note: trimmedNote,
      },
      { onSuccess: () => props.onDone(`Install recorded for ${count} ${count === 1 ? 'client' : 'clients'}.`) },
    );
  }

  const named = rejected
    .slice(0, MAX_NAMED_CLIENTS)
    .map((r) => `${r.name ?? 'Unknown client'}${REJECTION_SUFFIX[r.reason]}`);
  const more = rejected.length - named.length;

  const submitLabel = correcting
    ? 'Save correction'
    : count > 1
      ? `Record install for ${count} clients`
      : 'Record install';
  const blocked =
    active.isPending ||
    inFuture ||
    (correcting ? reason.trim() === '' || installedAt === '' : productId === '' || count === 0);

  return (
    <dialog
      ref={dialogRef}
      onClose={props.onClose}
      className="w-[32rem] max-w-full rounded border border-gray-200 bg-white p-6"
    >
      <h2 className="text-base font-semibold text-gray-900">{correcting ? 'Correct install' : 'Record install'}</h2>
      <form onSubmit={handleSubmit} className="mt-4 space-y-3">
        {correcting ? (
          <>
            <p className="text-sm text-gray-700">Product: {correcting.productName}</p>
            <p className="text-sm text-gray-700">Client: {correcting.clientName}</p>
          </>
        ) : (
          <>
            <div>
              <label htmlFor="installProduct" className="block text-sm font-semibold text-gray-700">
                Product
              </label>
              <select
                id="installProduct"
                value={productId}
                onChange={(event) => {
                  setProductId(event.target.value);
                  setSelected([]);
                }}
                className={FIELD}
              >
                {history.products.length !== 1 && <option value="">Select a product</option>}
                {history.products.map((p) => (
                  <option key={p.id} value={p.id} disabled={p.archived}>
                    {p.archived ? `${p.name} (archived)` : p.name}
                  </option>
                ))}
              </select>
            </div>
            <fieldset>
              <legend className="text-sm font-semibold text-gray-700">Clients that installed it</legend>
              {product && linked.length === 0 && (
                <p className="mt-1 text-sm text-gray-600">
                  No active clients are linked to {product.name}. Link clients on the{' '}
                  <Link to="/portfolio" className="text-blue-600 underline">
                    Portfolio page
                  </Link>
                  .
                </p>
              )}
              {product && linked.length > 0 && (
                <>
                  <div className="mt-1 flex items-center gap-2">
                    <button type="button" className={SMALL_BUTTON} onClick={() => setSelected(linked.map((c) => c.id))}>
                      Select all
                    </button>
                    <button type="button" className={SMALL_BUTTON} onClick={() => setSelected([])}>
                      Clear
                    </button>
                    <span aria-live="polite" className="text-xs text-gray-600">
                      {count} selected
                    </span>
                  </div>
                  <div className="mt-2 max-h-64 overflow-y-auto rounded border border-gray-300 p-3">
                    {linked.map((c) => (
                      <label key={c.id} className="flex items-center gap-2 py-0.5 text-sm text-gray-700">
                        <input
                          type="checkbox"
                          checked={selected.includes(c.id)}
                          onChange={(event) => toggle(c.id, event.target.checked)}
                        />
                        {c.name}
                        {rejectedIds.has(c.id) && (
                          <span className="text-xs text-red-700">can't receive this install</span>
                        )}
                      </label>
                    ))}
                  </div>
                </>
              )}
            </fieldset>
          </>
        )}
        <div>
          <label htmlFor="installOutcome" className="block text-sm font-semibold text-gray-700">
            Outcome
          </label>
          <select
            id="installOutcome"
            value={outcome}
            onChange={(event) => setOutcome(event.target.value as RecordableOutcome)}
            className={FIELD}
          >
            {RECORDABLE_OUTCOMES.map((o) => (
              <option key={o} value={o}>
                {OUTCOME_LABELS[o]}
              </option>
            ))}
          </select>
          {outcome === 'ROLLED_BACK' && (
            <p className="mt-1 text-xs text-gray-600">
              A rollback doesn't change what the client runs. To show the client went back to an older release, record
              a Success install of that release.
            </p>
          )}
        </div>
        <div>
          <label htmlFor="installInstalledAt" className="block text-sm font-semibold text-gray-700">
            Installed at (leave empty for now)
          </label>
          <input
            id="installInstalledAt"
            type="datetime-local"
            step={correcting ? 1 : undefined}
            max={toLocalInput(new Date().toISOString())}
            value={installedAt}
            onChange={(event) => {
              setInstalledAt(event.target.value);
              setInstalledAtEdited(true);
            }}
            className={FIELD}
          />
          <p className="mt-1 text-xs text-gray-600">
            Back-dating an install never replaces a newer one as the client's current release.
          </p>
          {inFuture && <p className="mt-1 text-xs text-red-600">Installed time can't be in the future.</p>}
        </div>
        <div>
          <label htmlFor="installNote" className="block text-sm font-semibold text-gray-700">
            Note (optional)
          </label>
          <textarea
            id="installNote"
            maxLength={2000}
            value={note}
            onChange={(event) => setNote(event.target.value)}
            className={FIELD}
          />
        </div>
        {correcting && (
          <div>
            <label htmlFor="installReason" className="block text-sm font-semibold text-gray-700">
              Reason for correction
            </label>
            <textarea
              id="installReason"
              required
              maxLength={2000}
              value={reason}
              onChange={(event) => setReason(event.target.value)}
              className={FIELD}
            />
          </div>
        )}

        {!history.inProduction && (
          <div role="status" className="rounded border border-amber-600 bg-amber-50 p-3 text-sm text-amber-700">
            <p className="font-semibold">This release isn't in Production yet.</p>
            <p>You can still record this install. It will appear as 'not in Production' on the client's page.</p>
          </div>
        )}

        {problem && (
          <div role="alert" data-reason={problem.reason} className="text-sm text-red-600">
            {rejected.length > 0 ? (
              <>
                <p className="font-semibold text-red-700">Nothing was recorded.</p>
                <p>
                  These clients can't receive this install: {named.join(', ')}
                  {more > 0 && ` and ${more} more`}. Untick them or fix their links on the Portfolio page, then record
                  again.
                </p>
              </>
            ) : (
              <p>Couldn't record the install — {messageForReason(problem)}. Try again.</p>
            )}
          </div>
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
            disabled={blocked}
            className="flex items-center gap-2 rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            {active.isPending && (
              <span
                aria-hidden="true"
                className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white motion-reduce:animate-none"
              />
            )}
            {active.isPending ? (correcting ? 'Saving…' : 'Recording…') : submitLabel}
          </button>
        </div>
      </form>
    </dialog>
  );
}
