import { Fragment, useState } from 'react';
import { Link, useParams, useSearch } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/auth';
import { MANUAL_ITEM_CATEGORY_LABELS, useRelease } from '../api/releases';

// D-03-13: idempotent add, honest feedback - never phrased as a warning, because a duplicate
// add is a success, not a partial failure.
function addResultMessage(added: number, alreadyPresent: number, name: string, version: string) {
  if (alreadyPresent === 0) return `${added} ticket(s) added to ${name} ${version}.`;
  if (added === 0) return `All ${alreadyPresent} selected tickets were already on this release.`;
  return `${added} added, ${alreadyPresent} already on this release.`;
}

export function ReleaseDetailRoute() {
  // The pathless authenticated layout route is registered with id: 'authenticated' (no `path`),
  // so TanStack Router's generated route-id space prefixes every child with /authenticated even
  // though the actual URL (and `to=` navigation) has no such segment.
  const { releaseId } = useParams({ from: '/authenticated/releases/$releaseId' });
  const { added, alreadyPresent } = useSearch({ from: '/authenticated/releases/$releaseId' });
  const release = useRelease(releaseId);
  const [expandedKey, setExpandedKey] = useState<string | null>(null);
  const [bannerDismissed, setBannerDismissed] = useState(false);
  const showBanner = !bannerDismissed && added !== undefined && alreadyPresent !== undefined;

  const problem = release.isError && release.error instanceof ApiError ? release.error.problem : undefined;

  if (release.isPending) {
    return (
      <div className="flex items-center gap-3">
        <span
          className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
          aria-hidden="true"
        />
        <p className="text-sm text-gray-600">Loading release…</p>
      </div>
    );
  }

  if (problem && problem.reason === 'RELEASE_NOT_FOUND') {
    return (
      <div>
        <p role="alert" data-reason="RELEASE_NOT_FOUND" className="text-sm text-red-600">
          {messageForReason(problem)}
        </p>
        <Link to="/releases" className="mt-2 inline-block text-sm text-blue-600 underline">
          Back to releases
        </Link>
      </div>
    );
  }

  if (problem) {
    return (
      <div>
        <p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
          {messageForReason(problem)}
        </p>
        <button
          type="button"
          onClick={() => release.refetch()}
          className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700"
        >
          Try again
        </button>
      </div>
    );
  }

  const data = release.data;
  if (!data) return null;

  const isFrozen = data.status === 'FROZEN';
  const noItems = data.items.length === 0 && data.manualItems.length === 0;

  return (
    <div className="w-full">
      <div className="bg-white p-6">
        <div>
          <h2 className="text-base font-semibold text-gray-900 inline">{data.name}</h2>
          <span className="ml-2 text-sm text-gray-600">{data.version}</span>
          {isFrozen ? (
            <span className="ml-3 rounded-full bg-gray-800 px-2 py-0.5 text-sm text-white">Frozen</span>
          ) : (
            <span className="ml-3 rounded-full bg-gray-100 px-2 py-0.5 text-sm text-gray-700">Open</span>
          )}
        </div>

        <div className="mt-2 space-x-4 text-sm text-gray-600">
          {data.jiraProjectKey !== null && <span>Project: {data.jiraProjectKey}</span>}
          <span>
            Created by {data.createdBy} on {new Date(data.createdAt).toLocaleString()}
          </span>
          {isFrozen && data.frozenAt !== null && (
            <span>
              Frozen by {data.frozenBy} on {new Date(data.frozenAt).toLocaleString()}
            </span>
          )}
          {data.lastRefreshedAt !== null && (
            <span>Last refreshed: {new Date(data.lastRefreshedAt).toLocaleString()}</span>
          )}
        </div>

        {isFrozen && (
          <p className="mt-4 rounded bg-gray-100 p-3 text-sm text-gray-700">
            This release is frozen. Its ticket set is locked and cannot be changed.
          </p>
        )}
      </div>

      {showBanner && (
        <div
          role="status"
          className="mt-4 flex items-start justify-between rounded border border-blue-200 bg-blue-50 p-3 text-sm text-blue-900"
        >
          <p>{addResultMessage(added, alreadyPresent, data.name, data.version)}</p>
          <button
            type="button"
            aria-label="Dismiss"
            onClick={() => setBannerDismissed(true)}
            className="ml-3 rounded px-2 text-blue-900 focus-visible:ring-2 focus-visible:ring-blue-600"
          >
            ×
          </button>
        </div>
      )}

      {noItems ? (
        <div className="mt-6 rounded bg-gray-50 p-6 text-center">
          {isFrozen ? (
            <p className="text-sm text-gray-600">This release has no items.</p>
          ) : (
            <>
              <p className="text-base font-semibold text-gray-900">No items yet</p>
              <p className="mt-2 text-sm text-gray-600">
                Add tickets from the ticket browser, or add a manual item below.
              </p>
            </>
          )}
        </div>
      ) : (
        <>
          <h3 className="mt-6 text-base font-semibold text-gray-900">Tickets ({data.items.length})</h3>
          <div className="mt-4 overflow-x-auto rounded border border-gray-200 bg-white">
            <table className="min-w-full text-sm">
              <thead className="border-b border-gray-200">
                <tr>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Key
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Summary
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Type
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Status
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Resolution
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Assignee
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Reporter
                  </th>
                  <th scope="col" className="px-4 py-2" />
                </tr>
              </thead>
              <tbody>
                {data.items.map((item) => (
                  <Fragment key={item.key}>
                    <tr className="border-b border-gray-100 hover:bg-gray-50">
                      <td className="whitespace-nowrap px-4 py-2 font-semibold text-gray-900">{item.key}</td>
                      <td className="min-w-[18rem] px-4 py-2 text-gray-700">{item.summary}</td>
                      <td className="whitespace-nowrap px-4 py-2 text-gray-700">{item.issueType}</td>
                      <td className="whitespace-nowrap px-4 py-2 text-gray-700">{item.status}</td>
                      <td className="whitespace-nowrap px-4 py-2 text-gray-700">{item.resolution ?? '—'}</td>
                      <td className="whitespace-nowrap px-4 py-2 text-gray-700">{item.assignee ?? '—'}</td>
                      <td className="whitespace-nowrap px-4 py-2 text-gray-700">{item.reporter ?? '—'}</td>
                      <td className="px-4 py-2">
                        <button
                          type="button"
                          onClick={() => setExpandedKey(expandedKey === item.key ? null : item.key)}
                          aria-expanded={expandedKey === item.key}
                          aria-label={
                            expandedKey === item.key ? 'Collapse ticket details' : 'Expand ticket details'
                          }
                          className="rounded px-2 text-sm text-gray-600 hover:text-gray-900 focus-visible:ring-2 focus-visible:ring-blue-600"
                        >
                          {expandedKey === item.key ? '▾' : '▸'}
                        </button>
                      </td>
                    </tr>
                    {expandedKey === item.key && (
                      <tr>
                        <td colSpan={8} className="px-4 pb-4">
                          <div className="max-h-80 overflow-y-auto rounded border border-gray-200 bg-gray-50 p-4">
                            <h3 className="text-sm font-semibold text-gray-900">Description</h3>
                            <p className="mt-2 whitespace-pre-wrap text-sm text-gray-700">
                              {item.description || 'No description.'}
                            </p>

                            <h3 className="mt-4 text-sm font-semibold text-gray-900">
                              Comments
                              {item.commentTotal > item.comments.length
                                ? ` (showing ${item.comments.length} of ${item.commentTotal})`
                                : ''}
                            </h3>
                            {item.comments.length === 0 ? (
                              <p className="mt-2 text-sm text-gray-600">No comments.</p>
                            ) : (
                              <ul className="mt-2 space-y-3">
                                {item.comments.map((comment, index) => (
                                  <li
                                    key={index}
                                    className="whitespace-pre-wrap border-l-2 border-gray-200 pl-3 text-sm text-gray-700"
                                  >
                                    {comment}
                                  </li>
                                ))}
                              </ul>
                            )}
                          </div>
                        </td>
                      </tr>
                    )}
                  </Fragment>
                ))}
              </tbody>
            </table>
          </div>

          <h3 className="mt-6 text-base font-semibold text-gray-900">
            Manual items ({data.manualItems.length})
          </h3>
          <div className="mt-4 overflow-x-auto rounded border border-gray-200 bg-white">
            <table className="min-w-full text-sm">
              <thead className="border-b border-gray-200">
                <tr>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Title
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Category
                  </th>
                  <th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
                    Description
                  </th>
                </tr>
              </thead>
              <tbody>
                {data.manualItems.map((item) => (
                  <tr key={item.id} className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="whitespace-nowrap px-4 py-2 font-semibold text-gray-900">{item.title}</td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">
                      {MANUAL_ITEM_CATEGORY_LABELS[item.category]}
                    </td>
                    <td
                      className="max-w-xs truncate px-4 py-2 text-gray-700"
                      title={item.description}
                    >
                      {item.description}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </div>
  );
}
