import { useEffect, useState } from 'react';
import { useAdoCredential, useUpdateAdoCredential } from '../api/adoCredential';
import { useJiraSite, useUpdateJiraSite } from '../api/jiraSite';
import { ApiError } from '../api/client';
import { EnvironmentLadderSection } from '../components/EnvironmentLadderSection';
import { useSession, useReplaceJiraToken as useReplaceToken, messageForReason } from '../api/auth';

export function SettingsRoute() {
  const jiraSite = useJiraSite();
  const updateJiraSite = useUpdateJiraSite();
  const [baseUrl, setBaseUrl] = useState('');
  const [saved, setSaved] = useState(false);
  const session = useSession();
  const replaceToken = useReplaceToken();
  const [apiToken, setApiToken] = useState('');
  const [tokenReplaced, setTokenReplaced] = useState(false);

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

  const tokenProblem =
    replaceToken.isError && replaceToken.error instanceof ApiError ? replaceToken.error.problem : undefined;

  function handleReplaceToken(event: React.FormEvent) {
    event.preventDefault();
    setTokenReplaced(false);
    replaceToken.mutate(apiToken, {
      onSuccess: () => {
        setApiToken('');
        setTokenReplaced(true);
      },
    });
  }

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
      {session.data && (
        <p className="mt-2 text-sm text-gray-600">
          Replacing the token for <span className="font-medium">{session.data.email}</span>.
        </p>
      )}
      <p className="mt-1 text-sm text-gray-600">
        The stored token is never displayed. Enter a new token to replace it.
      </p>

      <form onSubmit={handleReplaceToken} className="mt-4 space-y-2">
        <label htmlFor="apiToken" className="block text-sm font-medium text-gray-700">
          New Jira API token
        </label>
        <input
          id="apiToken"
          name="apiToken"
          type="password"
          required
          autoComplete="off"
          value={apiToken}
          onChange={(event) => setApiToken(event.target.value)}
          className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
        />
        <button
          type="submit"
          disabled={replaceToken.isPending}
          className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          Replace token
        </button>
        {tokenReplaced && !replaceToken.isError && (
          <p role="status" className="text-sm text-green-600">
            Token replaced.
          </p>
        )}
        {tokenProblem && (
          <p role="alert" data-reason={tokenProblem.reason} className="text-sm text-red-600">
            {messageForReason(tokenProblem)}
          </p>
        )}
      </form>

      <AzureDevOpsSection />
      <EnvironmentLadderSection />
    </div>
  );
}

const ADO_STATE_BADGE = {
  OK: { label: 'OK', className: 'text-green-600' },
  EXPIRING: { label: 'Expiring soon', className: 'text-amber-700' },
  EXPIRED: { label: 'Expired', className: 'text-red-600' },
} as const;

function AzureDevOpsSection() {
  const credential = useAdoCredential();
  const update = useUpdateAdoCredential();
  const [organization, setOrganization] = useState('');
  const [token, setToken] = useState('');
  const [expiresOn, setExpiresOn] = useState('');
  const [saved, setSaved] = useState(false);

  useEffect(() => {
    if (credential.data?.configured) {
      setOrganization(credential.data.organization ?? '');
      setExpiresOn(credential.data.expiresOn ?? '');
    }
  }, [credential.data]);

  const problem = update.isError && update.error instanceof ApiError ? update.error.problem : undefined;
  const configured = credential.data?.configured ? credential.data : undefined;
  const badge = configured?.state ? ADO_STATE_BADGE[configured.state] : undefined;

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSaved(false);
    update.mutate(
      { organization, token, expiresOn },
      {
        onSuccess: () => {
          setToken('');
          setSaved(true);
        },
      },
    );
  }

  return (
    <>
      <h2 className="mt-8 text-base font-semibold text-gray-900">Azure DevOps</h2>
      {configured && (
        <p className="mt-2 text-sm text-gray-600">
          Organization <span className="font-medium">{configured.organization}</span>, token expires{' '}
          {configured.expiresOn && new Date(configured.expiresOn + 'T00:00:00').toLocaleDateString()}
          {badge && (
            <span data-state={configured.state} className={`ml-2 font-medium ${badge.className}`}>
              {badge.label}
            </span>
          )}
        </p>
      )}
      <p className="mt-1 text-sm text-gray-600">
        Optional. Without a token, each ticket shows only the commits whose message names the ticket key. Create a
        Personal Access Token in Azure DevOps with the Code (Read) scope, select one organization (not &quot;All
        accessible organizations&quot;: those tokens stop working on 1 December 2026), and enter the expiry date you
        chose.
      </p>

      <form onSubmit={handleSubmit} className="mt-4 space-y-2">
        <label htmlFor="adoOrganization" className="block text-sm font-medium text-gray-700">
          Azure DevOps organization
        </label>
        <input
          id="adoOrganization"
          name="adoOrganization"
          type="text"
          required
          value={organization}
          onChange={(event) => setOrganization(event.target.value)}
          className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
        />
        <label htmlFor="adoToken" className="block text-sm font-medium text-gray-700">
          Personal Access Token
        </label>
        <input
          id="adoToken"
          name="adoToken"
          type="password"
          autoComplete="off"
          value={token}
          onChange={(event) => setToken(event.target.value)}
          placeholder={configured ? 'Leave blank to change only the expiry date' : undefined}
          className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
        />
        <label htmlFor="adoExpiresOn" className="block text-sm font-medium text-gray-700">
          Token expiry date
        </label>
        <input
          id="adoExpiresOn"
          name="adoExpiresOn"
          type="date"
          required
          value={expiresOn}
          onChange={(event) => setExpiresOn(event.target.value)}
          className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
        />
        <button
          type="submit"
          disabled={update.isPending}
          className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          Save Azure DevOps token
        </button>
        {saved && !update.isError && (
          <p role="status" className="text-sm text-green-600">
            Azure DevOps settings saved.
          </p>
        )}
        {problem && (
          <p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
            {messageForReason(problem)}
          </p>
        )}
      </form>
    </>
  );
}
