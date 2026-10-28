import { useRoadmapHistory } from '../api/roadmap';
import { Spinner } from '../components/ExportButtons';
import { SMALL_BUTTON } from '../components/EnvironmentLadderSection';
import { FIELD_LABELS } from './rubric';
import { formatChange } from './format';

const FIELD_NAMES = FIELD_LABELS as Record<string, string>;

export function InputHistoryPanel({ itemId }: { itemId: string }) {
  const history = useRoadmapHistory(itemId, true);

  if (history.isPending) {
    return (
      <p className="flex items-center gap-2 text-xs text-gray-600">
        <Spinner />
        Loading history…
      </p>
    );
  }
  if (history.isError) {
    return (
      <div role="alert" className="flex items-center gap-2 text-xs text-red-600">
        <span>Couldn&apos;t load the history — {history.error.message}. Try again.</span>
        <button type="button" onClick={() => history.refetch()} className={SMALL_BUTTON}>
          Try again
        </button>
      </div>
    );
  }

  const entries = [...history.data.entries].sort((a, b) => b.changedAt.localeCompare(a.changedAt));
  if (entries.length === 0) return <p className="text-xs text-gray-600">No input changes yet.</p>;

  return (
    <div className="max-h-64 overflow-y-auto rounded border border-gray-200 bg-white">
      <table className="min-w-full text-xs">
        <thead>
          <tr className="text-left font-semibold text-gray-700">
            <th scope="col" className="px-3 py-2">When</th>
            <th scope="col" className="px-3 py-2">Who</th>
            <th scope="col" className="px-3 py-2">Input</th>
            <th scope="col" className="px-3 py-2">Change</th>
          </tr>
        </thead>
        <tbody>
          {entries.map((entry, index) => (
            <tr key={`${entry.changedAt}-${entry.field}-${index}`} className="border-t border-gray-100">
              <td className="px-3 py-2">{new Date(entry.changedAt).toLocaleString()}</td>
              <td className="px-3 py-2">{entry.changedBy}</td>
              <td className="px-3 py-2">{FIELD_NAMES[entry.field] ?? entry.field}</td>
              <td className="px-3 py-2">
                {formatChange(entry.field, entry.oldValue, entry.newValue)}
                {entry.kind === 'OVERRIDDEN' && <span className="block text-xs text-gray-600">Overridden: {entry.reason}</span>}
                {entry.kind === 'RESET' && <span className="block text-xs text-gray-600">Reset to calculated</span>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
