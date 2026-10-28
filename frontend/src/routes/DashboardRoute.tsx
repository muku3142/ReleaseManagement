import { Suspense, lazy, useMemo, useState } from 'react';
import { Link, useNavigate, useSearch } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import {
  formatDuration,
  missingDocuments,
  outOfDateDocuments,
  paperworkCounts,
  useDashboardReleases,
  type DashboardData,
  type DashboardDocsFilter,
  type DashboardSearch,
  type DashboardReleaseRow,
} from '../api/dashboard';
import { readinessIndex, useDocumentReadiness, type ReadinessLookup } from '../api/documents';
import { DOCUMENT_TYPES, DOCUMENT_TYPE_LABELS } from '../api/templates';
import { FilterTile, Tile } from '../components/DashboardTiles';
import { DeploymentHistory } from '../components/DeploymentHistory';
import { LadderDots } from '../components/LadderDots';
import { PortfolioTab } from '../components/PortfolioTab';
import { ProductFilter } from '../components/ProductFilter';
import { ReadinessPill } from '../components/ReadinessPill';
import { useProducts, type Product } from '../api/products';
import type { StageChartRow } from '../components/StageChart';
import { Skeleton } from '@/components/ui/skeleton';
import { useEntranceStagger } from '@/hooks/useEntranceStagger';
import { cn } from '@/lib/utils';

const StageChart = lazy(() => import('../components/StageChart'));

const TH = 'px-4 py-2 text-left font-semibold text-gray-700';
const TD = 'whitespace-nowrap px-4 py-2 align-top';

const TABS = [
  { id: 'releases', label: 'Releases' },
  { id: 'portfolio', label: 'Portfolio' },
] as const;
type DashboardTab = (typeof TABS)[number]['id'];

const CODE_LINKS_TITLE = (manual: number) =>
  `Counts Jira-backed tickets only. ${manual} manual items are excluded. 'No data' means code links were never fetched for the ticket — it is not the same as 'no link'.`;
const STORY_POINTS_TITLE =
  "Sum of the Jira story-point estimates of this release's Jira tickets. Tickets with no estimate are counted separately, never as zero. 'Not read' tickets were snapshotted before story points were captured: refresh the release to read them (a frozen release keeps its snapshot). Manual items are excluded.";
const CHURN_TITLE =
  "Median commits made before the ticket first reached a Done status. Tickets with no history or no linked commits are excluded and counted as 'no data', never as zero.";
const DOCS_FILTER_LABELS: Record<DashboardDocsFilter, string> = {
  missing: 'Releases missing documents',
  stale: 'Documents out of date',
};
const DOCS_FILTER_TILE_IDS: Record<DashboardDocsFilter, string> = { missing: 'tile-missing', stale: 'tile-stale' };
const MISSING_HELP =
  'Releases with at least one document that has not been generated yet or is out of date. Document types that have no template are ignored.';
const STALE_HELP =
  'Releases with a document that is out of date because tickets changed after it was generated. A frozen release is only counted if it was unfrozen and its tickets changed.';
const DOCS_UNAVAILABLE = 'Document status unavailable';
const DOCS_EMPTY_BODIES: Record<DashboardDocsFilter, string> = {
  missing: 'Every visible release has all of its documents generated and current.',
  stale: 'Every visible release has up-to-date documents.',
};
const REWORK_TITLE =
  "Reopens are moves from a Done status back to a non-Done one. 'After Done' is the share of commits dated after the ticket first reached Done.";

function oneDecimal(x: number): number {
  return Number(x.toFixed(1));
}

function Spinner() {
  return (
    <span
      className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
      aria-hidden="true"
    />
  );
}

// One or two names are spelled out, more are counted.
function productScope(ids: string[], products: Product[]): string | undefined {
  if (ids.length === 0) return undefined;
  const names = ids.map((id) => products.find((p) => p.id === id)?.name).filter(Boolean);
  return ids.length <= 2 && names.length === ids.length ? names.join(', ') : `${ids.length} products`;
}

function MedianTile({ caption, seconds, count }: { caption: string; seconds: number | null; count: number }) {
  return (
    <Tile caption={caption}>
      <span title={seconds === null ? 'No release has reached this point yet' : undefined}>
        {formatDuration(seconds)}
      </span>
      <div className="text-xs font-normal text-gray-500">n = {count}</div>
    </Tile>
  );
}

