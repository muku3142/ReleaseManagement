import { Link } from '@tanstack/react-router';
import { usePortfolioMatrix, type MatrixCell, type MatrixColumn } from '../api/dashboard';
import { SectionError, SectionLoading } from './PortfolioStates';

const TH = 'px-4 py-2 text-left font-semibold text-gray-700';
const ARCHIVED_TAG = 'rounded border border-gray-300 bg-gray-100 px-2 py-0.5 text-xs text-gray-600';
const AMBER_MARKER = 'rounded border border-amber-600 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-700';

function behindLabel(n: number): string {
  return n === 1 ? '1 release behind' : `${n} releases behind`;
}

function Cell({ cell, product }: { cell: MatrixCell | undefined; product: MatrixColumn }) {
  if (!cell) {
    return (
      <span className="text-gray-400" aria-label={`Not linked to ${product.name}`}>
        —
      </span>
    );
  }
  const { run, newest, releasesBehind, upToDate } = cell;
  if (!run) return <span className="text-gray-500">Not installed</span>;
  return (
    <div className="space-y-1">
      <Link
        to="/releases/$releaseId"
        params={{ releaseId: run.releaseId }}
        className="font-semibold text-blue-600 hover:underline"
      >
        {run.name} {run.version}
      </Link>
      <div className="text-xs text-gray-600" title={new Date(run.installedAt).toLocaleString()}>
        Installed {new Date(run.installedAt).toLocaleDateString()}
      </div>
      {!run.inProduction && <div className="text-xs text-amber-700">not in Production</div>}
      {upToDate && <div className="text-xs text-green-700">Up to date</div>}
      {!upToDate && releasesBehind !== null && newest && (
        <div className="flex flex-wrap items-center gap-2">
          <span className={AMBER_MARKER}>{behindLabel(releasesBehind)}</span>
          <span className="text-xs text-gray-600">newest is {newest.name}</span>
        </div>
      )}
    </div>
  );
}

function ColumnName({ column }: { column: MatrixColumn }) {
  return (
    <>
      <span className={column.archived ? 'text-gray-500' : undefined}>{column.name}</span>
      {column.archived && <span className={`ml-2 ${ARCHIVED_TAG}`}>archived</span>}
    </>
  );
}

type Props = { productIds: string[]; archived: boolean; onProductsChange: (ids: string[]) => void };

export function PortfolioMatrix({ productIds, archived, onProductsChange }: Props) {
  const matrix = usePortfolioMatrix(productIds, archived);
  const data = matrix.data;
  const cells = new Map((data?.cells ?? []).map((c) => [`${c.clientId}:${c.productId}`, c]));

  return (
    <section aria-labelledby="matrix-heading">
      <h3 id="matrix-heading" className="text-base font-semibold text-gray-900">
        Client x product
      </h3>
      <p className="text-xs text-gray-500">The release each client currently runs, per product.</p>

      {matrix.isPending && (
        <div className="mt-4">
          <SectionLoading text="Loading client matrix…" />
        </div>
      )}
      {matrix.isError && (
        <div className="mt-4">
          <SectionError what="the client matrix" error={matrix.error} onRetry={() => matrix.refetch()} />
        </div>
      )}

      {data && data.clients.length === 0 && (
        <div className="mt-4 rounded bg-gray-50 p-6 text-center">
          <p className="text-base font-semibold text-gray-900">No clients yet</p>
          <p className="mt-2 text-sm text-gray-600">
            Add a client on the Portfolio page, then link it to the products it runs.{' '}
            <Link to="/portfolio" className="text-blue-600 underline">
              Portfolio page
            </Link>
          </p>
        </div>
      )}

      {data && data.clients.length > 0 && data.products.length === 0 && productIds.length > 0 && (
        <div className="mt-4 rounded bg-gray-50 p-6 text-center">
          <p className="text-base font-semibold text-gray-900">No products match this filter</p>
          <p className="mt-2 text-sm text-gray-600">
            Choose different products, or clear the filter to see all of them.{' '}
            <button type="button" onClick={() => onProductsChange([])} className="text-blue-600 underline">
              Clear products
            </button>
          </p>
        </div>
      )}

      {data && data.clients.length > 0 && data.products.length > 0 && (
        <div className="mt-4 overflow-x-auto rounded border border-gray-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="border-b border-gray-200">
              <tr>
                <th scope="col" className={`${TH} sticky left-0 min-w-[12rem] bg-white`}>
                  Client
                </th>
                {data.products.map((p) => (
                  <th key={p.id} scope="col" className={`${TH} min-w-[14rem]`}>
                    <ColumnName column={p} />
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {data.clients.map((client) => (
                <tr key={client.id} className="border-b border-gray-100 hover:bg-gray-50">
                  <th scope="row" className="sticky left-0 min-w-[12rem] bg-white px-4 py-2 text-left align-top">
                    <Link
                      to="/portfolio"
                      search={{ tab: 'clients', client: client.id }}
                      className={`font-semibold hover:underline ${client.archived ? 'text-gray-500' : 'text-blue-600'}`}
                    >
                      {client.name}
                    </Link>
                    {client.archived && <span className={`ml-2 ${ARCHIVED_TAG}`}>archived</span>}
                  </th>
                  {data.products.map((p) => (
                    <td key={p.id} className="px-4 py-2 align-top">
                      <Cell cell={cells.get(`${client.id}:${p.id}`)} product={p} />
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
