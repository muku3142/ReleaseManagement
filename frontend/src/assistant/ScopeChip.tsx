export function ScopeChip({ label, onRemove }: { label: string; onRemove?: () => void }) {
  return (
    <span className="inline-flex items-center gap-1 rounded border border-gray-300 bg-white px-2 py-0.5 text-xs text-gray-700">
      {`Release: ${label}`}
      {onRemove && (
        <button
          type="button"
          aria-label={`Remove release scope ${label}`}
          onClick={onRemove}
          className="focus-visible:ring-2 focus-visible:ring-blue-600"
        >
          ×
        </button>
      )}
    </span>
  );
}
