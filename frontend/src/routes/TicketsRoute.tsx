import { Fragment, useEffect, useRef, useState } from 'react';
import { useJiraProjects, useJiraSprints } from '../api/jiraProjects';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/auth';
import {
  DEFAULT_STATUS_CATEGORIES,
  STATUS_CATEGORIES,
  STATUS_CATEGORY_LABELS,
  useTicketSearch,
  type StatusCategory,
  type Ticket,
  type TicketSearchCriteria,
} from '../api/tickets';

export function TicketsRoute() {
  const [projectId, setProjectId] = useState('');
  const [sprintId, setSprintId] = useState('');
  const [statusCategories, setStatusCategories] = useState<StatusCategory[]>(DEFAULT_STATUS_CATEGORIES);
  const [loaded, setLoaded] = useState<Ticket[] | null>(null);
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [expandedKey, setExpandedKey] = useState<string | null>(null);
  const [jqlOpen, setJqlOpen] = useState(false);
  const [jql, setJql] = useState('');
  // Which mode produced the current `loaded` value. Deliberately not the live jqlOpen flag,
  // which the user may have toggled since the search ran.
  const [loadedMode, setLoadedMode] = useState<'project' | 'jql' | null>(null);

  const projects = useJiraProjects();
  const sprints = useJiraSprints(projectId);
  const search = useTicketSearch();

  const selectAllRef = useRef<HTMLInputElement>(null);
  const allSelected = loaded !== null && loaded.length > 0 && selected.size === loaded.length;
  const someSelected = selected.size > 0 && !allSelected;

  // indeterminate is a DOM property, not an HTML attribute, so it cannot be set as a JSX prop.
  useEffect(() => {
    if (selectAllRef.current) selectAllRef.current.indeterminate = someSelected;
  }, [someSelected]);

  function handleProjectChange(event: React.ChangeEvent<HTMLSelectElement>) {
    setProjectId(event.target.value);
    setSprintId('');
  }

  function runSearch() {
    const usingJql = jqlOpen && jql.trim() !== '';
    // Exactly one mode is ever sent: the backend rejects a body carrying both with
    // VALIDATION_FAILED, and the discriminated union makes that unrepresentable here.
    const criteria: TicketSearchCriteria = usingJql
      ? { mode: 'jql', jql: jql.trim() }
      : { mode: 'project', projectId, sprintId: sprintId || null, statusCategories };
    search.mutate(criteria, {
      onSuccess: (data) => {
        setLoaded(data.tickets);
        // A checkbox pointing at a ticket no longer on screen would misreport what is selected.
        setSelected(new Set());
        setExpandedKey(null);
        setLoadedMode(usingJql ? 'jql' : 'project');
      },
    });
  }

  function toggle(key: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key);
      else next.add(key);
      return next;
    });
  }

  const projectsProblem =
    projects.isError && projects.error instanceof ApiError ? projects.error.problem : undefined;
  const sprintsProblem =
    sprints.isError && sprints.error instanceof ApiError ? sprints.error.problem : undefined;
  const searchProblem =
    search.isError && search.error instanceof ApiError ? search.error.problem : undefined;

  // A project with no scrum board has no sprints, which is normal for a Kanban or business
  // project — the picker says so rather than sitting empty and looking like a failed load.
  const hasNoSprints = projectId !== '' && sprints.isSuccess && (sprints.data?.length ?? 0) === 0;

  const selectedProjectKey =
    projects.data?.find((p) => p.id === projectId)?.key ?? 'the selected project';
  const selectedSprint = sprints.data?.find((s) => s.id === sprintId)?.name;
  const scopeDescription = selectedSprint
    ? `${selectedProjectKey} / ${selectedSprint}`
    : selectedProjectKey;
  // With the Done-only default, the most likely cause of an empty result is that the sprint's
  // work is not finished yet — so the message names the active status filter rather than sending
  // the release manager hunting the wrong one.
  const statusHint =
    statusCategories.length === 0 || statusCategories.length === STATUS_CATEGORIES.length
      ? ''
      : ` with status ${statusCategories.map((c) => STATUS_CATEGORY_LABELS[c]).join(' or ')}`;

  return (
    // No max-width cap: the ticket table is the point of this screen and eight columns do not
    // fit in 5xl, which was cutting content off.
    <div className="w-full">
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
          <label htmlFor="sprintId" className="block text-sm font-semibold text-gray-700">
            Sprint <span className="font-normal text-gray-500">(optional)</span>
          </label>
          <select
            id="sprintId"
            value={sprintId}
            onChange={(event) => setSprintId(event.target.value)}
            disabled={projectId === '' || sprints.isPending || search.isPending || hasNoSprints}
            className="rounded border border-gray-300 px-3 py-2 text-sm"
          >
            <option value="">{hasNoSprints ? 'No sprints in this project' : 'All sprints'}</option>
            {sprints.data?.map((s) => (
              <option key={s.id} value={s.id}>
                {s.name} ({s.state})
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
          disabled={search.isPending || (jqlOpen && jql.trim() !== '' ? false : projectId === '')}
          className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
        >
          Load tickets
        </button>
      </div>

      <button
        type="button"
        onClick={() => setJqlOpen((open) => !open)}
        aria-expanded={jqlOpen}
        className="mt-3 text-sm text-blue-600 underline"
      >
        Advanced: edit as JQL
      </button>

      {jqlOpen && (
        <div className="mt-2">
          <label htmlFor="jql" className="block text-sm font-semibold text-gray-700">
            JQL
          </label>
          <textarea
            id="jql"
            rows={3}
            maxLength={2000}
            value={jql}
            onChange={(event) => setJql(event.target.value)}
            disabled={search.isPending}
            placeholder="project = CNV AND sprint = 42"
            className="mt-1 w-full rounded border border-gray-300 px-3 py-2 font-mono text-sm"
          />
          <p className="mt-1 text-sm text-gray-600">
            While JQL is entered, the project and sprint pickers are ignored for this search.
          </p>
        </div>
      )}

      {projectsProblem && (
        <p role="alert" data-reason={projectsProblem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(projectsProblem)}
        </p>
      )}
      {sprintsProblem && (
        <p role="alert" data-reason={sprintsProblem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(sprintsProblem)}
        </p>
      )}

      {search.isPending && (
        <div className="mt-6 flex items-center gap-3">
          <span
            className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
            aria-hidden="true"
          />
          <p className="text-sm text-gray-600">
            Loading tickets… this can take a few seconds for large sprints.
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
          {loadedMode === 'jql' ? (
            <p className="mt-2 text-sm text-gray-600">No tickets matched your JQL. Try a broader query.</p>
          ) : (
            <p className="mt-2 text-sm text-gray-600">
              {`No tickets matched ${scopeDescription}${statusHint}. Try a different sprint, widen the status filter, or use Advanced: edit as JQL.`}
            </p>
          )}
        </div>
      )}

      {!search.isPending && loaded !== null && loaded.length > 0 && (
        <p role="status" aria-live="polite" className="mt-6 text-sm font-semibold text-blue-600">
          {selected.size} selected
        </p>
      )}

      {!search.isPending && loaded !== null && loaded.length > 0 && (
        <div className="mt-6 overflow-x-auto rounded border border-gray-200 bg-white">
          <table className="min-w-full text-sm">
            <thead className="border-b border-gray-200">
              <tr>
                <th scope="col" className="px-4 py-2 text-left">
                  <input
                    ref={selectAllRef}
                    type="checkbox"
                    checked={allSelected}
                    onChange={() =>
                      setSelected(
                        allSelected ? new Set() : new Set((loaded ?? []).map((t) => t.key)),
                      )
                    }
                    aria-label="Select all loaded tickets"
                    className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-600"
                  />
                </th>
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
              {loaded.map((ticket) => (
                <Fragment key={ticket.key}>
                  <tr className="border-b border-gray-100 hover:bg-gray-50">
                    <td className="px-4 py-2">
                      <input
                        type="checkbox"
                        checked={selected.has(ticket.key)}
                        onChange={() => toggle(ticket.key)}
                        aria-label={`Select ticket ${ticket.key}`}
                        className="h-4 w-4 rounded border-gray-300 text-blue-600 focus-visible:ring-2 focus-visible:ring-blue-600"
                      />
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 font-semibold text-gray-900">
                      {ticket.key}
                    </td>
                    {/* Wraps rather than truncating — a release manager choosing tickets needs to
                        read the whole summary, and an ellipsis hid exactly that. */}
                    <td className="min-w-[18rem] px-4 py-2 text-gray-700">{ticket.summary}</td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">{ticket.issueType}</td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">{ticket.status}</td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">
                      {ticket.resolution ?? '—'}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">
                      {ticket.assignee ?? '—'}
                    </td>
                    <td className="whitespace-nowrap px-4 py-2 text-gray-700">{ticket.reporter}</td>
                    <td className="px-4 py-2">
                      <button
                        type="button"
                        onClick={() => setExpandedKey(expandedKey === ticket.key ? null : ticket.key)}
                        aria-expanded={expandedKey === ticket.key}
                        aria-label={
                          expandedKey === ticket.key ? 'Collapse ticket details' : 'Expand ticket details'
                        }
                        className="rounded px-2 text-sm text-gray-600 hover:text-gray-900 focus-visible:ring-2 focus-visible:ring-blue-600"
                      >
                        {expandedKey === ticket.key ? '▾' : '▸'}
                      </button>
                    </td>
                  </tr>
                  {expandedKey === ticket.key && (
                    <tr>
                      <td colSpan={9} className="px-4 pb-4">
                        <div className="max-h-80 overflow-y-auto rounded border border-gray-200 bg-gray-50 p-4">
                          <h3 className="text-sm font-semibold text-gray-900">Description</h3>
                          <p className="mt-2 whitespace-pre-wrap text-sm text-gray-700">
                            {ticket.description || 'No description.'}
                          </p>

                          <h3 className="mt-4 text-sm font-semibold text-gray-900">
                            Comments
                            {ticket.commentTotal > ticket.comments.length
                              ? ` (showing ${ticket.comments.length} of ${ticket.commentTotal})`
                              : ''}
                          </h3>
                          {ticket.comments.length === 0 ? (
                            <p className="mt-2 text-sm text-gray-600">No comments.</p>
                          ) : (
                            <ul className="mt-2 space-y-3">
                              {ticket.comments.map((comment, index) => (
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
      )}
    </div>
  );
}
