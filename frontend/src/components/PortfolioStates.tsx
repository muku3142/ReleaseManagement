import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';

export function SectionLoading({ text }: { text: string }) {
  return (
    <div className="flex items-center gap-3">
      <span
        className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
        aria-hidden="true"
      />
      <p className="text-sm text-gray-600">{text}</p>
    </div>
  );
}

export function SectionError({ what, error, onRetry }: { what: string; error: unknown; onRetry: () => void }) {
  const problem = error instanceof ApiError ? error.problem : undefined;
  return (
    <div>
      <p role="alert" data-reason={problem?.reason} className="text-sm text-red-600">
        Couldn&apos;t load {what} — {problem ? messageForReason(problem).replace(/\.$/, '') : 'something went wrong'}.
        Try again.
      </p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700"
      >
        Try again
      </button>
    </div>
  );
}
