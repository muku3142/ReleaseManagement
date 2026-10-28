import { useEffect, useState, type ReactNode } from 'react';
import { useNavigate, useSearch } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useProducts } from '../api/products';
import {
  useExportRoadmapCsv,
  useRefreshAll,
  useRefreshItem,
  useRoadmap,
  type RoadmapAddResult as AddResult,
  type RoadmapFilter,
  type RoadmapItem,
  type RoadmapRefreshResult,
  type RoadmapSearch,
} from '../api/roadmap';
import { SMALL_BUTTON } from '../components/EnvironmentLadderSection';
import { Spinner } from '../components/ExportButtons';
import { ProductFilter } from '../components/ProductFilter';
import { AddFromJiraDialog } from '../roadmap/AddFromJiraDialog';
import { AddFromReleaseDialog } from '../roadmap/AddFromReleaseDialog';
import { AddManualItemDialog } from '../roadmap/AddManualItemDialog';
import { RoadmapAddResult } from '../roadmap/RoadmapAddResult';
import { RoadmapTable } from '../roadmap/RoadmapTable';
import { RubricPanel } from '../roadmap/RubricPanel';

const BUTTON = 'rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 disabled:opacity-50';
const REFRESH_TITLE =
  'Updates the Jira summary and status of every Jira item. Your Reach, Impact, Confidence and Effort are never changed.';

type Dialog = 'jira' | 'release' | 'manual';
type Notice = { kind: 'add'; result: AddResult } | { kind: 'text'; lines: string[] };

const failureText = (error: Error) => (error instanceof ApiError ? messageForReason(error.problem) : error.message);

const countText = (n: number) => (n === 1 ? '1 item shown' : `${n} items shown`);

function refreshAllLines(result: RoadmapRefreshResult): string[] {
  const lines = [`Refreshed ${result.refreshed === 1 ? '1 item' : `${result.refreshed} items`} from Jira.`];
  if (result.unavailable.length > 0) {
    lines.push(`${result.unavailable.length} no longer in Jira and are marked unavailable.`);
  }
  return lines;
}

