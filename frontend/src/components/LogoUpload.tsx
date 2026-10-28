import { useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { formatMaxSize, useRemoveLogo, useUploadLogo, type LogoLimits, type LogoMetadata } from '../api/templates';

function messageForRejection(reason: string, limits: LogoLimits | undefined): string {
  if (reason === 'LOGO_INVALID_TYPE') {
    return "This file isn't a supported image. Upload a PNG or JPEG — SVG and other formats aren't accepted.";
  }
  if (reason === 'LOGO_TOO_LARGE') {
    // Catalog still loading (limits undefined) falls back to the generic sentence rather than
    // a hardcoded number - UI-SPEC planning input #2 forbids a literal byte/pixel count here.
    return limits ? `This image is too large. Upload a file under ${formatMaxSize(limits.maxBytes)}.` : 'This image is too large.';
  }
  if (reason === 'LOGO_DIMENSIONS_EXCEEDED') {
    return limits
      ? `This image is too large. Maximum dimensions are ${limits.maxWidth}×${limits.maxHeight}px.`
      : 'This image is too large.';
  }
  return 'This image could not be uploaded.';
}

export function LogoUpload(props: { templateId: string; logo: LogoMetadata | null; limits: LogoLimits | undefined }) {
  const { templateId, logo, limits } = props;
  const inputRef = useRef<HTMLInputElement>(null);
  const [rejection, setRejection] = useState<{ reason: string; message: string } | null>(null);

  const upload = useUploadLogo();
  const remove = useRemoveLogo();

  function handleFile(file: File | undefined) {
    if (inputRef.current) inputRef.current.value = '';
    if (!file) return;
    setRejection(null);

    // Latency-optimisation pre-check only (T-04-52) - the server re-validates by content
    // regardless (plan 04-05), and produces the identical sentence when it does, so a renamed
    // .svg that slips past this check is refused server-side with no different-sounding message.
    const isSupportedType = file.type === 'image/png' || file.type === 'image/jpeg';
    const looksLikeSvg = file.name.toLowerCase().endsWith('.svg');
    if (!isSupportedType || looksLikeSvg) {
      setRejection({ reason: 'LOGO_INVALID_TYPE', message: messageForRejection('LOGO_INVALID_TYPE', limits) });
      return;
    }
    if (limits && file.size > limits.maxBytes) {
      setRejection({ reason: 'LOGO_TOO_LARGE', message: messageForRejection('LOGO_TOO_LARGE', limits) });
      return;
    }

    upload.mutate(
      { templateId, file },
      {
        onError: (error) => {
          const reason = error instanceof ApiError ? error.problem.reason : 'UNKNOWN';
          setRejection({ reason, message: messageForRejection(reason, limits) });
        },
      },
    );
  }

  function handleDrop(e: React.DragEvent<HTMLDivElement>) {
    e.preventDefault();
    handleFile(e.dataTransfer.files?.[0]);
  }

  return (
    <div>
      {upload.isPending ? (
        <div className="flex items-center gap-3 p-6">
          <span
            className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
            aria-hidden="true"
          />
          <p className="text-sm text-gray-600">Uploading…</p>
        </div>
      ) : logo ? (
        <div>
          <img
            src={`/api/templates/${templateId}/logo?v=${logo.uploadedAt}`}
            alt="Template logo"
            className="paper max-h-40 max-w-full rounded border border-gray-200 bg-white object-contain p-2"
          />
          <p className="mt-2 text-sm text-gray-600">
            {logo.filename} · {logo.width}×{logo.height}px · {Math.round(logo.byteSize / 1024)}KB
          </p>
          <div className="mt-2 flex gap-3">
            <button
              type="button"
              onClick={() => inputRef.current?.click()}
              className="text-sm text-gray-600 hover:text-gray-900"
            >
              Replace
            </button>
            <button
              type="button"
              onClick={() => remove.mutate(templateId)}
              className="text-sm text-gray-600 hover:text-gray-900"
            >
              Remove
            </button>
          </div>
        </div>
      ) : (
        <div
          onDragOver={(e) => e.preventDefault()}
          onDrop={handleDrop}
          className="rounded border-2 border-dashed border-gray-300 p-6 text-center"
        >
          <p className="text-sm text-gray-600">Drag and drop a PNG or JPEG, or</p>
          <button
            type="button"
            onClick={() => inputRef.current?.click()}
            className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700"
          >
            Choose file
          </button>
        </div>
      )}

      <input
        ref={inputRef}
        type="file"
        accept="image/png,image/jpeg"
        className="hidden"
        onChange={(e) => handleFile(e.target.files?.[0])}
      />

      {rejection && (
        <p role="alert" data-reason={rejection.reason} className="mt-2 text-sm text-red-600">
          {rejection.message}
        </p>
      )}

      {/* UI-SPEC state 3: holding a pending File across the Save button would add real
      state-management complexity for no benefit - a logo change has no meaningful "undo before
      save" need the way a half-typed body edit does. */}
    </div>
  );
}
