import { Link } from '@tanstack/react-router';
import { useCredentialInvalid } from '../api/credentialStatus';
import { Alert } from '@/components/ui/alert';

export function CredentialAlert() {
  const invalid = useCredentialInvalid();
  if (!invalid) return null;

  return (
    <Alert variant="warning" role="alert" data-reason="INVALID_CREDENTIALS" className="mx-6 mt-4 w-auto lg:mx-8">
      <div>
        Your Jira credential is no longer valid.{' '}
        <Link to="/settings" className="font-semibold underline underline-offset-4">
          Replace your Jira API token
        </Link>
      </div>
    </Alert>
  );
}
