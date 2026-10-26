import { Link } from '@tanstack/react-router';
import { usePortfolioSummary, type PortfolioProductRow, type PortfolioSummary, type StatusCounts } from '../api/dashboard';
import { useProducts } from '../api/products';
import { HelpToggle, Tile } from './DashboardTiles';
import { PortfolioBehind } from './PortfolioBehind';
import { PortfolioMatrix } from './PortfolioMatrix';
import { SectionError, SectionLoading } from './PortfolioStates';

const TH = 'px-4 py-2 text-left font-semibold text-gray-700';
const TD = 'whitespace-nowrap px-4 py-2 align-top';
const ARCHIVED_TAG = 'rounded border border-gray-300 bg-gray-100 px-2 py-0.5 text-xs text-gray-600';
const TILE_BUTTON =
  'w-full rounded border border-gray-200 bg-white p-4 text-left hover:bg-gray-50 focus-visible:ring-2 focus-visible:ring-blue-600';

const BEHIND_HELP =
  'A client is behind when a newer frozen release of that product exists than the one it runs. Newest is by frozen date, not by version.';
const NOT_INSTALLED_HELP =
  'Clients linked to a product with no successful install recorded. They are not counted as behind.';
const INSTALLS_HELP =
  'Successful installs recorded in the last 30 days. Voided and superseded installs are not counted.';

function focusSection(id: string) {
  const el = document.getElementById(id);
  if (!el) return;
  el.focus();
  el.scrollIntoView({ block: 'start' });
}

function stop(event: React.SyntheticEvent) {
  event.stopPropagation();
}

function ActionTile({
  id,
  caption,
  figure,
  unit,
  help,
  targetId,
}: {
  id: string;
  caption: string;
  figure: number;
  unit: string;
  help: string;
  targetId: string;
}) {
  return (
    <div className="relative">
      <button id={id} type="button" aria-describedby={`${id}-unit`} onClick={() => focusSection(targetId)} className={TILE_BUTTON}>
        <span className="block text-lg font-semibold tabular-nums text-gray-900">{figure}</span>
        <span className="block text-sm text-gray-600">{caption}</span>
        <span id={`${id}-unit`} className="block text-xs text-gray-500">
          {unit}
        </span>
      </button>
      <HelpToggle id={`${id}-help`} text={help} />
    </div>
  );
}

function PortfolioTiles({ tiles }: { tiles: PortfolioSummary['tiles'] }) {
  return (
    <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
      <ActionTile
        id="tile-behind"
        caption="Clients behind"
        figure={tiles.clientsBehind}
        unit="client/product pairs"
        help={BEHIND_HELP}
        targetId="behind-heading"
      />
      <ActionTile
        id="tile-not-installed"
        caption="Not installed"
        figure={tiles.notInstalled}
        unit="linked, nothing installed"
        help={NOT_INSTALLED_HELP}
        targetId="not-installed-heading"
      />
      <Tile caption="Active products / clients" unit="products / clients">
        {tiles.activeProducts} / {tiles.activeClients}
      </Tile>
      <div className="relative">
        <Tile caption="Installs last 30 days" unit="successful installs">
          {tiles.installsLast30Days}
        </Tile>
        <HelpToggle id="tile-installs-help" text={INSTALLS_HELP} />
      </div>
    </div>
  );
}

function ClientsOnNewest({ p }: { p: PortfolioProductRow }) {
  if (p.newestFrozen === null || p.linkedClients === 0) return <span className="text-gray-400">—</span>;
  return (
    <>
      <span className="tabular-nums">
        {p.clientsOnNewest} of {p.linkedClients} clients
      </span>
      {p.clientsOnNewest === p.linkedClients && <div className="text-xs text-green-700">all up to date</div>}
    </>
  );
}

function ProductRow({
  p,
  soleSelected,
  onToggle,
}: {
  p: PortfolioProductRow;
  soleSelected: boolean;
  onToggle: (id: string) => void;
}) {
  const brief = p.newestFrozen;
  return (
    <tr
      onClick={() => onToggle(p.id)}
      className={`cursor-pointer border-b border-gray-100 hover:bg-gray-50${soleSelected ? ' bg-gray-100' : ''}`}
    >
      <td className={TD}>
        <button
          type="button"
          aria-pressed={soleSelected}
          aria-label={`Filter dashboard to ${p.name}`}
          onClick={(event) => {
            stop(event);
            onToggle(p.id);
          }}
          className={`hover:underline focus-visible:ring-2 focus-visible:ring-blue-600 ${p.archived ? 'text-gray-500' : 'text-gray-900'}`}
        >
          {p.name}
        </button>
        {p.archived && <span className={`ml-2 ${ARCHIVED_TAG}`}>archived</span>}
      </td>
      <td className={`${TD} tabular-nums`}>{p.releases.open}</td>
      <td className={`${TD} tabular-nums`}>{p.releases.frozen}</td>
      <td className={`${TD} tabular-nums`}>{p.releases.inProduction}</td>
      <td className={TD}>
        {brief ? (
          <>
            <Link
              to="/releases/$releaseId"
              params={{ releaseId: brief.releaseId }}
              onClick={stop}
              className="font-semibold text-blue-600 hover:underline"
            >
              {brief.name} {brief.version}
            </Link>
            <div className="text-xs text-gray-600" title={new Date(brief.frozenAt).toLocaleString()}>
              {new Date(brief.frozenAt).toLocaleDateString()}
            </div>
          </>
        ) : (
          <span className="text-gray-400" title="No frozen release yet">
            —
          </span>
        )}
      </td>
      <td className={TD}>
        <ClientsOnNewest p={p} />
      </td>
    </tr>
  );
}

