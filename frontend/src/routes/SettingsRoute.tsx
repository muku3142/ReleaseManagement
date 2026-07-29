import { useEffect, useState } from 'react';
import { useJiraSite, useUpdateJiraSite } from '../api/jiraSite';
import { ApiError } from '../api/client';

export function SettingsRoute() {
  const jiraSite = useJiraSite();
  const updateJiraSite = useUpdateJiraSite();
  const [baseUrl, setBaseUrl] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (jiraSite.data) setBaseUrl(jiraSite.data.baseUrl);
  }, [jiraSite.data]);

  const notConfigured =
    jiraSite.isError && jiraSite.error instanceof ApiError && jiraSite.error.problem.reason === 'SITE_NOT_CONFIGURED';

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSaved(false);
    updateJiraSite.mutate(baseUrl, { onSuccess: () => setSaved(true) });
  }

  const mutationProblem =
    updateJiraSite.isError && updateJiraSite.error instanceof ApiError ? updateJiraSite.error.problem : undefined;
  const fieldError =
    mutationProblem?.reason === 'VALIDATION_FAILED' ? mutationProblem.fieldErrors?.baseUrl : undefined;
  const mutationDetail = mutationProblem?.detail;

  return (
    <div className="max-w-xl">
      <h2 className="text-base font-semibold text-gray-900">Jira site</h2>

      {jiraSite.isPending && <p className="mt-2 text-sm text-gray-600">Loading…</p>}

      {jiraSite.isError && !notConfigured && (
        <p className="mt-2 text-sm text-red-600">
          {jiraSite.error instanceof ApiError ? jiraSite.error.problem.detail : 'Failed to load Jira site.'}
        </p>
      )}

      {notConfigured && (
        <p className="mt-2 text-sm text-gray-600">No Jira site configured yet — set one below.</p>
      )}

      {jiraSite.data && (
        <dl className="mt-2 space-y-1 text-sm text-gray-600">
          <div>
            <dt className="inline font-medium text-gray-700">Current site: </dt>
            <dd className="inline">{jiraSite.data.baseUrl}</dd>
          </div>
          <div>
            <dt className="inline font-medium text-gray-700">Last updated: </dt>
            <dd className="inline">{new Date(jiraSite.data.updatedAt).toLocaleString()}</dd>
          </div>
          <div>
            <dt className="inline font-medium text-gray-700">Updated by: </dt>
            <dd className="inline">{jiraSite.data.updatedBy}</dd>
          </div>
        </dl>
      )}

      <form onSubmit={handleSubmit} className="mt-4 space-y-2">
        <label htmlFor="baseUrl" className="block text-sm font-medium text-gray-700">
          Jira Cloud site
        </label>
        <input
          id="baseUrl"
          name="baseUrl"
          type="text"
          value={baseUrl}
          onChange={(event) => setBaseUrl(event.target.value)}
          placeholder="https://your-site.atlassian.net"
          className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
        />
        {fieldError && <p className="text-sm text-red-600">{fieldError}</p>}
        <button
          type="submit"
          disabled={updateJiraSite.isPending}
          className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          Save
        </button>
        {saved && !updateJiraSite.isError && <p className="text-sm text-green-600">Saved.</p>}
        {mutationDetail && !fieldError && <p className="text-sm text-red-600">{mutationDetail}</p>}
      </form>

      <h2 className="mt-8 text-base font-semibold text-gray-900">Jira API token</h2>
      <p className="mt-2 text-sm text-gray-600">Sign in to manage your Jira API token.</p>
    </div>
  );
}
