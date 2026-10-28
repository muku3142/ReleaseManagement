import { Link } from '@tanstack/react-router';
import type { RoadmapAddResult as AddResult } from '../api/roadmap';
import { messageForRejectedKey } from '../api/reasonMessages';

const MAX_NAMED_DUPLICATES = 5;
const LINK = 'text-blue-600 underline';

// Done and archived are switched on (and products dropped) so the linked row is visible whatever its state.
const ViewIt = ({ itemId }: { itemId: string }) => (
  <Link to="/roadmap" search={{ item: itemId, done: true, archived: true }} className={LINK}>
    View it
  </Link>
);

export function RoadmapAddResult({ result, showRoadmapLink }: { result: AddResult; showRoadmapLink?: boolean }) {
  const { added, alreadyPresent, notFound, rejected } = result;
  const named = alreadyPresent.slice(0, MAX_NAMED_DUPLICATES);
  const more = alreadyPresent.length - named.length;

  return (
    <div className="space-y-1">
      {added.length > 0 && (
        <>
          <p>Added {added.length === 1 ? '1 item' : `${added.length} items`} to the roadmap.</p>
          <p className="text-xs text-gray-600">
            {added.length === 1 ? 'It has' : 'They have'} no scores yet. Set Reach, Impact, Confidence and Effort to rank
            them.
          </p>
          {showRoadmapLink && (
            <p>
              <Link to="/roadmap" className={LINK}>
                Go to Roadmap
              </Link>
            </p>
          )}
        </>
      )}
      {added.length === 0 && alreadyPresent.length > 0 && (
        <p>
          Nothing added.{' '}
          {alreadyPresent.length === 1
            ? `${alreadyPresent[0].jiraKey} is already on the roadmap.`
            : `All ${alreadyPresent.length} are already on the roadmap.`}
          {alreadyPresent.length === 1 && (
            <>
              {' '}
              <ViewIt itemId={alreadyPresent[0].itemId} />
            </>
          )}
        </p>
      )}
      {added.length > 0 &&
        named.map((duplicate) => (
          <p key={duplicate.itemId}>
            {duplicate.jiraKey} is already on the roadmap. <ViewIt itemId={duplicate.itemId} />
          </p>
        ))}
      {added.length > 0 && more > 0 && <p>and {more} more already on the roadmap.</p>}
      {notFound.map((key) => (
        <p key={key}>{key} was not found.</p>
      ))}
      {rejected.map(({ jiraKey, reason }) => (
        <p key={jiraKey}>
          {jiraKey}: {messageForRejectedKey(jiraKey, reason)}
        </p>
      ))}
    </div>
  );
}
