import { Link } from '@tanstack/react-router';
import { usePortfolioBehind, type BehindRow, type NotInstalledRow, type ReleaseBrief } from '../api/dashboard';
import { SectionError, SectionLoading } from './PortfolioStates';

const TH = 'px-4 py-2 text-left font-semibold text-gray-700';
const TD = 'whitespace-nowrap px-4 py-2 align-top';
const ARCHIVED_TAG = 'rounded border border-gray-300 bg-gray-100 px-2 py-0.5 text-xs text-gray-600';
const AMBER_MARKER = 'rounded border border-amber-600 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-700';

function behindLabel(n: number): string {
  return n === 1 ? '1 release behind' : `${n} releases behind`;
}

function ReleaseLink({ release }: { release: Pick<ReleaseBrief, 'releaseId' | 'name' | 'version'> }) {
  return (
    <Link
      to="/releases/$releaseId"
      params={{ releaseId: release.releaseId }}
      className="font-semibold text-blue-600 hover:underline"
    >
      {release.name} {release.version}
    </Link>
  );
}

function DateLine({ iso }: { iso: string }) {
  return (
    <div className="text-xs text-gray-600" title={new Date(iso).toLocaleString()}>
      {new Date(iso).toLocaleDateString()}
    </div>
  );
}

function ClientLink({ id, name, archived }: { id: string; name: string; archived: boolean }) {
  return (
    <>
      <Link
        to="/portfolio"
        search={{ tab: 'clients', client: id }}
        className={`font-semibold hover:underline ${archived ? 'text-gray-500' : 'text-blue-600'}`}
      >
        {name}
      </Link>
      {archived && <span className={`ml-2 ${ARCHIVED_TAG}`}>archived</span>}
    </>
  );
}

function BehindTable({ rows }: { rows: BehindRow[] }) {
  return (
    <div className="mt-4 overflow-x-auto rounded border border-gray-200 bg-white">
      <table className="min-w-full text-sm">
        <thead className="border-b border-gray-200">
          <tr>
            {['Client', 'Product', 'Runs', 'Behind', 'Newest'].map((h) => (
              <th key={h} scope="col" className={TH}>
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={`${r.clientId}:${r.productId}`} className="border-b border-gray-100 hover:bg-gray-50">
              <td className={TD}>
                <ClientLink id={r.clientId} name={r.clientName} archived={r.clientArchived} />
              </td>
              <td className={TD}>{r.productName}</td>
              <td className={TD}>
                <ReleaseLink release={r.runs} />
                {!r.runs.inProduction && <span className={`ml-2 text-xs text-amber-700`}>not in Production</span>}
                <DateLine iso={r.runs.installedAt} />
              </td>
              <td className={TD}>
                <span className={AMBER_MARKER}>{behindLabel(r.releasesBehind)}</span>
                <span className="sr-only">
                  {behindLabel(r.releasesBehind)}: newest is {r.newest.name}
                </span>
              </td>
              <td className={TD}>
                <ReleaseLink release={r.newest} />
                <DateLine iso={r.newest.frozenAt} />
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function NotInstalledGroup({ rows }: { rows: NotInstalledRow[] }) {
  return (
    <div className="mt-6" id="not-installed">
      <h4 id="not-installed-heading" tabIndex={-1} className="text-sm font-semibold text-gray-900">
        Not installed ({rows.length})
      </h4>
      <p className="text-xs text-gray-500">Linked to the product, nothing installed yet. Not counted as behind.</p>
      <ul className="mt-2 divide-y divide-gray-100">
        {rows.map((r) => (
          <li key={`${r.clientId}:${r.productId}`} className="flex flex-wrap items-center gap-2 py-2 text-sm">
            <span>
              <ClientLink id={r.clientId} name={r.clientName} archived={r.clientArchived} />
            </span>
            <span aria-hidden="true">·</span>
            <span>{r.productName}</span>
            <span className="grow" />
            {r.newest ? (
              <span className="text-xs text-gray-600">
                Newest available: <ReleaseLink release={r.newest} />
              </span>
            ) : (
              <span className="text-xs text-gray-400">No frozen release yet</span>
            )}
          </li>
        ))}
      </ul>
    </div>
  );
}

export function PortfolioBehind({ productIds, archived }: { productIds: string[]; archived: boolean }) {
  const behind = usePortfolioBehind(productIds, archived);
  const data = behind.data;
  return (
    <section id="behind">
      <h3 id="behind-heading" tabIndex={-1} className="text-base font-semibold text-gray-900">
        Clients behind
      </h3>
      <p className="text-xs text-gray-500">
        Compared with the newest frozen release of each product, whether or not it has reached Production.
      </p>

      {behind.isPending && (
        <div className="mt-4">
          <SectionLoading text="Loading clients behind…" />
        </div>
      )}
      {behind.isError && (
        <div className="mt-4">
          <SectionError what="clients behind" error={behind.error} onRetry={() => behind.refetch()} />
        </div>
      )}

      {data && data.behind.length > 0 && <BehindTable rows={data.behind} />}
      {data && data.behind.length === 0 && (
        <div className="mt-4 rounded bg-gray-50 p-6 text-center">
          <p className="text-base font-semibold text-gray-900">Everyone is up to date</p>
          <p className="mt-2 text-sm text-gray-600">
            Every client that has an install runs the newest frozen release of its product.
          </p>
        </div>
      )}
      {data && data.notInstalled.length > 0 && <NotInstalledGroup rows={data.notInstalled} />}
    </section>
  );
}
