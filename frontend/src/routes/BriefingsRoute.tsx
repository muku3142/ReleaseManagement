import { useState } from 'react';
import { useNavigate, useSearch } from '@tanstack/react-router';
import {
  useBriefings,
  type AppBriefingRun,
  type BriefingStatus,
  type BriefingTaskFeed,
} from '../api/briefings';
import { useProducts } from '../api/products';
import { AppFallbackSection, CREDENTIAL_COPY_BEFORE, SettingsBriefingsLink } from '../briefings/AppFallbackSection';
import { attentionText, formatCairo, worstStatus } from '../briefings/briefingFormat';
import { IssueKeyText, useIssueLinkContext } from '../briefings/IssueKeyText';
import { StatusPill } from '../briefings/StatusPill';
import { PRODUCT_CHIP } from '../components/ProductsEditor';
import { ProductFilter } from '../components/ProductFilter';
import { SectionError, SectionLoading } from '../components/PortfolioStates';

const PAGE_SIZE = 50;
const JIRA_TASKS = new Set(['jira_digest', 'sprint_check', 'weekly_status']);
const NO_ATTENTION_COUNT: BriefingStatus[] = ['skipped', 'needs_credential'];

type LinkContext = { knownKeys: string[]; baseUrl?: string };

type HistoryRow = {
  key: string;
  taskName: string;
  source: 'claude' | 'app';
  productName: string | null;
  runAt: string;
  status: BriefingStatus;
  headline: string | null;
  attention: number | null;
  linked: boolean;
};

// Claude's COVERED markers are history-only; the card never shows them.
function cardApp(task: BriefingTaskFeed, selected: string[]): AppBriefingRun[] {
  if (!(task.appFallback ?? JIRA_TASKS.has(task.id))) return [];
  return (task.appLatest ?? []).filter(
    (r) => r.reason !== 'CLAUDE_COVERED' && (selected.length === 0 || selected.includes(r.productId)),
  );
}

function cardKind(task: BriefingTaskFeed, app: AppBriefingRun[]) {
  const worst = worstStatus([...(task.latest ? [task.latest.status] : []), ...app.map((r) => r.status)]);
  if (worst) return worst;
  return task.readError ? 'unreadable' : 'not_run';
}

function cardAttention(task: BriefingTaskFeed, app: AppBriefingRun[]): number {
  return (task.latest?.attention ?? 0) + app.reduce((sum, r) => sum + (r.attention ?? 0), 0);
}

function timeOf(iso: string): number {
  const time = Date.parse(iso);
  return Number.isNaN(time) ? -Infinity : time;
}

function summaryLine(cards: { kind: string }[], rows: HistoryRow[]): string {
  const latest = rows.find((r) => timeOf(r.runAt) !== -Infinity);
  if (!latest) return 'No briefings have run yet';
  const count = (kind: string) => cards.filter((c) => c.kind === kind).length;
  const attention = count('attention');
  const failed = count('failed');
  const credential = count('needs_credential');
  const lead =
    attention === 0
      ? 'No briefing needs attention'
      : attention === 1
        ? `1 of ${cards.length} briefings needs attention`
        : `${attention} of ${cards.length} briefings need attention`;
  return (
    `${lead} · latest run ${formatCairo(latest.runAt)} Cairo` +
    (failed > 0 ? ` · ${failed} failed` : '') +
    (credential > 0 ? ` · ${credential} need a credential` : '')
  );
}

function historyRows(tasks: BriefingTaskFeed[], selected: string[]): HistoryRow[] {
  return tasks
    .flatMap((task) => [
      ...task.runs.map(
        (run, i): HistoryRow => ({
          key: `${task.id}-claude-${run.runAt}-${i}`,
          taskName: task.name,
          source: 'claude',
          productName: null,
          runAt: run.runAt,
          status: run.status,
          headline: run.headline,
          attention: run.attention,
          linked: JIRA_TASKS.has(task.id),
        }),
      ),
      ...(task.appRuns ?? [])
        .filter((r) => selected.length === 0 || selected.includes(r.productId))
        .map(
          (r, i): HistoryRow => ({
            key: `${task.id}-${r.productId}-${r.runAt}-${i}`,
            taskName: task.name,
            source: 'app',
            productName: r.productName,
            runAt: r.runAt,
            status: r.status,
            headline: r.headline,
            attention: r.attention,
            linked: true,
          }),
        ),
    ])
    .sort((a, b) => timeOf(b.runAt) - timeOf(a.runAt));
}

