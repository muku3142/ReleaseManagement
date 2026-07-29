import { Link, Outlet } from '@tanstack/react-router';

export function AppShell() {
  return (
    <div className="min-h-screen bg-gray-50">
      <header className="border-b border-gray-200 bg-white px-6 py-4">
        <h1 className="text-lg font-semibold text-gray-900">Release Management</h1>
        <nav className="mt-2 flex gap-4 text-sm">
          <Link
            to="/"
            className="text-gray-600 hover:text-gray-900"
            activeProps={{ className: 'font-semibold text-gray-900' }}
          >
            Dashboard
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
      <main className="p-6">
        <Outlet />
      </main>
    </div>
  );
}
