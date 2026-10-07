import { Link } from '@tanstack/react-router';
import { useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/auth';
import {
  readinessFor,
  useDocumentReadiness,
  useDocumentRevisions,
  useGenerateDocument,
  type DocumentReadinessState,
  type DocumentRevision,
} from '../api/documents';
import { DOCUMENT_TYPES, DOCUMENT_TYPE_LABELS, type DocumentType } from '../api/templates';
import { ReadinessPill } from './ReadinessPill';
import { ExportButtons, Spinner } from './ExportButtons';

type DocumentsSectionProps = {
  releaseId: string;
  releaseName: string;
  version: string;
};

// DOC-01/DOC-05/DOC-06/DOC-07/DOC-09: the screen that drives generation, export and revision
// history for all three document types. Renders identically for OPEN and FROZEN releases -
// document generation is orthogonal to whether the item set can still change.
export function DocumentsSection({ releaseId, releaseName, version }: DocumentsSectionProps) {
  const readiness = useDocumentReadiness();

  return (
    <section id="documents" className="mt-6 rounded border border-gray-200 bg-white p-4">
      <h3 className="text-base font-semibold text-gray-900">Documents</h3>
      {DOCUMENT_TYPES.map((documentType) => (
        <DocumentRow
          key={documentType}
          releaseId={releaseId}
          releaseName={releaseName}
          version={version}
          documentType={documentType}
          readinessState={
            readinessFor(readiness.data?.entries, releaseId, documentType)?.state ?? 'NOT_GENERATED'
          }
        />
      ))}
    </section>
  );
}

const GENERATING_LABEL = 'Generating…';

type DocumentRowProps = {
  releaseId: string;
  releaseName: string;
  version: string;
  documentType: DocumentType;
  readinessState: DocumentReadinessState;
};

// A dedicated component per document type: its own generate mutation, its own export mutations
// (via ExportButtons), its own disclosure state - so a Release Notes failure or pending state can
// never bleed into the Runbook or Approval row (T-05-65).
function DocumentRow({ releaseId, releaseName, version, documentType, readinessState }: DocumentRowProps) {
  const typeLabel = DOCUMENT_TYPE_LABELS[documentType];
  const revisions = useDocumentRevisions(releaseId, documentType);
  const generateMutation = useGenerateDocument();
  const [historyOpen, setHistoryOpen] = useState(false);

  const revisionList = revisions.data?.revisions ?? [];
  const hasRevisions = revisionList.length > 0;
  const latest = revisionList.find((r) => r.latest);
  const total = revisionList.length;

  const generateProblem =
    generateMutation.isError && generateMutation.error instanceof ApiError ? generateMutation.error.problem : undefined;

  const generateBusy = generateMutation.isPending;

  return (
    <div className="border-b border-gray-100 py-3 last:border-b-0">
      <div className="flex items-center justify-between">
        <h4 className="text-base font-semibold text-gray-900">{typeLabel}</h4>
        <ReadinessPill state={readinessState} releaseId={releaseId} documentType={documentType} />
      </div>

      <p className="mt-1 text-sm text-gray-600">
        {readinessState === 'NO_TEMPLATE' && (
          <>
            {`No ${typeLabel} template — create one to generate this document.`}{' '}
            <Link to="/templates" className="text-blue-600 underline">
              Go to templates
            </Link>
          </>
        )}
        {readinessState === 'NOT_GENERATED' && "This document hasn't been generated yet."}
        {readinessState === 'STALE' && latest && (
          <>
            {`Revision ${latest.revision} of ${total} · generated ${new Date(latest.generatedAt).toLocaleString()} by ${latest.generatedBy}. The release has changed since this was generated.`}
          </>
        )}
        {readinessState === 'UP_TO_DATE' && latest && (
          <>
            {`Revision ${latest.revision} of ${total} · generated ${new Date(latest.generatedAt).toLocaleString()} by ${latest.generatedBy}.`}
          </>
        )}
      </p>

      <div className="mt-2 flex flex-wrap items-center gap-2">
        {readinessState === 'NO_TEMPLATE' && (
          <Link to="/templates" className="text-sm text-blue-600 underline">
            Go to templates
          </Link>
        )}

        {readinessState !== 'NO_TEMPLATE' && (
          <button
            type="button"
            onClick={() => generateMutation.mutate({ releaseId, documentType })}
            disabled={generateBusy}
            className={
              hasRevisions
                ? readinessState === 'STALE'
                  ? 'flex items-center gap-2 rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50'
                  : 'flex items-center gap-2 rounded border border-gray-300 px-4 py-2 text-sm font-semibold text-gray-700 disabled:opacity-50'
                : 'flex items-center gap-2 rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50'
            }
          >
            {generateBusy && <Spinner />}
            {generateBusy ? GENERATING_LABEL : `${hasRevisions ? 'Regenerate' : 'Generate'} ${typeLabel}`}
          </button>
        )}

        {hasRevisions && (
          <>
            <DocumentViewLink releaseId={releaseId} documentType={documentType} disabled={generateBusy} />
            <ExportButtons
              releaseId={releaseId}
              releaseName={releaseName}
              version={version}
              documentType={documentType}
              disabled={generateBusy}
            />
          </>
        )}
      </div>

      {generateProblem && (
        <p role="alert" data-reason={generateProblem.reason} className="mt-2 text-sm text-red-600">
          {`Couldn't generate ${typeLabel} — ${messageForReason(generateProblem)} Try again.`}
        </p>
      )}

      {hasRevisions && (
        <div className="mt-2">
          <button
            type="button"
            onClick={() => setHistoryOpen((open) => !open)}
            aria-expanded={historyOpen}
            aria-label={historyOpen ? 'Hide revision history' : 'Show revision history'}
            className="rounded px-1 text-sm text-gray-600 hover:text-gray-900 focus-visible:ring-2 focus-visible:ring-blue-600"
          >
            {historyOpen ? '▾' : '▸'} Revision history ({total})
          </button>

          {historyOpen && (
            <ul className="mt-2 space-y-3">
              {[...revisionList]
                .sort((a, b) => b.revision - a.revision)
                .map((revision) => (
                  <RevisionRow
                    key={revision.revision}
                    releaseId={releaseId}
                    releaseName={releaseName}
                    version={version}
                    documentType={documentType}
                    revision={revision}
                    disabled={generateBusy}
                  />
                ))}
            </ul>
          )}
        </div>
      )}
    </div>
  );
}

type DocumentViewLinkProps = {
  releaseId: string;
  documentType: DocumentType;
  revision?: number;
  disabled: boolean;
  className?: string;
};

function DocumentViewLink({ releaseId, documentType, revision, disabled, className }: DocumentViewLinkProps) {
  if (disabled) {
    return (
      <span className={className ?? 'text-sm text-gray-400'} aria-disabled="true">
        View
      </span>
    );
  }
  return (
    <Link
      to="/releases/$releaseId/documents/$documentType"
      params={{ releaseId, documentType }}
      search={revision === undefined ? undefined : { revision }}
      className={className ?? 'text-sm text-blue-600 underline'}
    >
      View
    </Link>
  );
}

type RevisionRowProps = {
  releaseId: string;
  releaseName: string;
  version: string;
  documentType: DocumentType;
  revision: DocumentRevision;
  disabled: boolean;
};

function RevisionRow({ releaseId, releaseName, version, documentType, revision, disabled }: RevisionRowProps) {
  return (
    <li className="text-xs text-gray-600">
      <p>
        {`Revision ${revision.revision} `}
        {revision.latest ? '(latest)' : '(superseded)'}
        {` · generated ${new Date(revision.generatedAt).toLocaleString()} by ${revision.generatedBy}`}
      </p>
      <div className="mt-1 flex flex-wrap items-center gap-2">
        <DocumentViewLink
          releaseId={releaseId}
          documentType={documentType}
          revision={revision.revision}
          disabled={disabled}
          className={disabled ? 'text-xs text-gray-400' : 'text-xs text-blue-600 underline'}
        />
        <ExportButtons
          releaseId={releaseId}
          releaseName={releaseName}
          version={version}
          documentType={documentType}
          revision={revision.revision}
          disabled={disabled}
        />
      </div>
    </li>
  );
}