function ClaudeResult({ task, link }: { task: BriefingTaskFeed; link: LinkContext }) {
  const run = task.latest;
  if (!run) {
    return task.readError ? (
      <p className="text-xs text-amber-700">Couldn&apos;t read this briefing&apos;s file</p>
    ) : (
      <p className="text-sm text-gray-600">No result yet. This briefing runs {task.schedule}.</p>
    );
  }
  const linked = JIRA_TASKS.has(task.id);
  const text = (value: string) => (linked ? <IssueKeyText text={value} {...link} /> : value);
  const bullets = [run.h1, run.h2, run.h3].filter((b): b is string => b !== null);
  return (
    <div className="space-y-2">
      <p className="flex flex-wrap items-center gap-2 text-xs text-gray-600">
        <span className={PRODUCT_CHIP}>Claude</span>
        <span>Last run {formatCairo(run.runAt)} Cairo</span>
      </p>
      {run.headline && <p className="text-sm font-semibold text-gray-900">{text(run.headline)}</p>}
      {run.status === 'failed' && <p className="text-sm text-gray-700">Claude&apos;s last run for this briefing failed.</p>}
      {bullets.length > 0 && (
        <ul className="list-disc space-y-1 pl-5 text-sm text-gray-700">
          {bullets.map((b, i) => (
            <li key={i}>{text(b)}</li>
          ))}
        </ul>
      )}
    </div>
  );
}

function BriefingCard({
  task,
  app,
  link,
}: {
  task: BriefingTaskFeed;
  app: AppBriefingRun[];
  link: LinkContext;
}) {
  const attention = cardAttention(task, app);
  return (
    <section
      aria-labelledby={`briefing-${task.id}-name`}
      data-testid={`briefing-card-${task.id}`}
      className="space-y-4 rounded border border-gray-200 bg-white p-4"
    >
      <div className="flex flex-wrap items-start justify-between gap-2">
        <div>
          <h3 id={`briefing-${task.id}-name`} className="text-base font-semibold text-gray-900">
            {task.name}
          </h3>
          <p className="text-xs text-gray-600">{task.schedule}</p>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <StatusPill kind={cardKind(task, app)} />
          {attention > 0 && <span className="text-xs tabular-nums text-gray-600">{attentionText(attention)}</span>}
        </div>
      </div>
      {task.readError && task.latest && <p className="text-xs text-amber-700">Couldn&apos;t read this briefing&apos;s file</p>}
      <ClaudeResult task={task} link={link} />
      <AppFallbackSection runs={app} link={link} />
    </section>
  );
}

