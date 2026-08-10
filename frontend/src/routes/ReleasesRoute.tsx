import { useEffect, useState } from 'react';
import { Link } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/auth';
import { useReleases } from '../api/releases';
import { readinessFor, useDocumentReadiness } from '../api/documents';
import { DOCUMENT_TYPES, DOCUMENT_TYPE_LABELS } from '../api/templates';
import { ReadinessPill } from '../components/ReadinessPill';

export function ReleasesRoute() {
  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');

  // Local-DB search is cheap, unlike Phase 2's Jira search, so it runs as you type rather than
  // behind a Load button. 300ms keeps a fast typist to one request per pause.
  useEffect(() => {
    const timer = setTimeout(() => setQuery(search.trim()), 300);
    return () => clearTimeout(timer);
  }, [search]);

  const releases = useReleases(query);
  const problem = releases.isError && releases.error instanceof ApiError ? releases.error.problem : undefined;
  // DOC-09: independent of the releases query on purpose - a readiness failure must not blank the
  // whole release table (T-05-60), so its own isError branch only affects the three new cells below.
  const readiness = useDocumentReadiness();

  return (
    <div className="w-full">
      <div className="bg-white p-6">
        <h2 className="text-base font-semibold text-gray-900">Releases</h2>

        <div className="mt-4 flex flex-wrap items-end gap-3">
          <input
            type="search"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            placeholder="Search by name or version…"
            aria-label="Search releases"
            className="rounded border border-gray-300 px-3 py-2 text-sm focus-visible:ring-2 focus-visible:ring-blue-600"
          />
        </div>
      </div>

      {releases.isPending && (
        <div className="mt-6 flex items-center gap-3">
          <span
            className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
            aria-hidden="true"
          />
          <p className="text-sm text-gray-600">Loading releases…</p>
        </div>
      )}

      {problem && (
        <div className="mt-6">
          <p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
            {messageForReason(problem)}
          </p>
          <button
            type="button"
            onClick={() => releases.refetch()}
            className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700"
          >
            Try again
          </button>
        </div>
      )}

      {!releases.isPending && !problem && releases.data && releases.data.releases.length === 0 && (
        <div className="mt-6 rounded bg-gray-50 p-6 text-center">
          {query === '' ? (
            <>
              <p className="text-base font-semibold text-gray-900">No releases yet</p>
              <p className="mt-2 text-sm text-gray-600">
                Releases are created from the ticket browser. Browse tickets, select the ones you
                want, and choose &apos;Add to release&apos; → &apos;New release…&apos;.{' '}
                <Link to="/tickets" className="text-blue-600 underline">
                  Browse tickets
                </Link>
              </p>
            </>
          ) : (
            <>
              <p className="text-base font-semibold text-gray-900">No releases match &apos;{query}&apos;</p>
              <p className="mt-2 text-sm text-gray-600">Try a different name or version.</p>
            </>
          )}
        </div>
      )}

      {!releases.isPending && !problem && releases.data && releases.data.releases.length > 0 && (
        <div className="mt-6 overflow-x-auto rounded border border-gray-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="border-b border-gray-200">
              <tr>
                <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  Name
                </th>
                <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  Version
                </th>
                <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  Status
                </th>
                <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  Project
                </th>
                <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  Items
                </th>
                <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                  Last refreshed
                </th>
                {DOCUMENT_TYPES.map((type) => (
                  <th key={type} scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    {DOCUMENT_TYPE_LABELS[type]}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {releases.data.releases.map((r) => (
                <tr key={r.id} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="whitespace-nowrap px-4 py-2">
                    <Link
                      to="/releases/$releaseId"
                      params={{ releaseId: r.id }}
                      className="font-semibold text-blue-600 hover:underline"
                    >
                      {r.name}
                    </Link>
                  </td>
                  <td className="whitespace-nowrap px-4 py-2 text-gray-700">{r.version}</td>
                  <td className="whitespace-nowrap px-4 py-2">
                    {r.status === 'FROZEN' ? (
                      <span className="rounded-full bg-gray-800 px-2 py-0.5 text-sm text-white">Frozen</span>
                    ) : (
                      <span className="rounded-full bg-gray-100 px-2 py-0.5 text-sm text-gray-700">Open</span>
                    )}
                  </td>
                  <td className="whitespace-nowrap px-4 py-2 text-gray-700">{r.jiraProjectKey ?? '—'}</td>
                  <td className="whitespace-nowrap px-4 py-2 text-gray-700">{r.itemCount}</td>
                  <td className="whitespace-nowrap px-4 py-2 text-gray-700">
                    {r.lastRefreshedAt ? new Date(r.lastRefreshedAt).toLocaleString() : '—'}
                  </td>
                  {DOCUMENT_TYPES.map((type) => (
                    <td key={type} className="whitespace-nowrap px-4 py-2">
                      {readiness.isError ? (
                        <span className="text-gray-400">—</span>
                      ) : (
                        <ReadinessPill
                          state={readinessFor(readiness.data?.entries, r.id, type)?.state ?? 'NOT_GENERATED'}
                          releaseId={r.id}
                          documentType={type}
                        />
                      )}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
