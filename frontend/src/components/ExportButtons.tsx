import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useExportDocument, type ExportFormat } from '../api/documents';
import type { DocumentType } from '../api/templates';

const SPIN_GLYPH = 'h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600';

export function Spinner() {
  return <span className={SPIN_GLYPH} aria-hidden="true" />;
}

type ExportButtonsProps = {
  releaseId: string;
  releaseName: string;
  version: string;
  documentType: DocumentType;
  revision?: number;
  disabled: boolean;
  onExported?: (format: ExportFormat) => void;
};

// One mutation instance per (row, revision) usage site - each superseded revision in the history
// disclosure gets its own ExportButtons instance, so its pending/error state never bleeds into the
// latest revision's export controls or a sibling revision's (D-05-06: every revision exportable).
export function ExportButtons({
  releaseId,
  releaseName,
  version,
  documentType,
  revision,
  disabled,
  onExported,
}: ExportButtonsProps) {
  const exportMutation = useExportDocument();

  const isPending = (format: ExportFormat) => exportMutation.isPending && exportMutation.variables?.format === format;

  const problem =
    exportMutation.isError && exportMutation.error instanceof ApiError ? exportMutation.error.problem : undefined;
  const failedFormat = exportMutation.variables?.format;

  function handleExport(format: ExportFormat) {
    exportMutation.mutate(
      { releaseId, documentType, revision, format, releaseName, version },
      { onSuccess: () => onExported?.(format) },
    );
  }

  return (
    <>
      <button
        type="button"
        onClick={() => handleExport('pdf')}
        disabled={disabled || isPending('pdf')}
        className="flex items-center gap-2 rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 disabled:opacity-50"
      >
        {isPending('pdf') && <Spinner />}
        {isPending('pdf') ? 'Exporting PDF…' : 'Export PDF'}
      </button>
      <button
        type="button"
        onClick={() => handleExport('docx')}
        disabled={disabled || isPending('docx')}
        className="flex items-center gap-2 rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700 disabled:opacity-50"
      >
        {isPending('docx') && <Spinner />}
        {isPending('docx') ? 'Exporting DOCX…' : 'Export DOCX'}
      </button>
      {problem && (
        <p role="alert" data-reason={problem.reason} className="w-full text-xs text-red-600">
          {`Couldn't export the ${failedFormat === 'docx' ? 'DOCX' : 'PDF'} — ${messageForReason(problem)} Try again.`}
        </p>
      )}
    </>
  );
}