function RunHistory({
  rows,
  filtered,
  onClear,
  link,
}: {
  rows: HistoryRow[];
  filtered: boolean;
  onClear: () => void;
  link: LinkContext;
}) {
  const [shown, setShown] = useState(PAGE_SIZE);
  if (rows.length === 0) {
    return filtered ? (
      <div className="rounded bg-gray-50 p-6 text-center">
        <p className="text-sm font-semibold text-gray-900">No runs match this filter</p>
        <p className="text-sm text-gray-600">Choose different products. Claude&apos;s runs are always shown.</p>
        <button type="button" onClick={onClear} className="text-sm text-blue-600 hover:underline">
          Clear filter
        </button>
      </div>
    ) : (
      <div className="rounded bg-gray-50 p-6 text-center">
        <p className="text-sm font-semibold text-gray-900">No runs yet</p>
        <p className="text-sm text-gray-600">Runs appear here after a briefing&apos;s first scheduled run.</p>
      </div>
    );
  }
  const visible = rows.slice(0, shown);
  return (
    <div className="space-y-3">
      <div className="overflow-x-auto rounded border border-gray-200 bg-white">
        <table className="min-w-full text-sm">
          <caption className="sr-only">Run history, newest first</caption>
          <thead>
            <tr>
              {['Task', 'Product', 'Source', 'Run (Cairo)', 'Status', 'Headline', 'Needs attention'].map((h) => (
                <th key={h} scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {visible.map((row) => (
              <tr key={row.key} data-source={row.source} className="border-b border-gray-100 hover:bg-gray-50">
                <td className="px-4 py-2 font-semibold text-gray-900">{row.taskName}</td>
                {row.productName === null ? (
                  <td className="px-4 py-2 text-gray-400" title="Claude results are not split by product">
                    —
                  </td>
                ) : (
                  <td className="px-4 py-2">{row.productName}</td>
                )}
                <td className="px-4 py-2">
                  <span className={PRODUCT_CHIP}>{row.source === 'claude' ? 'Claude' : 'App fallback'}</span>
                </td>
                <td className="px-4 py-2 tabular-nums">
                  <time dateTime={row.runAt} title={row.runAt}>
                    {formatCairo(row.runAt)}
                  </time>
                </td>
                <td className="px-4 py-2">
                  <StatusPill kind={row.status} />
                </td>
                <td className="min-w-[16rem] px-4 py-2 text-gray-700">
                  {row.headline !== null && row.linked ? <IssueKeyText text={row.headline} {...link} /> : row.headline}
                </td>
                <td className="px-4 py-2 tabular-nums">
                  {row.attention === null || NO_ATTENTION_COUNT.includes(row.status) ? '—' : row.attention}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="flex items-center gap-3">
        {shown < rows.length && (
          <button
            type="button"
            onClick={() => setShown((n) => n + PAGE_SIZE)}
            className="rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700 focus-visible:ring-2 focus-visible:ring-blue-600"
          >
            Show more
          </button>
        )}
        <p className="text-xs text-gray-600">
          Showing {visible.length} of {rows.length} runs.
        </p>
      </div>
    </div>
  );
}

export function BriefingsRoute() {
  const q = useBriefings();
  return (
    <div className="space-y-8">
      <h2 className="text-base font-semibold text-gray-900">Briefings</h2>
      {q.isPending ? (
        <SectionLoading text="Loading briefings…" />
      ) : q.isError ? (
        <SectionError what="the briefings" error={q.error} onRetry={() => q.refetch()} />
      ) : (
        <Briefings tasks={q.data.tasks} />
      )}
    </div>
  );
}

function Briefings({ tasks }: { tasks: BriefingTaskFeed[] }) {
  const search: { products?: string[] } = useSearch({ from: '/authenticated/briefings' });
  const navigate = useNavigate();
  const products = useProducts().data?.products ?? [];
  const link = useIssueLinkContext();
  const selected = search.products ?? [];
  const setProducts = (ids: string[]) =>
    navigate({ to: '/briefings', search: { products: ids.length > 0 ? ids : undefined }, replace: true });

  const cards = tasks.map((task) => {
    const app = cardApp(task, selected);
    return { task, app, kind: cardKind(task, app) };
  });
  const rows = historyRows(tasks, selected);
  const needsCredential = cards.some((c) => c.app.some((r) => r.status === 'needs_credential'));
  const unfiltered = selected.length > 0 && historyRows(tasks, []).length > 0;

  return (
    <>
      <p className="text-sm text-gray-600">{summaryLine(cards, rows)}</p>
      {products.length > 0 && (
        <div className="flex flex-wrap items-end gap-3">
          <ProductFilter products={products} selected={selected} onChange={setProducts} />
          <p className="text-xs text-gray-600">Claude results are not split by product and always show.</p>
        </div>
      )}
      {needsCredential && (
        <p role="status" className="rounded border border-amber-600 bg-amber-50 p-3 text-sm text-amber-700">
          {CREDENTIAL_COPY_BEFORE}
          <SettingsBriefingsLink />.
        </p>
      )}
      <div className="grid gap-4 lg:grid-cols-2">
        {cards.map(({ task, app }) => (
          <BriefingCard key={task.id} task={task} app={app} link={link} />
        ))}
      </div>
      <div className="space-y-3">
        <h3 className="text-base font-semibold text-gray-900">Run history</h3>
        <RunHistory rows={rows} filtered={unfiltered} onClear={() => setProducts([])} link={link} />
      </div>
    </>
  );
}
