import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { apiFetch, apiFetchBlob, triggerDownload } from './client';
import { DOCUMENT_TYPE_LABELS, type ChromeFields, type DocumentType } from './templates';

// DOC-09/D-05-09: the four states DocumentReadinessService computes server-side. The frontend
// renders whichever one it is given and never re-derives staleness from a raw timestamp (T-05-55).
export type DocumentReadinessState = 'NO_ACTIVE_TEMPLATE' | 'NOT_GENERATED' | 'STALE' | 'UP_TO_DATE';
export type ReadinessEntry = { releaseId: string; documentType: DocumentType; state: DocumentReadinessState };
export type ReadinessResponse = { entries: ReadinessEntry[] };

export type DocumentRevision = { revision: number; latest: boolean; generatedAt: string; generatedBy: string };
export type DocumentRevisionListResponse = { revisions: DocumentRevision[] };

// Mirrors DocumentResponse exactly (05-04-SUMMARY.md). The stored template's full body is
// deliberately not part of this shape either - it is provenance, not something any screen renders.
export type GeneratedDocument = {
  id: string;
  releaseId: string;
  documentType: DocumentType;
  revision: number;
  latestRevision: number;
  markdown: string;
  templateName: string;
  templateDeleted: boolean;
  hasLogo: boolean;
  chrome: ChromeFields;
  generatedAt: string;
  generatedBy: string;
};

export type ExportFormat = 'pdf' | 'docx';

export const readinessQueryKey = () => ['documents', 'readiness'] as const;
export const documentQueryKey = (releaseId: string, documentType: DocumentType, revision?: number) =>
  ['document', releaseId, documentType, revision ?? 'latest'] as const;
export const documentRevisionsQueryKey = (releaseId: string, documentType: DocumentType) =>
  ['document-revisions', releaseId, documentType] as const;

export function useDocumentReadiness() {
  return useQuery({
    queryKey: readinessQueryKey(),
    queryFn: () => apiFetch<ReadinessResponse>('/api/documents/readiness'),
  });
}

// O(1) per table cell rather than an array scan per cell - the whole point of loading the grid in
// one request is wasted if every cell then re-scans it.
export function readinessFor(
  entries: ReadinessEntry[] | undefined,
  releaseId: string,
  documentType: DocumentType,
): ReadinessEntry | undefined {
  return entries?.find((e) => e.releaseId === releaseId && e.documentType === documentType);
}

function documentPath(releaseId: string, documentType: DocumentType, revision?: number): string {
  const base = `/api/releases/${releaseId}/documents/${documentType}`;
  return revision === undefined ? base : `${base}?revision=${revision}`;
}

export function useDocument(releaseId: string, documentType: DocumentType, revision?: number) {
  return useQuery({
    queryKey: documentQueryKey(releaseId, documentType, revision),
    queryFn: () => apiFetch<GeneratedDocument>(documentPath(releaseId, documentType, revision)),
    // A 404 on a fixed (release, type, revision) is a permanent answer, matching
    // useRelease/useTemplate - retrying it three times only delays DOCUMENT_NOT_FOUND.
    retry: false,
  });
}

export function useDocumentRevisions(releaseId: string, documentType: DocumentType) {
  return useQuery({
    queryKey: documentRevisionsQueryKey(releaseId, documentType),
    queryFn: () =>
      apiFetch<DocumentRevisionListResponse>(`/api/releases/${releaseId}/documents/${documentType}/revisions`),
  });
}

// A generate that leaves a stale pill on screen is the whole feature failing, so every query that
// could show this document's state is invalidated: the release list (item counts/status), the
// cross-release readiness grid, this exact document read and its revision history.
export function useGenerateDocument() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (vars: { releaseId: string; documentType: DocumentType }) =>
      apiFetch<GeneratedDocument>(documentPath(vars.releaseId, vars.documentType), { method: 'POST' }),
    onSuccess: (document, vars) => {
      queryClient.invalidateQueries({ queryKey: ['releases'] });
      queryClient.invalidateQueries({ queryKey: readinessQueryKey() });
      queryClient.invalidateQueries({ queryKey: documentQueryKey(vars.releaseId, vars.documentType) });
      queryClient.invalidateQueries({ queryKey: documentRevisionsQueryKey(vars.releaseId, vars.documentType) });
      queryClient.setQueryData(documentQueryKey(vars.releaseId, vars.documentType, document.revision), document);
    },
  });
}

// An URL.createObjectURL download cannot read the server's sanitised Content-Disposition name, so
// the client composes its own using the same [A-Za-z0-9 ._-] allowlist plan 05-08 applies
// server-side - a hostile release name/version cannot direct where the browser writes the file.
function sanitizeFilename(candidate: string): string {
  const cleaned = candidate.replace(/[^A-Za-z0-9 ._-]/g, '_').trim();
  return cleaned.length > 0 ? cleaned : 'document';
}

function exportFilename(releaseName: string, version: string, documentType: DocumentType, format: ExportFormat): string {
  const name = sanitizeFilename(`${releaseName} ${version} ${DOCUMENT_TYPE_LABELS[documentType]}`);
  return `${name}.${format}`;
}

// A mutation, not a query: exporting is an action with pending/error states (UI-SPEC's "Exporting
// PDF..."), and its result (a downloaded file) must never be cached.
export function useExportDocument() {
  return useMutation({
    mutationFn: async (vars: {
      releaseId: string;
      documentType: DocumentType;
      revision?: number;
      format: ExportFormat;
      releaseName: string;
      version: string;
    }) => {
      const path = `/api/releases/${vars.releaseId}/documents/${vars.documentType}/export.${vars.format}`;
      const url = vars.revision === undefined ? path : `${path}?revision=${vars.revision}`;
      const blob = await apiFetchBlob(url);
      triggerDownload(blob, exportFilename(vars.releaseName, vars.version, vars.documentType, vars.format));
    },
  });
}

// The logo endpoint is a normal same-origin GET (like a template's own logo), so it needs no fetch
// wrapper - just a URL for an <img src>.
export function documentLogoUrl(releaseId: string, documentType: DocumentType, revision?: number): string {
  const base = `/api/releases/${releaseId}/documents/${documentType}/logo`;
  return revision === undefined ? base : `${base}?revision=${revision}`;
}
