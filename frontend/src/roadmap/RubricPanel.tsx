import { RUBRIC } from './rubric';

const ENTRIES = [
  { id: 'rubric-reach', heading: 'Reach (clients)', text: RUBRIC.reach },
  { id: 'rubric-impact', heading: 'Impact', text: RUBRIC.impact },
  { id: 'rubric-confidence', heading: 'Confidence', text: RUBRIC.confidence },
  { id: 'rubric-effort', heading: 'Effort (person-weeks)', text: RUBRIC.effort },
] as const;

export function RubricPanel() {
  return (
    <details open className="rounded border border-gray-200 bg-white p-4">
      <summary className="cursor-pointer text-sm font-semibold text-gray-900 focus:outline-none focus-visible:ring-2 focus-visible:ring-blue-600">
        How scoring works
      </summary>
      <p className="mt-3 text-sm text-gray-900">{RUBRIC.formula}</p>
      <div className="mt-3 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        {ENTRIES.map((entry) => (
          <div key={entry.id}>
            <h3 className="text-sm font-semibold text-gray-900">{entry.heading}</h3>
            <p id={entry.id} className="mt-1 text-xs text-gray-600">
              {entry.text}
            </p>
          </div>
        ))}
      </div>
    </details>
  );
}
