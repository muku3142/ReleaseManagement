import { Link } from '@tanstack/react-router';
import { CircleCheck, TriangleAlert } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { usePulseOnChange } from '@/hooks/usePulseOnChange';
import { cn } from '@/lib/utils';
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
// elsewhere (release detail's Documents section, or /templates). Variants follow the D-23-05 status map.
export function ReadinessPill({ state, releaseId, documentType }: ReadinessPillProps) {
  const { pulsing, onAnimationEnd } = usePulseOnChange(state);
  const pulse = cn(pulsing && 'motion-safe:animate-status-pulse');
  switch (state) {
    case 'UP_TO_DATE':
      return (
        <Badge variant="success" asChild className={pulse} onAnimationEnd={onAnimationEnd}>
          <Link
            to="/releases/$releaseId/documents/$documentType"
            params={{ releaseId, documentType }}
            className="hover:underline"
          >
            <CircleCheck className="size-3.5" aria-hidden="true" />
            Up to date
          </Link>
        </Badge>
      );
    case 'STALE':
      return (
        <Badge variant="warning" asChild className={pulse} onAnimationEnd={onAnimationEnd}>
          <Link
            to="/releases/$releaseId/documents/$documentType"
            params={{ releaseId, documentType }}
            className="hover:underline"
          >
            <TriangleAlert className="size-3.5" aria-hidden="true" />
            Out of date
          </Link>
        </Badge>
      );
    case 'NOT_GENERATED':
      return (
        <Badge variant="neutral" asChild className={pulse} onAnimationEnd={onAnimationEnd}>
          <Link to="/releases/$releaseId" params={{ releaseId }} hash="documents" className="hover:underline">
            Not generated
          </Link>
        </Badge>
      );
    case 'NO_TEMPLATE':
      return (
        <Badge variant="neutral" asChild className={pulse} onAnimationEnd={onAnimationEnd}>
          <Link to="/templates" className="hover:underline">
            No template
          </Link>
        </Badge>
      );
  }
}
