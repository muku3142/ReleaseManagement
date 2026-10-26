import { Link } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import {
  defaultHistoryDates,
  historyRange,
  mergeDeploymentPages,
  useDeploymentHistory,
  type DashboardSearch,
} from '../api/dashboard';
import { useEnvironmentLadder } from '../api/ladder';
import { OUTCOME_COLOURS, OUTCOME_LABELS } from '../api/promotions';

const CONTROL =
  'rounded border border-gray-300 px-3 py-1.5 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600';
const BORDERED_BUTTON = 'rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700';

function Spinner() {
  return (
    <span
      className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
      aria-hidden="true"
    />
  );
}

export function DeploymentHistory({
  search,
  onSearchChange,
}: {
  search: DashboardSearch;
  onSearchChange: (next: DashboardSearch) => void;
}) {
  const defaults = defaultHistoryDates();
  const from = search.from ?? defaults.from;
  const to = search.to ?? defaults.to;
  const range = historyRange(from, to);
  const history = useDeploymentHistory(
    range.ok
      ? { stageId: search.stage, fromIso: range.fromIso, toIso: range.toIso, productIds: search.products }
      : null,
  );
  const ladder = useEnvironmentLadder();

  const stages = [...(ladder.data?.stages ?? [])].sort((a, b) => a.position - b.position);
  const environments = [...stages.filter((s) => !s.retired), ...stages.filter((s) => s.retired)];
  const rows = mergeDeploymentPages(history.data?.pages ?? []);
  const problem = history.isError && history.error instanceof ApiError ? history.error.problem : undefined;

  const change = (key: 'stage' | 'from' | 'to', value: string) =>
    onSearchChange({ ...search, [key]: value || undefined });

  const clearFilters = () => onSearchChange({ ...search, stage: undefined, from: undefined, to: undefined });

  return (
    <section className="rounded border border-gray-200 bg-white p-4">
      <h3 className="text-base font-semibold text-gray-900">Deployment history</h3>

      <div className="mt-3 flex flex-wrap items-end gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="history-environment" className="text-xs text-gray-600">
            Environment
          </label>
          <select
            id="history-environment"
            value={search.stage ?? ''}
            onChange={(event) => change('stage', event.target.value)}
            className={CONTROL}
          >
            <option value="">All environments</option>
            {environments.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name}
                {s.retired ? ' (retired)' : ''}
              </option>
            ))}
          </select>
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="history-from" className="text-xs text-gray-600">
            From
          </label>
          <input
            id="history-from"
            type="date"
            value={from}
            onChange={(event) => change('from', event.target.value)}
            className={CONTROL}
          />
        </div>
        <div className="flex flex-col gap-1">
          <label htmlFor="history-to" className="text-xs text-gray-600">
            To
          </label>
          <input
            id="history-to"
            type="date"
            value={to}
            onChange={(event) => change('to', event.target.value)}
            className={CONTROL}
          />
        </div>
        {!range.ok && <p className="text-xs text-amber-700">{range.message}</p>}
        <button
          type="button"
          onClick={clearFilters}
          className="text-sm text-blue-600 hover:underline"
        >
          Clear filters
        </button>
      </div>

      {history.isLoading && (
        <div className="mt-4 flex items-center gap-3">
          <Spinner />
          <p className="text-sm text-gray-600">Loading deployment history…</p>
        </div>
      )}

      {problem && (
        <div className="mt-4">
          <p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
            Couldn&apos;t load deployment history — {messageForReason(problem).replace(/\.$/, '')}. Try again.
          </p>
          <button type="button" onClick={() => history.refetch()} className={`mt-2 ${BORDERED_BUTTON}`}>
            Try again
          </button>
        </div>
      )}

      {history.data && rows.length === 0 && (
        <div className="mt-4 rounded bg-gray-50 p-6 text-center">
          <p className="text-base font-semibold text-gray-900">No promotions match these filters</p>
          <p className="mt-2 text-sm text-gray-600">
            Try a wider date range or a different environment. Promotions are recorded from a frozen
            release&apos;s page.{' '}
            <button
              type="button"
              onClick={clearFilters}
              className="text-blue-600 underline"
            >
              Clear filters
            </button>
          </p>
        </div>
      )}

      {rows.length > 0 && (
        <>
          <p className="mt-4 text-xs text-gray-500">
            {rows.length} promotions shown{history.hasNextPage ? ' · more available' : ''}
          </p>
          <div className="mt-2 overflow-x-auto">
            <table className={`min-w-full text-sm ${history.isPlaceholderData ? 'opacity-60' : ''}`}>
              <thead className="border-b border-gray-200">
                <tr>
                  {['Release', 'Environment', 'Outcome', 'Deployed', 'By', 'Note'].map((heading) => (
                    <th key={heading} scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                      {heading}
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((e) => (
                  <tr key={e.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="whitespace-nowrap px-4 py-2">
                      <Link
                        to="/releases/$releaseId"
                        params={{ releaseId: e.releaseId }}
                        className="font-semibold text-blue-600 hover:underline"
                      >
                        {e.releaseName}
                      </Link>
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">
                      {e.stageName}
                      {e.stageRetired ? ' (retired)' : ''}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2">
                      <span className={OUTCOME_COLOURS[e.outcome]}>{OUTCOME_LABELS[e.outcome]}</span>
                      {e.corrected && (
                        <Link
                          to="/releases/$releaseId"
                          params={{ releaseId: e.releaseId }}
                          hash="history"
                          className="ml-2 text-xs text-gray-500 hover:underline"
                        >
                          corrected
                        </Link>
                      )}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">
                      {new Date(e.deployedAt).toLocaleString()}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">{e.recordedBy}</td>
                    <td className="max-w-xs truncate px-4 py-2 text-gray-700" title={e.note ?? undefined}>
                      {e.note}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {history.hasNextPage && (
        <button
          type="button"
          onClick={() => history.fetchNextPage()}
          disabled={history.isFetchingNextPage}
          className={`mt-3 flex items-center gap-2 disabled:opacity-50 ${BORDERED_BUTTON}`}
        >
          {history.isFetchingNextPage ? (
            <>
              <Spinner />
              Loading…
            </>
          ) : (
            'Load more'
          )}
        </button>
      )}
    </section>
  );
}
