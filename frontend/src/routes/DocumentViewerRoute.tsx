import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams, useSearch } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { documentLogoUrl, readinessIndex, useDocument, useDocumentReadiness, type ExportFormat } from '../api/documents';
import { useRelease } from '../api/releases';
import { DOCUMENT_TYPE_LABELS, type DocumentType } from '../api/templates';
import { AiAssistPanel } from '../components/AiAssistPanel';
import { ReadinessPill } from '../components/ReadinessPill';
import { ExportButtons } from '../components/ExportButtons';
import { RenderedDocument, MarkdownBody } from '../components/RenderedDocument';

const AI_VERIFY_TYPES: readonly DocumentType[] = ['RUNBOOK', 'APPROVAL'];
const AI_MODE_TEXT = { CLEAN_UP: 'Cleaned up', SUMMARIZE: 'Summarized', CUSTOM: 'edited with a custom instruction' } as const;

const PARITY_HINT =
  "Visual parity check — open the PDF and DOCX you just exported side by side and confirm the cover, header, footer and page numbers match. There's no automated check for this (see DOC-08 in the project's recorded decisions).";

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
  const stateOf = useMemo(() => readinessIndex(readiness.data?.entries), [readiness.data]);

  // Session-only, never persisted (D-05-07 declined a verification record) - resets on remount.
  const [exportedPdf, setExportedPdf] = useState(false);
  const [exportedDocx, setExportedDocx] = useState(false);

  function handleExported(format: ExportFormat) {
    if (format === 'pdf') setExportedPdf(true);
    else setExportedDocx(true);
  }

  const [resultOpen, setResultOpen] = useState(false);
  const [savedRevision, setSavedRevision] = useState<number | null>(null);

  const loaded = documentQuery.data;
  useEffect(() => {
    if (savedRevision === null || loaded?.revision !== savedRevision) return;
    window.document.getElementById('ai-revision-banner')?.focus();
    setSavedRevision(null);
  }, [savedRevision, loaded?.revision]);

  function handleAiSaved(newRevision: number) {
    setSavedRevision(newRevision);
    navigate({ to: '.', search: { revision: undefined } });
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
  const readinessState = stateOf(releaseId, documentType) ?? 'NOT_GENERATED';
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
            disabled={resultOpen}
            title={resultOpen ? 'Save or discard the AI result first.' : undefined}
            className="rounded border border-gray-300 px-2 py-1 text-sm disabled:opacity-50"
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

      {data.aiAssisted && data.aiMode && (
        <>
          <div
            id="ai-revision-banner"
            role="note"
            tabIndex={-1}
            className="mx-6 mt-2 rounded border border-gray-200 bg-gray-100 p-3 text-sm text-gray-700"
          >
            <p className="font-semibold">AI-assisted revision.</p>
            <p>
              {`This revision was ${AI_MODE_TEXT[data.aiMode]} with the AI model and saved by ${data.generatedBy}. Compare it with the earlier revisions before relying on it.`}
            </p>
            {data.aiInstruction && (
              <details className="mt-2 text-xs">
                <summary>Instruction used</summary>
                <p className="mt-1 whitespace-pre-wrap rounded bg-white p-3 text-xs text-gray-700">{data.aiInstruction}</p>
              </details>
            )}
          </div>
          {AI_VERIFY_TYPES.includes(documentType) && (
            <div className="mx-6 mt-2 rounded border border-amber-600 bg-amber-50 p-3 text-sm text-amber-700">
              <p className="font-semibold">Verify steps before use.</p>
              <p>AI-assisted text can change commands, order, values or risk wording. Compare every step with the original before you rely on it.</p>
            </div>
          )}
        </>
      )}

      <AiAssistPanel
        document={data}
        releaseName={releaseName}
        releaseVersion={releaseVersion}
        onResultOpenChange={setResultOpen}
        onSaved={handleAiSaved}
      >
        <RenderedDocument
          chrome={data.chrome}
          logoUrl={data.hasLogo ? documentLogoUrl(releaseId, documentType, data.revision) : null}
        >
          <MarkdownBody markdown={data.markdown} blockImages={data.aiAssisted} />
        </RenderedDocument>
      </AiAssistPanel>

      {parityHintVisible && (
        <p className="mx-6 mb-6 rounded border border-gray-200 bg-gray-50 px-3 py-2 text-xs text-gray-600">
          {PARITY_HINT}
        </p>
      )}
    </div>
  );
}