function SummaryStrip({
  data,
  counts,
  scope,
  docs,
  onToggleDocs,
}: {
  data: DashboardData;
  counts: { needPaperwork: number; stale: number } | undefined;
  scope: string | undefined;
  docs: DashboardDocsFilter | undefined;
  onToggleDocs: (kind: DashboardDocsFilter) => void;
}) {
  const { summary, stages } = data;
  const perStage = new Map(summary.releasesPerStage.map((s) => [s.stageId, s.count]));
  const shownStages = stages.filter((s) => !s.retired || (perStage.get(s.id) ?? 0) > 0);
  const chartRows: StageChartRow[] = [
    ...shownStages.map((s) => ({
      label: s.retired ? `${s.name} (retired)` : s.name,
      count: perStage.get(s.id) ?? 0,
      kind: 'stage' as const,
    })),
    { label: 'Not promoted', count: summary.notPromoted, kind: 'notPromoted' },
  ];
  return (
    <section aria-label="Summary">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-5">
        <FilterTile
          id="tile-missing"
          caption={DOCS_FILTER_LABELS.missing}
          figure={counts?.needPaperwork ?? '—'}
          unit="releases"
          pressed={docs === 'missing'}
          onPress={() => onToggleDocs('missing')}
          help={MISSING_HELP}
          disabledNote={counts ? undefined : DOCS_UNAVAILABLE}
        />
        <FilterTile
          id="tile-stale"
          caption={DOCS_FILTER_LABELS.stale}
          figure={counts?.stale ?? '—'}
          unit="releases"
          pressed={docs === 'stale'}
          onPress={() => onToggleDocs('stale')}
          help={STALE_HELP}
          disabledNote={counts ? undefined : DOCS_UNAVAILABLE}
        />
        <MedianTile
          caption="Median created → frozen"
          seconds={summary.medianCreatedToFrozenSeconds}
          count={summary.createdToFrozenCount}
        />
        <MedianTile
          caption="Median frozen → production"
          seconds={summary.medianFrozenToProductionSeconds}
          count={summary.frozenToProductionCount}
        />
        <div className="rounded border border-gray-200 bg-white p-4">
          <Suspense fallback={<Skeleton className="h-24" />}>
            <StageChart rows={chartRows} />
          </Suspense>
          <ul className="sr-only">
            {shownStages.map((s) => (
              <li key={s.id} className="tabular-nums">
                {s.name}
                {s.retired ? ' (retired)' : ''} {perStage.get(s.id) ?? 0}
              </li>
            ))}
            <li className="tabular-nums">Not promoted {summary.notPromoted}</li>
          </ul>
          <div className="mt-1 text-sm text-gray-600">Releases per stage</div>
        </div>
      </div>
      <p className="mt-2 text-xs text-gray-500">Over {summary.visibleReleases} visible releases{scope ? ` in ${scope}` : ''}.</p>
    </section>
  );
}

function StoryPointsCell({ r }: { r: DashboardReleaseRow }) {
  const { tickets } = r.composition;
  const { total, unestimated, unread } = r.storyPoints;
  if (tickets === 0) {
    return <span className="text-gray-400">—</span>;
  }
  if (unread === tickets) {
    return <span className="italic text-gray-400">Unknown</span>;
  }
  const notes = [unestimated > 0 ? `${unestimated} unestimated` : null, unread > 0 ? `${unread} not read` : null].filter(
    Boolean,
  );
  return (
    <>
      <div>{total.toLocaleString(undefined, { maximumFractionDigits: 2 })} pts</div>
      {notes.length > 0 && <div className="mt-1 text-xs text-gray-500">{notes.join(' · ')}</div>}
    </>
  );
}

