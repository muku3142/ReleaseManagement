import { useState } from 'react';
import { useLogin, messageForReason } from '../api/auth';
import { useJiraSite } from '../api/jiraSite';
import { ApiError } from '../api/client';

export function LoginRoute() {
  const [email, setEmail] = useState('');
  const [apiToken, setApiToken] = useState('');
  const login = useLogin();
  const jiraSite = useJiraSite();

  const siteNotConfigured =
    jiraSite.isError && jiraSite.error instanceof ApiError && jiraSite.error.problem.reason === 'SITE_NOT_CONFIGURED';

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    login.mutate(
      { email, apiToken },
      {
        onSuccess: () => {
          const search = new URLSearchParams(window.location.search);
          const redirectTo = search.get('redirect');
          window.location.assign(redirectTo || '/');
        },
      },
    );
  }

  const problem = login.error instanceof ApiError ? login.error.problem : undefined;

  return (
    <div className="mx-auto mt-16 max-w-sm">
      <h1 className="text-lg font-semibold text-gray-900">Sign in</h1>

      {jiraSite.data && (
        <p className="mt-2 text-sm text-gray-600">
          Signing in against <span className="font-medium">{jiraSite.data.baseUrl}</span>.
        </p>
      )}

      {siteNotConfigured && jiraSite.error instanceof ApiError && (
        <p role="alert" data-reason={jiraSite.error.problem.reason} className="mt-2 text-sm text-red-600">
          {messageForReason(jiraSite.error.problem)}{' '}
          <a href="/settings" className="underline">
            Go to Settings
          </a>
        </p>
      )}

      {!siteNotConfigured && (
        <form onSubmit={handleSubmit} className="mt-4 space-y-3">
          <div>
            <label htmlFor="email" className="block text-sm font-medium text-gray-700">
              Jira email
            </label>
            <input
              id="email"
              name="email"
              type="email"
              required
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className="w-full rounded border border-gray-300 px-3 py-2 text-sm"
            />
          </div>
          <div>
            <label htmlFor="apiToken" className="block text-sm font-medium text-gray-700">
              Jira API token
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
          </div>
          <p className="text-sm text-gray-600">
            Need a token?{' '}
            <a
              href="https://id.atlassian.com/manage-profile/security/api-tokens"
              target="_blank"
              rel="noreferrer"
              className="underline"
            >
              Create one on Atlassian's site.
            </a>
          </p>
          <button
            type="submit"
            disabled={login.isPending}
            className="rounded bg-blue-600 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
          >
            Sign in
          </button>
          {problem && (
            <p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
              {messageForReason(problem)}
            </p>
          )}
        </form>
      )}
    </div>
  );
}
