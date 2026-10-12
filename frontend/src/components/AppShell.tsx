import { Link, Outlet } from '@tanstack/react-router';
import { useSession, useLogout } from '../api/auth';
import { AdoCredentialBanner } from './AdoCredentialBanner';
import { CredentialAlert } from './CredentialAlert';

export function AppShell() {
  const session = useSession();
  const logout = useLogout();

  function handleSignOut() {
    logout.mutate(undefined, {
      onSuccess: () => window.location.assign('/login'),
    });
  }

  return (
    <div className="min-h-screen bg-gray-50">
      <header className="border-b border-gray-200 bg-white px-6 py-4">
        <div className="flex items-center justify-between">
          <h1 className="text-lg font-semibold text-gray-900">Release Management</h1>
          {session.data && (
            <div className="flex items-center gap-3 text-sm text-gray-600">
              <span>{session.data.email}</span>
              <button
                type="button"
                onClick={handleSignOut}
                disabled={logout.isPending}
                className="rounded border border-gray-300 px-3 py-1 text-sm text-gray-700 disabled:opacity-50"
              >
                Sign out
              </button>
            </div>
          )}
        </div>
        <nav className="mt-2 flex gap-4 text-sm">
          <Link
            to="/"
            className="text-gray-600 hover:text-gray-900"
            activeProps={{ className: 'font-semibold text-gray-900' }}
          >
            Dashboard
          </Link>
          <Link
            to="/tickets"
            className="text-gray-600 hover:text-gray-900"
            activeProps={{ className: 'font-semibold text-gray-900' }}
          >
            Tickets
          </Link>
          <Link
            to="/releases"
            className="text-gray-600 hover:text-gray-900"
            activeProps={{ className: 'font-semibold text-gray-900' }}
          >
            Releases
          </Link>
          <Link
            to="/templates"
            className="text-gray-600 hover:text-gray-900"
            activeProps={{ className: 'font-semibold text-gray-900' }}
          >
            Templates
          </Link>
          <Link
            to="/settings"
            className="text-gray-600 hover:text-gray-900"
            activeProps={{ className: 'font-semibold text-gray-900' }}
          >
            Settings
          </Link>
        </nav>
      </header>
      <CredentialAlert />
      <AdoCredentialBanner />
      <main className="p-6">
        <Outlet />
      </main>
    </div>
  );
}
