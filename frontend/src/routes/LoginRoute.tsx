import { useEffect, useRef, useState } from 'react';
import { ExternalLink } from 'lucide-react';
import { useLogin, useSession } from '../api/auth';
import { messageForReason } from '../api/reasonMessages';
import { useJiraSite } from '../api/jiraSite';
import { ApiError } from '../api/client';
import { safeRedirectTarget } from '../safeRedirect';
import { ProductOpsLogo } from '@/components/brand/ProductOpsLogo';
import { PRODUCT_NAME } from '@/components/brand/brand';
import { Alert, AlertDescription, AlertTitle } from '@/components/ui/alert';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Spinner } from '@/components/ui/spinner';

const LOGIN_GLOW = 'radial-gradient(120% 80% at 50% 0%, color-mix(in oklch, var(--brand-from) 14%, transparent), transparent 60%)';

function redirectTarget() {
  return safeRedirectTarget(new URLSearchParams(window.location.search).get('redirect'));
}

export function LoginRoute() {
  const [email, setEmail] = useState('');
  const [apiToken, setApiToken] = useState('');
  const login = useLogin();
  const jiraSite = useJiraSite();
  const session = useSession();
  const errorRef = useRef<HTMLDivElement>(null);

  const siteNotConfigured =
    jiraSite.isError && jiraSite.error instanceof ApiError && jiraSite.error.problem.reason === 'SITE_NOT_CONFIGURED';
  const problem = login.error instanceof ApiError ? login.error.problem : undefined;

  useEffect(() => {
    if (session.data) window.location.replace(redirectTarget());
  }, [session.data]);

  useEffect(() => {
    if (problem) errorRef.current?.focus();
  }, [problem]);

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    login.mutate({ email, apiToken }, { onSuccess: () => window.location.assign(redirectTarget()) });
  }

  return (
    <div className="relative grid min-h-dvh place-items-center bg-background px-6 py-12">
      <div aria-hidden="true" className="pointer-events-none absolute inset-0" style={{ background: LOGIN_GLOW }} />
      <div className="relative w-full max-w-sm animate-in fade-in slide-in-from-bottom-1 duration-200 ease-out">
        <ProductOpsLogo variant="login" className="mb-8" animate />
        <Card className="gap-6 rounded-xl p-8 shadow-md">
          <div>
            <h1 className="text-base font-semibold text-foreground">Sign in</h1>
            {jiraSite.data && (
              <p className="mt-1 text-sm text-muted-foreground">
                Signing in to <span className="font-semibold text-foreground">{jiraSite.data.baseUrl}</span>.
              </p>
            )}
          </div>

          {siteNotConfigured ? (
            <Alert variant="warning" role="alert" data-reason="SITE_NOT_CONFIGURED">
              <AlertTitle>No Jira site is set up yet</AlertTitle>
              <AlertDescription>
                {PRODUCT_NAME} takes its Jira Cloud site from the RM_JIRA_DEFAULT_BASE_URL setting the first time it
                starts. Ask whoever runs {PRODUCT_NAME} to set it and restart the app, then reload this page.
              </AlertDescription>
            </Alert>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="email">Jira email</Label>
                <Input
                  id="email"
                  name="email"
                  type="email"
                  required
                  autoComplete="username"
                  value={email}
                  onChange={(event) => setEmail(event.target.value)}
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="apiToken">Jira API token</Label>
                <Input
                  id="apiToken"
                  name="apiToken"
                  type="password"
                  required
                  autoComplete="off"
                  value={apiToken}
                  onChange={(event) => setApiToken(event.target.value)}
                />
              </div>
              <p className="text-xs text-muted-foreground">
                Need a token?{' '}
                <a
                  href="https://id.atlassian.com/manage-profile/security/api-tokens"
                  target="_blank"
                  rel="noreferrer"
                  className="inline-flex items-center gap-1 text-primary underline-offset-4 hover:underline"
                >
                  Create one on Atlassian's site
                  <ExternalLink className="size-3" aria-hidden="true" />
                </a>
              </p>
              <Button type="submit" className="w-full" disabled={login.isPending}>
                {login.isPending ? (
                  <>
                    <Spinner />
                    Signing in…
                  </>
                ) : (
                  'Sign in'
                )}
              </Button>
              {problem && (
                <Alert variant="destructive" role="alert" data-reason={problem.reason} tabIndex={-1} ref={errorRef}>
                  <AlertDescription>{messageForReason(problem)}</AlertDescription>
                </Alert>
              )}
            </form>
          )}
        </Card>
      </div>
    </div>
  );
}