function CodeLinksCell({ r }: { r: DashboardReleaseRow }) {
  const c = r.composition;
  if (c.tickets === 0) {
    return <span className="italic text-gray-400">No Jira tickets</span>;
  }
  const pct = (n: number) => `${(n / c.tickets) * 100}%`;
  const label = `${c.linked} linked, ${c.noLink} no link, ${c.failed} failed, ${c.noData} no data`;
  return (
    <>
      <div role="img" aria-label={label} className="flex h-2 w-16 overflow-hidden rounded">
        <div className="bg-blue-600" style={{ width: pct(c.linked) }} />
        <div className="bg-gray-300" style={{ width: pct(c.noLink) }} />
        <div className="bg-red-600" style={{ width: pct(c.failed) }} />
        <div className="border border-gray-300 bg-gray-100" style={{ width: pct(c.noData) }} />
      </div>
      <div className="mt-1 text-xs text-gray-700">
        {c.linked} linked · {c.noLink} no link · {c.failed} failed
        {c.noData > 0 ? ` · ${c.noData} no data` : ''}
      </div>
    </>
  );
}

function ChurnCell({ r }: { r: DashboardReleaseRow }) {
  const k = r.churn;
  if (k.medianCommitsToDone === null) {
    return <span className="italic text-gray-400">No data</span>;
  }
  return (
    <>
      <div className="tabular-nums">{oneDecimal(k.medianCommitsToDone)} commits to Done</div>
      {k.keyTagged > 0 && <div className="text-xs text-gray-500">{k.keyTagged} key-tagged only</div>}
      {k.outliers > 0 && (
        <Link to="/releases/$releaseId" params={{ releaseId: r.id }} className="text-xs text-amber-700 hover:underline">
          {k.outliers} outliers
        </Link>
      )}
      {k.commitNoData > 0 && <div className="text-xs text-gray-500">{k.commitNoData} no data</div>}
    </>
  );
}

function ReworkCell({ r }: { r: DashboardReleaseRow }) {
  const k = r.churn;
  return (
    <>
      <div className="tabular-nums">
        {k.reopens} reopens
        {k.postDoneShare !== null ? ` · ${Math.round(k.postDoneShare * 100)}% after Done` : ''}
      </div>
      {k.reopenNoData > 0 && <div className="text-xs text-gray-500">{k.reopenNoData} no data</div>}
    </>
  );
}

function StageCell({ r, data }: { r: DashboardReleaseRow; data: DashboardData }) {
  const current = data.stages.find((s) => s.id === r.currentStageId);
  return (
    <>
      <Link to="/releases/$releaseId" params={{ releaseId: r.id }} hash="history" className="flex flex-col gap-1">
        <LadderDots
          compact
          stages={data.stages}
          currentStageId={r.currentStageId}
          failedStageId={r.lastFailedStageId}
          ariaLabel={`Environment ladder for ${r.name}`}
        />
        {current ? (
          <span className="text-gray-700">
            {current.name}
            {current.retired ? ' (retired)' : ''}
          </span>
        ) : (
          <span className="text-gray-500">Not promoted</span>
        )}
      </Link>
      {r.lastFailedStageId && (
        <span className="mt-1 inline-block rounded border border-red-600 bg-red-50 px-2 py-0.5 text-xs font-semibold text-red-700">
          Last promotion failed
        </span>
      )}
    </>
  );
}

function DurationCell({ seconds, missing }: { seconds: number | null; missing: string }) {
  return seconds === null ? (
    <td className={`${TD} tabular-nums`} title={missing} aria-label={missing}>
      —
    </td>
  ) : (
    <td className={`${TD} tabular-nums`}>{formatDuration(seconds)}</td>
  );
}