function CountsRow({ label, counts, muted }: { label: string; counts: StatusCounts; muted?: boolean }) {
  return (
    <tr>
      <th scope="row" className={`${TH}${muted ? ' text-gray-500' : ''}`}>
        {label}
      </th>
      <td className={`${TD} tabular-nums`}>{counts.open}</td>
      <td className={`${TD} tabular-nums`}>{counts.frozen}</td>
      <td className={`${TD} tabular-nums`}>{counts.inProduction}</td>
      <td colSpan={2} />
    </tr>
  );
}

function ProductsTable({
  summary,
  productIds,
  onProductsChange,
}: {
  summary: PortfolioSummary;
  productIds: string[];
  onProductsChange: (ids: string[]) => void;
}) {
  const soleId = productIds.length === 1 ? productIds[0] : undefined;
  const toggle = (id: string) => onProductsChange(soleId === id ? [] : [id]);
  return (
    <section aria-labelledby="products-heading">
      <h3 id="products-heading" className="text-base font-semibold text-gray-900">
        Products
      </h3>
      <p className="text-xs text-gray-500">
        Releases per product. A release that covers several products appears in each product&apos;s row; totals
        elsewhere count it once.
      </p>
      <div className="mt-4 overflow-x-auto rounded border border-gray-200 bg-white">
        <table className="min-w-full text-sm">
          <thead className="border-b border-gray-200">
            <tr>
              {['Product', 'Open', 'Frozen', 'In Production', 'Newest frozen', 'Clients on newest'].map((h) => (
                <th key={h} scope="col" className={TH}>
                  {h}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {summary.products.map((p) => (
              <ProductRow key={p.id} p={p} soleSelected={soleId === p.id} onToggle={toggle} />
            ))}
            {summary.unassigned && <CountsRow label="Unassigned" counts={summary.unassigned} muted />}
          </tbody>
          <tfoot className="border-t border-gray-200">
            <CountsRow label={productIds.length > 0 ? 'All selected products' : 'All products'} counts={summary.totals} />
          </tfoot>
        </table>
      </div>
    </section>
  );
}

type Props = {
  productIds: string[];
  archived: boolean;
  onProductsChange: (ids: string[]) => void;
  onArchivedChange: (archived: boolean) => void;
};

export function PortfolioTab({ productIds, archived, onProductsChange, onArchivedChange }: Props) {
  const products = useProducts();
  const summary = usePortfolioSummary(productIds, archived);
  const noProducts = products.data !== undefined && products.data.products.length === 0;

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between gap-4">
        <p className="text-sm text-gray-600">
          What each client runs, and who is behind the newest frozen release of each product. Installs are recorded
          from a release&apos;s page.
        </p>
        <label className="flex shrink-0 items-center gap-2 text-sm text-gray-700">
          <input
            type="checkbox"
            checked={archived}
            onChange={(event) => onArchivedChange(event.target.checked)}
            className="focus-visible:ring-2 focus-visible:ring-blue-600"
          />
          Show archived
        </label>
      </div>

      {summary.isPending && <SectionLoading text="Loading portfolio…" />}
      {summary.isError && <SectionError what="the portfolio" error={summary.error} onRetry={() => summary.refetch()} />}

      {noProducts && (
        <div className="rounded bg-gray-50 p-6 text-center">
          <p className="text-base font-semibold text-gray-900">No products yet</p>
          <p className="mt-2 text-sm text-gray-600">
            Add products and clients on the Portfolio page, then record installs from a frozen release.{' '}
            <Link to="/portfolio" className="text-blue-600 underline">
              Portfolio page
            </Link>
          </p>
        </div>
      )}

      {summary.data && !noProducts && <PortfolioTiles tiles={summary.data.tiles} />}
      {summary.data && !noProducts && (
        <ProductsTable summary={summary.data} productIds={productIds} onProductsChange={onProductsChange} />
      )}
      {!noProducts && <PortfolioBehind productIds={productIds} archived={archived} />}
      {!noProducts && (
        <PortfolioMatrix productIds={productIds} archived={archived} onProductsChange={onProductsChange} />
      )}
    </div>
  );
}
