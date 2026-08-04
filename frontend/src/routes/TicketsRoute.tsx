import { useState } from 'react';
import { useJiraProjects, useJiraVersions } from '../api/jiraProjects';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/auth';
import {
  DEFAULT_STATUS_CATEGORIES,
  STATUS_CATEGORIES,
  STATUS_CATEGORY_LABELS,
  useTicketSearch,
  type StatusCategory,
  type Ticket,
} from '../api/tickets';

export function TicketsRoute() {
  const [projectId, setProjectId] = useState('');
  const [fixVersionId, setFixVersionId] = useState('');
  const [statusCategories, setStatusCategories] = useState<StatusCategory[]>(DEFAULT_STATUS_CATEGORIES);
  const [loaded, setLoaded] = useState<Ticket[] | null>(null);

  const projects = useJiraProjects();
  const versions = useJiraVersions(projectId);
  const search = useTicketSearch();

  function handleProjectChange(event: React.ChangeEvent<HTMLSelectElement>) {
    setProjectId(event.target.value);
    setFixVersionId('');
  }

  function runSearch() {
    search.mutate(
      { mode: 'fixVersion', projectId, fixVersionId, statusCategories },
      { onSuccess: (data) => setLoaded(data.tickets) },
    );
  }

  const projectsProblem =
    projects.isError && projects.error instanceof ApiError ? projects.error.problem : undefined;
  const versionsProblem =
    versions.isError && versions.error instanceof ApiError ? versions.error.problem : undefined;
  const searchProblem =
    search.isError && search.error instanceof ApiError ? search.error.problem : undefined;

  const selectedProjectKey =
    projects.data?.find((p) => p.id === projectId)?.key ?? 'the selected project';
  const selectedVersionName =
    versions.data?.find((v) => v.id === fixVersionId)?.name ?? 'the selected fix version';
  // With the Done-only default, the most likely cause of an empty result is that the version's
  // work is not finished yet — so the message names the active status filter rather than sending
  // the release manager hunting the wrong one.
  const statusHint =
    statusCategories.length === 0 || statusCategories.length === STATUS_CATEGORIES.length
      ? ''
      : ` with status ${statusCategories.map((c) => STATUS_CATEGORY_LABELS[c]).join(' or ')}`;

  return (
    <div className="max-w-5xl">
      <h2 className="text-base font-semibold text-gray-900">Browse tickets</h2>

      {projects.isPending && <p className="mt-2 text-sm text-gray-600">Loading projects…</p>}

      <div className="mt-4 flex flex-wrap items-end gap-3">
        <div>
          <label htmlFor="projectId" className="block text-sm font-semibold text-gray-700">
            Project
          </label>
          <select
            id="projectId"
            value={projectId}
            onChange={handleProjectChange}
            disabled={search.isPending}
            className="rounded border border-gray-300 px-3 py-2 text-sm"
          >
            <option value="">Select a project…</option>
            {projects.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.key} — {p.name}
              </option>
            ))}
          </select>
        </div>

        <div>
          <label htmlFor="fixVersionId" className="block text-sm font-semibold text-gray-700">
            Fix version
          </label>
          <select
            id="fixVersionId"
            value={fixVersionId}
            onChange={(event) => setFixVersionId(event.target.value)}
            disabled={projectId === '' || versions.isPending || search.isPending}
            className="rounded border border-gray-300 px-3 py-2 text-sm"
          >
            <option value="">Select a fix version…</option>
            {versions.data?.map((v) => (
              <option key={v.id} value={v.id}>
                {v.name}
              </option>
            ))}
          </select>
        </div>

        <fieldset className="flex items-center gap-3" disabled={search.isPending}>
          <legend className="text-sm font-semibold text-gray-900">Status</legend>
          {STATUS_CATEGORIES.map((category) => (
            <label key={category} className="flex items-center gap-1.5 text-sm text-gray-600">
              <input
                type="checkbox"
                checked={statusCategories.includes(category)}
                onChange={(event) =>
                  setStatusCategories((current) =>
                    event.target.checked
                      ? [...current, category]
                      : current.filter((c) => c !== category),
                  )
                }
                className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-600"
              />
              {STATUS_CATEGORY_LABELS[category]}
            </label>
          ))}
        </fieldset>

        <button
          type="button"
          onClick={runSearch}
          disabled={projectId === '' || fixVersionId === '' || search.isPending}
          className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        >
          Load tickets
        </button>
      </div>

      {projectsProblem && (
        <p role="alert" data-reason={projectsProblem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(projectsProblem)}
        </p>
      )}
      {versionsProblem && (
        <p role="alert" data-reason={versionsProblem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(versionsProblem)}
        </p>
      )}

      {search.isPending && (
        <div className="mt-6 flex items-center gap-3">
          <span
            className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
            aria-hidden="true"
          />
          <p className="text-sm text-gray-600">
            Loading tickets… this can take a few seconds for large fix versions.
          </p>
        </div>
      )}

      {searchProblem && (
        <div className="mt-6">
          <p role="alert" data-reason={searchProblem.reason} className="text-sm text-red-600">
            {messageForReason(searchProblem)}
          </p>
          <button
            type="button"
            onClick={runSearch}
            className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700"
          >
            Try again
          </button>
        </div>
      )}

      {!search.isPending && !searchProblem && loaded !== null && loaded.length === 0 && (
        <div className="mt-6 rounded bg-gray-50 p-6 text-center">
          <p className="text-base font-semibold text-gray-900">No tickets found</p>
          <p className="mt-2 text-sm text-gray-600">
            No tickets matched {selectedProjectKey} / {selectedVersionName}
            {statusHint}. Try a different fix version, widen the status filter, or use Advanced:
            edit as JQL.
          </p>
        </div>
      )}

      {!search.isPending && loaded !== null && loaded.length > 0 && (
        <div className="mt-6 overflow-x-auto rounded border border-gray-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="border-b border-gray-200">
              <tr>
                <th scope="col" className="px-4 py-2 text-left"></th>
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
              </tr>
            </thead>
            <tbody>
              {loaded.map((ticket) => (
                <tr key={ticket.key} className="border-b border-gray-100 hover:bg-gray-50">
                  <td className="px-4 py-2"></td>
                  <td className="px-4 py-2 font-semibold text-gray-900">{ticket.key}</td>
                  <td
                    className="max-w-md overflow-hidden text-ellipsis whitespace-nowrap px-4 py-2 text-gray-700"
                    title={ticket.summary}
                  >
                    {ticket.summary}
                  </td>
                  <td className="px-4 py-2 text-gray-700">{ticket.issueType}</td>
                  <td className="px-4 py-2 text-gray-700">{ticket.status}</td>
                  <td className="px-4 py-2 text-gray-700">{ticket.resolution ?? '—'}</td>
                  <td className="px-4 py-2 text-gray-700">{ticket.assignee ?? '—'}</td>
                  <td className="px-4 py-2 text-gray-700">{ticket.reporter}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
