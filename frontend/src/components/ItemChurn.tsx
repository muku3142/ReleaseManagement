import type { ItemChurn as ItemChurnFigures } from '../api/releases';

const TOOLTIP =
  "Commits before the ticket first reached Done, reopens after Done, and commits after first Done. This release's churn as of the last refresh - not Jira cycle time.";

export function ItemChurn({ churn }: { churn: ItemChurnFigures | null }) {
  if (churn === null) {
    return (
      <span className="text-xs italic text-gray-400" title={TOOLTIP} aria-label={TOOLTIP}>
        No data
      </span>
    );
  }
  const commits =
    churn.commitsToDone === null ? (
      'No commit data'
    ) : (
      <>
        <span className="tabular-nums">{churn.commitsToDone}</span> to Done
      </>
    );
  const reopens =
    churn.reopens === null ? (
      'reopens: no data'
    ) : (
      <>
        <span className="tabular-nums">{churn.reopens}</span> {churn.reopens === 1 ? 'reopen' : 'reopens'}
      </>
    );
  return (
    <span className="inline-flex flex-col text-xs text-gray-700" title={TOOLTIP} aria-label={TOOLTIP}>
      <span>
        {commits} · {reopens}
        {churn.postDoneCommits !== null && (
          <>
            {' · '}
            <span className="tabular-nums">{churn.postDoneCommits}</span> after Done
          </>
        )}
      </span>
      {churn.keyTaggedOnly && <span className="text-xs text-gray-500">Key-tagged commits only</span>}
      {churn.outlier && <span className="text-xs font-semibold text-amber-700">Outlier</span>}
    </span>
  );
}
