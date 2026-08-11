import { useState, type ReactNode } from 'react';
import { Link, useNavigate, useParams, useSearch } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/auth';
import { documentLogoUrl, readinessFor, useDocument, useDocumentReadiness, type ExportFormat } from '../api/documents';
import { useRelease } from '../api/releases';
import { DOCUMENT_TYPE_LABELS, type DocumentType } from '../api/templates';
import { ReadinessPill } from '../components/ReadinessPill';
import { ExportButtons } from '../components/ExportButtons';
import { RenderedDocument, md, chipUnknownMarkers } from '../components/RenderedDocument';

const PARITY_HINT =
  "Visual parity check — open the PDF and DOCX you just exported side by side and confirm the cover, header, footer and page numbers match. There's no automated check for this (see DOC-08 in the project's recorded decisions).";

// The snapshot is already Mustache-resolved (D-05-05), so this needs markdown-it only. A render
// failure here is defensive, not an expected path - DOC generation only succeeds from a template
// that already passed save-time validation - so it falls back to TemplatePreview's own neutral panel.
function renderBody(markdown: string): ReactNode {
  try {
    const html = chipUnknownMarkers(md.render(markdown));
    return <div className="prose prose-gray max-w-none p-4" dangerouslySetInnerHTML={{ __html: html }} />;
  } catch {
    return (
      <div className="m-4 bg-gray-50 p-4 text-sm text-gray-600">
        Preview unavailable — check your template syntax (e.g., every {'{{#x}}'} needs a matching {'{{/x}}'}).
      </div>
    );
  }
}

// 05-UI-SPEC screen 2: the document viewer. Params/search come from router.tsx's registration;
// revision defaults to the latest whenever ?revision is absent (D-05-06). Every revision is an
// independent immutable snapshot, so switching the picker is a full data swap, not a client toggle.
export function DocumentViewerRoute() {
  const { releaseId, documentType: documentTypeParam } = useParams({
    from: '/authenticated/releases/$releaseId/documents/$documentType',
  });
  const documentType = documentTypeParam as DocumentType;
  const { revision } = useSearch({ from: '/authenticated/releases/$releaseId/documents/$documentType' });
  const navigate = useNavigate();

  const documentQuery = useDocument(releaseId, documentType, revision);
  const release = useRelease(releaseId);
  const readiness = useDocumentReadiness();

  // Session-only, never persisted (D-05-07 declined a verification record) - resets on remount.
  const [exportedPdf, setExportedPdf] = useState(false);
  const [exportedDocx, setExportedDocx] = useState(false);

  function handleExported(format: ExportFormat) {
    if (format === 'pdf') setExportedPdf(true);
    else setExportedDocx(true);
  }

  const problem =
    documentQuery.isError && documentQuery.error instanceof ApiError ? documentQuery.error.problem : undefined;

  if (documentQuery.isPending) {
    return (
      <div className="flex items-center gap-3 p-6">
        <span
          className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
          aria-hidden="true"
        />
        <p className="text-sm text-gray-600">Loading document…</p>
      </div>
    );
  }

  // Retrying a 404 on a fixed (release, type, revision) cannot succeed, matching RELEASE_NOT_FOUND's
  // precedent - no "Try again" affordance.
  if (problem && problem.reason === 'DOCUMENT_NOT_FOUND') {
    return (
      <div className="p-6">
        <p role="alert" data-reason="DOCUMENT_NOT_FOUND" className="text-sm text-red-600">
          This document doesn&apos;t exist yet.
        </p>
        <Link
          to="/releases/$releaseId"
          params={{ releaseId }}
          className="mt-2 inline-block text-sm text-blue-600 underline"
        >
          Back to release
        </Link>
      </div>
    );
  }

  if (problem) {
    return (
      <div className="p-6">
        <p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
          {messageForReason(problem)}
        </p>
        <button
          type="button"
          onClick={() => documentQuery.refetch()}
          className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700"
        >
          Try again
        </button>
      </div>
    );
  }

  const data = documentQuery.data;
  if (!data) return null;

  const releaseName = release.data?.name ?? '';
  const releaseVersion = release.data?.version ?? '';
  const readinessState = readinessFor(readiness.data?.entries, releaseId, documentType)?.state ?? 'NOT_GENERATED';
  const superseded = data.revision !== data.latestRevision;
  const parityHintVisible = exportedPdf && exportedDocx;

  return (
    <div className="w-full">
      <div className="flex items-center justify-between bg-white p-6">
        <div className="flex items-center gap-3">
          <Link
            to="/releases/$releaseId"
            params={{ releaseId }}
            hash="documents"
            className="text-sm text-gray-600 hover:text-gray-900"
          >
            ← Back to release
          </Link>
          <h2 className="text-base font-semibold">
            {`${DOCUMENT_TYPE_LABELS[documentType]} — ${releaseName} ${releaseVersion}`}
          </h2>
          <ReadinessPill state={readinessState} releaseId={releaseId} documentType={documentType} />
        </div>

        <div className="flex items-center gap-2">
          <select
            aria-label="Revision"
            value={data.revision}
            onChange={(e) => navigate({ to: '.', search: { revision: Number(e.target.value) } })}
            className="rounded border border-gray-300 px-2 py-1 text-sm"
          >
            {Array.from({ length: data.latestRevision }, (_, i) => data.latestRevision - i).map((n) => (
              <option key={n} value={n}>
                {n === data.latestRevision ? `Revision ${n} (latest)` : `Revision ${n}`}
              </option>
            ))}
          </select>
          <ExportButtons
            releaseId={releaseId}
            releaseName={releaseName}
            version={releaseVersion}
            documentType={documentType}
            revision={data.revision}
            disabled={false}
            onExported={handleExported}
          />
        </div>
      </div>

      {data.templateDeleted && (
        <p className="px-6 text-xs italic text-gray-500">
          Generated from a template that has since been deleted. The content shown here is unaffected.
        </p>
      )}

      {superseded && (
        <div className="mx-6 mt-2 rounded bg-gray-100 p-3 text-sm text-gray-700">
          {`You're viewing Revision ${data.revision}, generated ${new Date(data.generatedAt).toLocaleString()}. This has been superseded by Revision ${data.latestRevision}.`}{' '}
          <Link
            to="/releases/$releaseId/documents/$documentType"
            params={{ releaseId, documentType }}
            search={{ revision: data.latestRevision }}
            className="text-blue-600 underline"
          >
            View latest
          </Link>
        </div>
      )}

      <div className="m-6 rounded border border-gray-200 bg-white">
        <RenderedDocument
          chrome={data.chrome}
          logoUrl={data.hasLogo ? documentLogoUrl(releaseId, documentType, data.revision) : null}
        >
          {renderBody(data.markdown)}
        </RenderedDocument>
      </div>

      {parityHintVisible && (
        <p className="mx-6 mb-6 rounded border border-gray-200 bg-gray-50 px-3 py-2 text-xs text-gray-600">
          {PARITY_HINT}
        </p>
      )}
    </div>
  );
}
