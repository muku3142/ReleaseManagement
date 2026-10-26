import { Link } from '@tanstack/react-router';
import type { DocumentReadinessState } from '../api/documents';
import type { DocumentType } from '../api/templates';

type ReadinessPillProps = {
  state: DocumentReadinessState;
  releaseId: string;
  documentType: DocumentType;
};

// DOC-09/D-05-08: the one four-state pill reused verbatim on /releases and release detail's
// Documents section. No "frozen" decoration is added here - the release's own Status column
// already carries that fact, and the server computes the state for open and frozen releases alike (D-15.1-05).
// UP_TO_DATE and STALE link to the document viewer route (05-12); the other two states link
// elsewhere (release detail's Documents section, or /templates).
export function ReadinessPill({ state, releaseId, documentType }: ReadinessPillProps) {
  switch (state) {
    case 'UP_TO_DATE':
      return (
        <Link
          to="/releases/$releaseId/documents/$documentType"
          params={{ releaseId, documentType }}
          className="rounded border border-green-700 bg-green-50 px-2 py-0.5 text-xs font-semibold text-green-700"
        >
          Up to date
        </Link>
      );
    case 'STALE':
      return (
        <Link
          to="/releases/$releaseId/documents/$documentType"
          params={{ releaseId, documentType }}
          className="rounded border border-amber-600 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-600"
        >
          Stale
        </Link>
      );
    case 'NOT_GENERATED':
      return (
        <Link
          to="/releases/$releaseId"
          params={{ releaseId }}
          hash="documents"
          className="whitespace-nowrap text-xs text-gray-500"
        >
          Not generated
        </Link>
      );
    case 'NO_TEMPLATE':
      return (
        <Link to="/templates" className="whitespace-nowrap text-xs italic text-gray-400">
          No template
        </Link>
      );
  }
}