function ReleaseMatrix({
  data,
  all,
  stateOf,
  readinessFailed,
  productsFiltered,
  docsFilter,
  onToggleAll,
  onClearProducts,
  onClearDocs,
}: {
  data: DashboardData;
  all: boolean;
  stateOf: ReadinessLookup;
  readinessFailed: boolean;
  productsFiltered: boolean;
  docsFilter: DashboardDocsFilter | undefined;
  onToggleAll: (checked: boolean) => void;
  onClearProducts: () => void;
  onClearDocs: () => void;
}) {
  const { summary, releases } = data;
  const rows = releases.filter((r) =>
    docsFilter === 'missing'
      ? missingDocuments(r.id, stateOf)
      : docsFilter === 'stale'
        ? outOfDateDocuments(r.id, stateOf)
        : true,
  );
  const stagger = useEntranceStagger(rows.length > 0);
  return (
    <section>
      <div className="flex items-center justify-between">
        <div>
          <h3 className="text-base font-semibold text-gray-900">Releases</h3>
          <p className="text-xs text-gray-500">
            {all ? 'All releases.' : 'In-flight releases and those shipped in the last 90 days.'}
          </p>
        </div>
        <label className="flex items-center gap-2 text-sm text-gray-700">
          <input
            type="checkbox"
            checked={all}
            onChange={(event) => onToggleAll(event.target.checked)}
            className="focus-visible:ring-2 focus-visible:ring-blue-600"
          />
          Show all releases
        </label>
      </div>

      {summary.totalReleases === 0 && (
        <div className="mt-4 rounded bg-gray-50 p-6 text-center">
          <p className="text-base font-semibold text-gray-900">No releases yet</p>
          <p className="mt-2 text-sm text-gray-600">
            Releases are created from the ticket browser. Browse tickets, select the ones you want, and choose
            &apos;Add to release&apos; → &apos;New release…&apos;.{' '}
            <Link to="/tickets" className="text-blue-600 underline">
              Browse tickets
            </Link>
          </p>
        </div>
      )}

      {productsFiltered && summary.totalReleases > 0 && releases.length === 0 && (
        <div className="mt-4 rounded bg-gray-50 p-6 text-center">
          <p className="text-base font-semibold text-gray-900">No releases for these products</p>
          <p className="mt-2 text-sm text-gray-600">
            Nothing in the selected products is open, frozen or in a stage short of Production, and nothing shipped in
            the last 90 days.{' '}
            <button type="button" onClick={onClearProducts} className="text-blue-600 underline">
              Clear products
            </button>{' '}
            <button type="button" onClick={() => onToggleAll(true)} className="text-blue-600 underline">
              Show all releases
            </button>
          </p>
        </div>
      )}

      {!productsFiltered && summary.totalReleases > 0 && releases.length === 0 && (
        <div className="mt-4 rounded bg-gray-50 p-6 text-center">
          <p className="text-base font-semibold text-gray-900">No in-flight or recently shipped releases</p>
          <p className="mt-2 text-sm text-gray-600">
            Nothing is open, frozen or in a stage short of Production, and nothing shipped in the last 90 days.{' '}
            <button type="button" onClick={() => onToggleAll(true)} className="text-blue-600 underline">
              Show all releases
            </button>
          </p>
        </div>
      )}

      {docsFilter && (
        <div role="status" className="mt-2 flex flex-wrap items-center gap-2 text-sm">
          <span className="rounded border border-gray-300 bg-gray-100 px-2 py-0.5 text-xs font-semibold text-gray-700">
            Showing: {DOCS_FILTER_LABELS[docsFilter]} · {rows.length} of {releases.length} releases
          </span>
          <button type="button" onClick={onClearDocs} className="text-xs text-blue-600 hover:underline">
            Clear filter
          </button>
        </div>
      )}

      {docsFilter && releases.length > 0 && rows.length === 0 && (
        <div className="mt-4 rounded bg-gray-50 p-6 text-center">
          <p className="text-base font-semibold text-gray-900">No releases match this filter</p>
          <p className="mt-2 text-sm text-gray-600">
            {DOCS_EMPTY_BODIES[docsFilter]}{' '}
            <button type="button" onClick={onClearDocs} className="text-blue-600 underline">
              Clear filter
            </button>
          </p>
        </div>
      )}

      {rows.length > 0 && (
        <div className="mt-4 overflow-x-auto rounded border border-gray-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="border-b border-gray-200">
              <tr>
                <th scope="col" rowSpan={2} className={`${TH} sticky left-0 bg-white`}>
                  Release
                </th>
                <th scope="colgroup" colSpan={3} className={`${TH} text-center`}>
                  Documents
                </th>
                {['Stage', 'Created → frozen', 'Frozen → production', 'Composition', 'Story points', 'Code links', 'Development churn', 'Testing rework'].map(
                  (heading) => (
                    <th key={heading} scope="col" rowSpan={2} className={TH}>
                      {heading}
                    </th>
                  ),
                )}
              </tr>
              <tr>
                {DOCUMENT_TYPES.map((type) => (
                  <th key={type} scope="col" className={TH}>
                    {DOCUMENT_TYPE_LABELS[type]}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rows.map((r, index) => (
                <tr
                  key={r.id}
                  className={cn('border-b border-gray-100 hover:bg-gray-50', stagger(index).className)}
                  style={stagger(index).style}
                >
                  <td className={`${TD} sticky left-0 bg-white`}>
                    <Link
                      to="/releases/$releaseId"
                      params={{ releaseId: r.id }}
                      className="font-semibold text-blue-600 hover:underline"
                    >
                      {r.name}
                    </Link>
                    <div className="text-xs text-gray-500">{r.version}</div>
                  </td>
                  {DOCUMENT_TYPES.map((type) => (
                    <td key={type} className={TD}>
                      {readinessFailed ? (
                        <span className="text-gray-400">—</span>
                      ) : (
                        <ReadinessPill
                          state={stateOf(r.id, type) ?? 'NOT_GENERATED'}
                          releaseId={r.id}
                          documentType={type}
                        />
                      )}
                    </td>
                  ))}
                  <td className={TD}>
                    <StageCell r={r} data={data} />
                  </td>
                  <DurationCell seconds={r.createdToFrozenSeconds} missing="Not frozen yet" />
                  <DurationCell seconds={r.frozenToProductionSeconds} missing="Not in production yet" />
                  <td className={`${TD} tabular-nums`}>
                    {r.composition.tickets} tickets · {r.composition.manual} manual
                  </td>
                  <td className={`${TD} tabular-nums`} title={STORY_POINTS_TITLE}>
                    <StoryPointsCell r={r} />
                  </td>
                  <td className={TD} title={CODE_LINKS_TITLE(r.composition.manual)}>
                    <CodeLinksCell r={r} />
                  </td>
                  <td className={TD} title={CHURN_TITLE} aria-label={CHURN_TITLE}>
                    <ChurnCell r={r} />
                  </td>
                  <td className={TD} title={REWORK_TITLE} aria-label={REWORK_TITLE}>
                    <ReworkCell r={r} />
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot className="border-t border-gray-200">
              <tr>
                <th scope="row" colSpan={5} className={`${TH} sticky left-0 bg-white`}>
                  Median (visible)
                </th>
                <td className={`${TD} tabular-nums`}>{formatDuration(summary.medianCreatedToFrozenSeconds)}</td>
                <td className={`${TD} tabular-nums`}>{formatDuration(summary.medianFrozenToProductionSeconds)}</td>
                <td colSpan={5} />
              </tr>
            </tfoot>
          </table>
        </div>
      )}

      <div className="mt-2 space-y-1 text-xs text-gray-500">
        <p>
          Churn figures describe this release&apos;s code and test activity from Azure DevOps commits and Jira
          status history. They are not Jira cycle time.
        </p>
        <p>
          Code-link counts cover Jira-backed tickets only; manual items are excluded. Medians and counts are
          computed over the releases shown.
        </p>
      </div>
    </section>
  );
}

export function DashboardRoute() {
  const search = useSearch({ from: '/authenticated/' });
  const navigate = useNavigate();
  const all = search.all === true;
  const tab: DashboardTab = search.tab ?? 'releases';
  const productIds = search.products ?? [];
  const products = useProducts().data?.products ?? [];
  const [announcement, setAnnouncement] = useState('');

  const dashboard = useDashboardReleases(all, productIds);
  // Independent on purpose: a readiness failure only blanks the document cells (T-05-60).
  const readiness = useDocumentReadiness();
  const stateOf = useMemo(() => readinessIndex(readiness.data?.entries), [readiness.data]);

  const problem = dashboard.isError && dashboard.error instanceof ApiError ? dashboard.error.problem : undefined;
  const counts = dashboard.data && !readiness.isError
    ? paperworkCounts(dashboard.data.releases.map((r) => r.id), stateOf)
    : undefined;

  const docsFilter = readiness.isError ? undefined : search.docs;
  const setDocs = (docs: DashboardDocsFilter | undefined) =>
    navigate({ to: '/', search: (prev) => ({ ...(prev as DashboardSearch), docs }), replace: true });
  const clearDocs = () => {
    setDocs(undefined);
    if (docsFilter) document.getElementById(DOCS_FILTER_TILE_IDS[docsFilter])?.focus();
  };

  const setProducts = (ids: string[]) => {
    const scope = productScope(ids, products);
    setAnnouncement(scope ? `Filtered to ${scope}.` : 'Showing all products.');
    navigate({
      to: '/',
      search: (prev) => ({ ...(prev as DashboardSearch), products: ids.length ? ids : undefined }),
      replace: true,
    });
  };

  const goTab = (next: DashboardTab) =>
    navigate({
      to: '/',
      search: (prev) => ({ ...(prev as DashboardSearch), tab: next === 'portfolio' ? 'portfolio' : undefined, docs: undefined }),
      replace: true,
    });
  const onTabKeyDown = (event: React.KeyboardEvent, index: number) => {
    if (event.key !== 'ArrowRight' && event.key !== 'ArrowLeft') return;
    event.preventDefault();
    const next = TABS[(index + (event.key === 'ArrowRight' ? 1 : TABS.length - 1)) % TABS.length];
    goTab(next.id);
    document.getElementById(`dashboard-tab-${next.id}`)?.focus();
  };

  return (
    <div className="space-y-8">
      <h2 className="text-base font-semibold text-gray-900">Dashboard</h2>

      <ProductFilter products={products} selected={productIds} onChange={setProducts} />
      <p role="status" aria-live="polite" aria-atomic="true" className="sr-only">
        {announcement}
      </p>

      <div role="tablist" aria-label="Dashboard views" className="flex gap-4 border-b border-gray-200">
        {TABS.map((t, index) => {
          const active = t.id === tab;
          return (
            <button
              key={t.id}
              id={`dashboard-tab-${t.id}`}
              type="button"
              role="tab"
              aria-selected={active}
              aria-controls={`dashboard-panel-${t.id}`}
              tabIndex={active ? 0 : -1}
              onClick={() => goTab(t.id)}
              onKeyDown={(e) => onTabKeyDown(e, index)}
              className={`px-1 pb-2 text-sm ${
                active
                  ? '-mb-px border-b-2 border-blue-600 font-semibold text-gray-900'
                  : 'text-gray-600 hover:text-gray-900'
              }`}
            >
              {t.label}
            </button>
          );
        })}
      </div>

      <div role="tabpanel" id={`dashboard-panel-${tab}`} aria-labelledby={`dashboard-tab-${tab}`} className="space-y-8">
      {tab === 'portfolio' ? (
        <PortfolioTab
          productIds={productIds}
          archived={search.archived === true}
          onProductsChange={setProducts}
          onArchivedChange={(checked) =>
            navigate({
              to: '/',
              search: (prev) => ({ ...(prev as DashboardSearch), archived: checked ? true : undefined }),
              replace: true,
            })
          }
        />
      ) : (
      <>
      {dashboard.isPending && (
        <div className="flex items-center gap-3">
          <Spinner />
          <p className="text-sm text-gray-600">Loading dashboard…</p>
        </div>
      )}

      {dashboard.isError && (
        <div>
          <p role="alert" data-reason={problem?.reason} className="text-sm text-red-600">
            Couldn&apos;t load the dashboard — {problem ? messageForReason(problem).replace(/\.$/, '') : 'something went wrong'}. Try again.
          </p>
          <button
            type="button"
            onClick={() => dashboard.refetch()}
            className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700"
          >
            Try again
          </button>
        </div>
      )}

      {dashboard.data && (
        <>
          <SummaryStrip
            data={dashboard.data}
            counts={counts}
            scope={productScope(productIds, products)}
            docs={docsFilter}
            onToggleDocs={(kind) => setDocs(search.docs === kind ? undefined : kind)}
          />
          <ReleaseMatrix
            data={dashboard.data}
            all={all}
            stateOf={stateOf}
            readinessFailed={readiness.isError}
            productsFiltered={productIds.length > 0}
            docsFilter={docsFilter}
            onClearDocs={clearDocs}
            onClearProducts={() => setProducts([])}
            onToggleAll={(checked) =>
              navigate({ to: '/', search: (prev) => ({ ...(prev as DashboardSearch), all: checked ? true : undefined }), replace: true })
            }
          />
        </>
      )}

      <DeploymentHistory
        search={search}
        onSearchChange={(next) => navigate({ to: '/', search: next, replace: true })}
      />
      </>
      )}
      </div>
    </div>
  );
}
