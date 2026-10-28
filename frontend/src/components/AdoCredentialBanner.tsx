import { Link } from '@tanstack/react-router';
import { useAdoCredential } from '../api/adoCredential';
import { Alert } from '@/components/ui/alert';

export function AdoCredentialBanner() {
  const credential = useAdoCredential();
  const { state, expiresOn } = credential.data ?? {};
  if ((state !== 'EXPIRING' && state !== 'EXPIRED') || !expiresOn) return null;

  const date = new Date(expiresOn + 'T00:00:00').toLocaleDateString();
  const expired = state === 'EXPIRED';

  return (
    <Alert
      variant="warning"
      role="alert"
      data-reason={expired ? 'ADO_CREDENTIAL_EXPIRED' : 'ADO_CREDENTIAL_EXPIRING'}
      className="mx-6 mt-4 w-auto lg:mx-8"
    >
      <div>
        {expired
          ? `The Azure DevOps token expired on ${date}. Commit lists fall back to commits that name the ticket key.`
          : `The Azure DevOps token expires on ${date}.`}{' '}
        <Link to="/settings" className="font-semibold underline underline-offset-4">
          Update the Azure DevOps token
        </Link>
      </div>
    </Alert>
  );
}
