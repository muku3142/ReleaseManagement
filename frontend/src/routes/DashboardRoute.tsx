import { Link } from '@tanstack/react-router';

export function DashboardRoute() {
  return (
    <div>
      <h2 className="text-base font-semibold text-gray-900">Dashboard</h2>
      <p className="mt-2 text-sm text-gray-600">
        No releases yet. Release assembly arrives in a later phase.
      </p>
      <Link to="/settings" className="mt-2 inline-block text-sm text-blue-600 hover:underline">
        Go to Settings
      </Link>
    </div>
  );
}
