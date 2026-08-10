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
// already carries that fact, and D-05-09 already makes green either way (open or frozen).
export function ReadinessPill({ state, releaseId, documentType }: ReadinessPillProps) {
  switch (state) {
    case 'UP_TO_DATE':
      return (
        // The document viewer route (`/releases/$releaseId/documents/$documentType`) is built in a
        // later plan (05-11/05-12), so this is a plain anchor rather than a typed <Link> - the href
        // is already correct and will work once that route exists; nothing here needs to change
        // when it lands.
        <a
          href={`/releases/${releaseId}/documents/${documentType}`}
          className="rounded border border-green-700 bg-green-50 px-2 py-0.5 text-xs font-semibold text-green-700"
        >
          Up to date
        </a>
      );
    case 'STALE':
      return (
        <a
          href={`/releases/${releaseId}/documents/${documentType}`}
          className="rounded border border-amber-600 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-600"
        >
          Stale
        </a>
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
    case 'NO_ACTIVE_TEMPLATE':
      return (
        <Link to="/templates" className="whitespace-nowrap text-xs italic text-gray-400">
          No active template
        </Link>
      );
  }
}
