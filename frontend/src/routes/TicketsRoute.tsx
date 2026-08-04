import { useState } from 'react';
import { useJiraProjects, useJiraVersions } from '../api/jiraProjects';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/auth';

export function TicketsRoute() {
  const [projectId, setProjectId] = useState('');
  const [fixVersionId, setFixVersionId] = useState('');

  const projects = useJiraProjects();
  const versions = useJiraVersions(projectId);

  function handleProjectChange(event: React.ChangeEvent<HTMLSelectElement>) {
    setProjectId(event.target.value);
    setFixVersionId('');
  }

  const projectsProblem =
    projects.isError && projects.error instanceof ApiError ? projects.error.problem : undefined;
  const versionsProblem =
    versions.isError && versions.error instanceof ApiError ? versions.error.problem : undefined;

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
            disabled={projectId === '' || versions.isPending}
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
    </div>
  );
}
