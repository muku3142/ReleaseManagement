import { Link } from '@tanstack/react-router';
import { useCredentialInvalid } from '../api/credentialStatus';

export function CredentialAlert() {
  const invalid = useCredentialInvalid();
  if (!invalid) return null;

  return (
    <div role="alert" data-reason="INVALID_CREDENTIALS" className="border-b border-amber-300 bg-amber-50 px-6 py-3 text-sm text-amber-900">
      Your Jira credential is no longer valid.{' '}
      <Link to="/settings" className="font-medium underline">
        Replace your Jira API token
      </Link>
    </div>
  );
}
