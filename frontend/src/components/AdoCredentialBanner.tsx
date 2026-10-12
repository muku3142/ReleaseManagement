import { Link } from '@tanstack/react-router';
import { useAdoCredential } from '../api/adoCredential';

export function AdoCredentialBanner() {
  const credential = useAdoCredential();
  const { state, expiresOn } = credential.data ?? {};
  if ((state !== 'EXPIRING' && state !== 'EXPIRED') || !expiresOn) return null;

  const date = new Date(expiresOn + 'T00:00:00').toLocaleDateString();
  const expired = state === 'EXPIRED';

  return (
    <div
      role="alert"
      data-reason={expired ? 'ADO_CREDENTIAL_EXPIRED' : 'ADO_CREDENTIAL_EXPIRING'}
      className="border-b border-amber-300 bg-amber-50 px-6 py-3 text-sm text-amber-900"
    >
      {expired
        ? `The Azure DevOps token expired on ${date}. Commit lists fall back to commits that name the ticket key.`
        : `The Azure DevOps token expires on ${date}.`}{' '}
      <Link to="/settings" className="font-medium underline">
        Update the Azure DevOps token
      </Link>
    </div>
  );
}
