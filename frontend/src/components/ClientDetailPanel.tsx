import { useState } from 'react';
import { Link } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useClient, useLinkProduct, useUnlinkProduct, type ClientRun } from '../api/clients';
import { useProducts } from '../api/products';
import { OUTCOME_COLOURS, OUTCOME_LABELS } from '../api/promotions';
import { ConfirmDialog } from './ConfirmDialog';
import { SMALL_BUTTON } from './EnvironmentLadderSection';

const PANEL = 'rounded border border-gray-200 bg-white p-4';
const CONFIRM_BLUE = 'rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white';
const BORDERED_BUTTON = 'rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700';
const RELEASE_LINK = 'font-semibold text-blue-600 hover:underline';

export function ClientDetailPanel({ clientId, onShowProducts }: { clientId?: string; onShowProducts: () => void }) {
  const client = useClient(clientId);
  const products = useProducts();
  const link = useLinkProduct(clientId ?? '');
  const unlink = useUnlinkProduct(clientId ?? '');
  const [chosen, setChosen] = useState('');
  const [unlinking, setUnlinking] = useState<ClientRun | null>(null);

  if (!clientId) {
    return (
      <section className={PANEL}>
        <p className="text-sm text-gray-600">Select a client to see what it runs.</p>
      </section>
    );
  }

  if (client.isPending) {
    return (
      <section className={`${PANEL} flex items-center gap-3`}>
        <span
          className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
          aria-hidden="true"
        />
        <p className="text-sm text-gray-600">Loading clients…</p>
      </section>
    );
  }

  if (client.isError) {
    const problem = client.error instanceof ApiError ? client.error.problem : undefined;
    const text = problem ? messageForReason(problem) : client.error.message;
    return (
      <section className={PANEL}>
        <p role="alert" data-reason={problem?.reason} className="text-sm text-red-600">
          Couldn&apos;t load clients — {text.replace(/\.$/, '')}. Try again.
        </p>
        <button type="button" onClick={() => client.refetch()} className={`mt-2 ${BORDERED_BUTTON}`}>
          Try again
        </button>
      </section>
    );
  }

  const detail = client.data;
  const linkedIds = new Set(detail.runs.map((run) => run.productId));
  const allProducts = products.data?.products ?? [];
  const linkable = allProducts.filter((p) => !p.archived && !linkedIds.has(p.id));
  const mutationError = [link, unlink].find((m) => m.isError)?.error;
  const problem = mutationError instanceof ApiError ? mutationError.problem : undefined;
  const busy = link.isPending || unlink.isPending;

  function handleLink() {
    link.mutate(chosen, { onSuccess: () => setChosen('') });
  }

  function confirmUnlink() {
    if (!unlinking) return;
    unlink.mutate(unlinking.productId, { onSettled: () => setUnlinking(null) });
  }

  return (
    <section className={PANEL}>
      <div className="flex flex-wrap items-center gap-2">
        <h2 className="text-base font-semibold text-gray-900">{detail.name}</h2>
        {detail.archived && <span className="text-xs text-gray-500">archived</span>}
      </div>

      <h3 className="mt-4 text-sm font-semibold text-gray-700">Runs</h3>
      {detail.runs.length === 0 ? (
        <p className="mt-2 text-sm text-gray-600">
          This client isn&apos;t linked to any product yet. Use &apos;Link a product&apos; below.
        </p>
      ) : (
        <div className="mt-2 overflow-x-auto">
          <table className="min-w-full text-sm">
            <thead>
              <tr className="text-left text-gray-600">
                <th scope="col" className="py-1 pr-4 font-semibold">Product</th>
                <th scope="col" className="py-1 pr-4 font-semibold">Current release</th>
                <th scope="col" className="py-1 pr-4 font-semibold">Installed</th>
                <th scope="col" className="py-1">
                  <span className="sr-only">Actions</span>
                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {detail.runs.map((run) => {
                const noteId = `unlink-blocked-${run.productId}`;
                return (
                  <tr key={run.productId} className="align-top">
                    <td className="py-2 pr-4">
                      <span className="font-semibold text-gray-900">{run.productName}</span>
                      {run.productArchived && <span className="ml-2 text-xs text-gray-500">archived</span>}
                    </td>
                    <td className="py-2 pr-4">
                      {run.current ? (
                        <>
                          <Link
                            to="/releases/$releaseId"
                            params={{ releaseId: run.current.releaseId }}
                            className={RELEASE_LINK}
                          >
                            {run.current.releaseName} {run.current.releaseVersion}
                          </Link>
                          {!run.current.inProduction && (
                            <span className="ml-2 text-xs text-amber-700">not in Production</span>
                          )}
                        </>
                      ) : (
                        <span className="text-gray-500">Not installed</span>
                      )}
                    </td>
                    <td className="py-2 pr-4 text-gray-700">
                      {run.current ? new Date(run.current.installedAt).toLocaleString() : '—'}
                    </td>
                    <td className="py-2">
                      <button
                        type="button"
                        aria-label={`Unlink ${run.productName}`}
                        aria-disabled={run.hasInstalls ? 'true' : undefined}
                        aria-describedby={run.hasInstalls ? noteId : undefined}
                        disabled={!run.hasInstalls && busy}
                        onClick={() => {
                          if (!run.hasInstalls) setUnlinking(run);
                        }}
                        className={`${SMALL_BUTTON} ${run.hasInstalls ? 'cursor-not-allowed opacity-50' : ''}`}
                      >
                        Unlink
                      </button>
                      {run.hasInstalls && (
                        <p id={noteId} className="mt-1 text-xs text-amber-700">
                          Can&apos;t unlink: {detail.name} has install records for {run.productName}. Archive the
                          client instead to stop new installs.
                        </p>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {problem && (
        <p role="alert" data-reason={problem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(problem)}
        </p>
      )}

      {!detail.archived && (
        <div className="mt-3">
          {allProducts.length === 0 ? (
            <p className="text-xs text-gray-600">
              No products yet. Add one on the{' '}
              <button type="button" onClick={onShowProducts} className="text-blue-600 underline">
                Products tab
              </button>
              .
            </p>
          ) : linkable.length === 0 ? (
            <p className="text-xs text-gray-600">Every active product is already linked.</p>
          ) : (
            <div className="flex flex-wrap items-end gap-2">
              <div className="flex flex-col gap-1">
                <label htmlFor="link-product" className="text-sm font-medium text-gray-700">
                  Link a product
                </label>
                <select
                  id="link-product"
                  value={chosen}
                  onChange={(e) => setChosen(e.target.value)}
                  className="rounded border border-gray-300 px-2 py-1 text-sm"
                >
                  <option value="" disabled>
                    Choose a product…
                  </option>
                  {linkable.map((p) => (
                    <option key={p.id} value={p.id}>
                      {p.name}
                    </option>
                  ))}
                </select>
              </div>
              <button type="button" disabled={!chosen || busy} onClick={handleLink} className={BORDERED_BUTTON}>
                Link
              </button>
            </div>
          )}
        </div>
      )}

      <h3 className="mt-6 text-sm font-semibold text-gray-700">Install history</h3>
      {detail.history.length === 0 ? (
        <p className="mt-2 text-sm text-gray-600">
          No installs recorded for this client yet. Installs are recorded from a frozen release&apos;s page.
        </p>
      ) : (
        <ul className="mt-4 divide-y divide-gray-100">
          {detail.history.map((entry) => (
            <li key={entry.id} className={`py-2 text-sm ${entry.effective ? '' : 'text-gray-400'}`}>
              <div className="flex flex-wrap items-center gap-2">
                <span>{entry.productName} · </span>
                <Link
                  to="/releases/$releaseId"
                  params={{ releaseId: entry.releaseId }}
                  className={`${RELEASE_LINK} ${entry.supersededById ? 'line-through' : ''}`}
                >
                  {entry.releaseName} {entry.releaseVersion}
                </Link>
                {entry.effective && (
                  <span className={OUTCOME_COLOURS[entry.outcome]}>{OUTCOME_LABELS[entry.outcome]}</span>
                )}
                {!entry.effective && <span className="text-xs">Superseded</span>}
              </div>
              <p className="text-xs text-gray-600">
                Installed {new Date(entry.installedAt).toLocaleString()} · Recorded{' '}
                {new Date(entry.recordedAt).toLocaleString()} by {entry.recordedBy}
                {!entry.inProduction && <span className="text-amber-700"> · not in Production</span>}
              </p>
              {entry.note && <p className="mt-1 text-sm">{entry.note}</p>}
              {entry.correctionReason && <p className="text-xs">Correction: {entry.correctionReason}</p>}
            </li>
          ))}
        </ul>
      )}

      <ConfirmDialog
        open={unlinking !== null}
        title="Unlink product"
        body={unlinking ? `Unlink ${unlinking.productName} from ${detail.name}? You can link it again later.` : ''}
        confirmLabel="Unlink"
        confirmClassName={CONFIRM_BLUE}
        pending={unlink.isPending}
        onConfirm={confirmUnlink}
        onCancel={() => setUnlinking(null)}
      />
    </section>
  );
}