export function RoadmapRoute() {
  const search: RoadmapSearch = useSearch({ from: '/authenticated/roadmap' });
  const navigate = useNavigate();
  const filter: RoadmapFilter = { products: search.products ?? [], done: !!search.done, archived: !!search.archived };
  const roadmap = useRoadmap(filter);
  const products = useProducts().data?.products ?? [];
  const refreshAll = useRefreshAll();
  const refreshItem = useRefreshItem();
  const exportCsv = useExportRoadmapCsv();

  const [dialog, setDialog] = useState<Dialog | null>(null);
  const [notice, setNotice] = useState<Notice | null>(null);
  const [jiraError, setJiraError] = useState<string | null>(null);
  const [openIds, setOpenIds] = useState<ReadonlySet<string>>(() => new Set(search.item ? [search.item] : []));
  const [savedId, setSavedId] = useState<string | null>(null);
  const [announcement, setAnnouncement] = useState('');

  const items = roadmap.data?.items ?? [];
  const jiraItems = items.filter((item) => item.source === 'JIRA').length;
  const hasFilter = filter.products.length > 0 || filter.done || filter.archived;
  const problem = roadmap.isError && roadmap.error instanceof ApiError ? roadmap.error.problem : undefined;

  useEffect(() => {
    const id = search.item;
    if (id) setOpenIds((prev) => (prev.has(id) ? prev : new Set(prev).add(id)));
  }, [search.item]);

  // The list is refetched after an inline save, so the announcement reads the new score and rank.
  useEffect(() => {
    if (savedId === null || !roadmap.data) return;
    const saved = roadmap.data.items.find((item) => item.id === savedId);
    if (!saved) return;
    setAnnouncement(
      saved.rank === null
        ? `${saved.title}: not scored yet.`
        : `${saved.title}: score ${saved.scoreDisplay}, rank ${saved.rank}.`,
    );
    setSavedId(null);
  }, [savedId, roadmap.data]);

  const setSearch = (changes: Partial<RoadmapSearch>) =>
    navigate({ to: '/roadmap', search: (prev) => ({ ...(prev as RoadmapSearch), ...changes }), replace: true });

  const clearFilters = () => setSearch({ products: undefined, done: undefined, archived: undefined });

  const toggleOpen = (id: string) =>
    setOpenIds((prev) => {
      const next = new Set(prev);
      if (!next.delete(id)) next.add(id);
      return next;
    });

  function showAdded(result: AddResult) {
    setJiraError(null);
    setNotice({ kind: 'add', result });
  }

  function onManualAdded(item: RoadmapItem) {
    showAdded({ added: [{ itemId: item.id, jiraKey: item.jiraKey ?? '' }], alreadyPresent: [], notFound: [], rejected: [] });
    setOpenIds((prev) => new Set(prev).add(item.id));
    setSearch({ item: item.id });
  }

  function runRefreshAll() {
    if (jiraItems === 0) return;
    setJiraError(null);
    refreshAll.mutate(undefined, {
      onSuccess: (result) => setNotice({ kind: 'text', lines: refreshAllLines(result) }),
      onError: (error) => {
        setNotice(null);
        setJiraError(`Couldn't reach Jira — ${failureText(error)} Nothing was changed. Try again.`);
      },
    });
  }

  function runRefreshItem(item: RoadmapItem) {
    setJiraError(null);
    refreshItem.mutate(item.id, {
      onSuccess: (result) =>
        setNotice({
          kind: 'text',
          lines: [
            result.unavailable.some((u) => u.itemId === item.id)
              ? `${item.jiraKey} is no longer in Jira. It stays on the roadmap and is marked unavailable.`
              : `Refreshed ${item.jiraKey} from Jira.`,
          ],
        }),
      onError: (error) => {
        setNotice(null);
        setJiraError(`Couldn't reach Jira — ${failureText(error)} Nothing was changed. Try again.`);
      },
    });
  }

  const addButtons = (
    <>
      <button type="button" className={BUTTON} onClick={() => setDialog('jira')}>
        Add from Jira
      </button>
      <button type="button" className={BUTTON} onClick={() => setDialog('release')}>
        Add from a release
      </button>
      <button type="button" className={BUTTON} onClick={() => setDialog('manual')}>
        Add manual item
      </button>
    </>
  );

  const nothingToRefresh = jiraItems === 0;
  const nothingToExport = items.length === 0;

  let content: ReactNode;
  if (roadmap.isPending) {
    content = (
      <div className="flex items-center gap-3">
        <Spinner />
        <span className="text-sm text-gray-700">Loading roadmap…</span>
      </div>
    );
  } else if (roadmap.isError) {
    content = (
      <div role="alert" data-reason={problem?.reason} className="space-y-2 text-sm text-red-600">
        <p>{`Couldn't load the roadmap — ${problem ? messageForReason(problem) : roadmap.error.message} Try again.`}</p>
        <button type="button" className={SMALL_BUTTON} onClick={() => roadmap.refetch()}>
          Try again
        </button>
      </div>
    );
  } else if (items.length === 0) {
    content = hasFilter ? (
      <div className="space-y-2 rounded bg-gray-50 p-6 text-center">
        <h3 className="text-base font-semibold text-gray-900">No items match these filters</h3>
        <p className="text-sm text-gray-700">Choose different products, or turn on Show done or Show archived.</p>
        <button type="button" className="text-sm text-blue-600 underline" onClick={clearFilters}>
          Clear filters
        </button>
      </div>
    ) : (
      <div className="space-y-3 rounded bg-gray-50 p-6 text-center">
        <h3 className="text-base font-semibold text-gray-900">No roadmap items yet</h3>
        <p className="text-sm text-gray-700">
          Add items from Jira or by hand, then set Reach, Impact, Confidence and Effort to rank them.
        </p>
        <div className="flex flex-wrap justify-center gap-3">{addButtons}</div>
      </div>
    );
  } else {
    content = (
      <RoadmapTable
        items={items}
        openIds={openIds}
        onToggle={toggleOpen}
        refreshDisabled={refreshAll.isPending}
        onRefreshItem={runRefreshItem}
        refreshingId={refreshItem.isPending ? refreshItem.variables : null}
        focusId={search.item}
        onInputSaved={(saved) => setSavedId(saved.id)}
      />
    );
  }

  return (
    <div className="space-y-8">
      <h2 className="text-base font-semibold text-gray-900">Roadmap</h2>

      <div className="space-y-4">
        <div className="flex flex-wrap items-center gap-3">
          {addButtons}
          <div className="ml-auto flex flex-wrap items-center gap-3">
            <button
              type="button"
              className={BUTTON}
              title={REFRESH_TITLE}
              disabled={refreshAll.isPending}
              aria-disabled={nothingToRefresh || undefined}
              aria-describedby={nothingToRefresh ? 'refresh-help' : undefined}
              onClick={runRefreshAll}
            >
              {refreshAll.isPending ? 'Refreshing…' : 'Refresh from Jira'}
            </button>
            {refreshAll.isPending && <Spinner />}
            {nothingToRefresh && (
              <small id="refresh-help" className="text-xs text-gray-600">
                No Jira items to refresh.
              </small>
            )}
            <button
              type="button"
              className={BUTTON}
              disabled={exportCsv.isPending}
              aria-disabled={nothingToExport || undefined}
              aria-describedby="export-help"
              onClick={() => {
                if (!nothingToExport) exportCsv.mutate(filter);
              }}
            >
              {exportCsv.isPending ? 'Exporting CSV…' : 'Export CSV'}
            </button>
            {exportCsv.isPending && <Spinner />}
            <small id="export-help" className="text-xs text-gray-600">
              {nothingToExport ? 'Nothing to export.' : `Exports the ${items.length} items shown.`}
            </small>
          </div>
        </div>
        {exportCsv.isError && (
          <p role="alert" className="text-xs text-red-600">
            {`Couldn't export the CSV — ${failureText(exportCsv.error)} Try again.`}
          </p>
        )}

        <div className="flex flex-wrap items-center gap-3">
          <ProductFilter
            products={products}
            selected={filter.products}
            onChange={(ids) => setSearch({ products: ids.length ? ids : undefined })}
          />
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={filter.done}
              onChange={(event) => setSearch({ done: event.target.checked ? true : undefined })}
            />
            Show done
          </label>
          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={filter.archived}
              onChange={(event) => setSearch({ archived: event.target.checked ? true : undefined })}
            />
            Show archived
          </label>
          {roadmap.data && (
            <span role="status" aria-live="polite" className="ml-auto text-xs text-gray-600">
              {countText(items.length)}
            </span>
          )}
        </div>
      </div>

      {(notice || jiraError) && (
        <div role={jiraError ? 'alert' : 'status'} className="rounded border border-gray-200 bg-white p-3 text-sm">
          <div className="flex items-start justify-between gap-3">
            <div className="space-y-1">
              {jiraError && <p className="text-red-600">{jiraError}</p>}
              {notice?.kind === 'add' && <RoadmapAddResult result={notice.result} />}
              {notice?.kind === 'text' &&
                notice.lines.map((line) => (
                  <p key={line} className={line === notice.lines[0] ? undefined : 'text-xs text-gray-600'}>
                    {line}
                  </p>
                ))}
            </div>
            <button
              type="button"
              aria-label="Dismiss message"
              className={SMALL_BUTTON}
              onClick={() => {
                setNotice(null);
                setJiraError(null);
              }}
            >
              Dismiss
            </button>
          </div>
        </div>
      )}

      <RubricPanel />

      {content}

      <p role="status" aria-live="polite" aria-atomic="true" className="sr-only">
        {announcement}
      </p>

      <AddFromJiraDialog open={dialog === 'jira'} onClose={() => setDialog(null)} onAdded={showAdded} />
      <AddFromReleaseDialog open={dialog === 'release'} onClose={() => setDialog(null)} onAdded={showAdded} />
      <AddManualItemDialog open={dialog === 'manual'} onClose={() => setDialog(null)} onAdded={onManualAdded} />
    </div>
  );
}
