import { Fragment, useEffect, useRef } from 'react';
import type { RoadmapItem } from '../api/roadmap';
import { Spinner } from '../components/ExportButtons';
import { SMALL_BUTTON } from '../components/EnvironmentLadderSection';
import { PRODUCT_CHIP } from '../components/ProductsEditor';
import { InputCell } from './InputCell';
import { ItemDetailRow } from './ItemDetailRow';
import { relativeTime } from './format';
import { EFFORT_UNIT, FIELD_LABELS, REACH_UNIT, STATUS_LABELS } from './rubric';

const TH = 'px-4 py-2 text-left font-semibold text-gray-700';
const TD = 'px-4 py-2 align-top';
const UNAVAILABLE_TITLE = 'Jira no longer returns this issue. The last fetched summary and status are kept.';
const COLUMN_COUNT = 12;

const UnitLine = ({ unit }: { unit: string }) => (
  <span className="block text-xs font-normal text-gray-600">{unit}</span>
);

const Dash = ({ title }: { title?: string }) => (
  <span className="text-gray-400" title={title}>
    —
  </span>
);

const missingText = (item: RoadmapItem) => item.missing.map((field) => FIELD_LABELS[field]).join(', ');

type Props = {
  items: RoadmapItem[];
  openIds: ReadonlySet<string>;
  onToggle: (id: string) => void;
  refreshDisabled: boolean;
  onRefreshItem: (item: RoadmapItem) => void;
  refreshingId?: string | null;
  focusId?: string;
  onInputSaved?: (item: RoadmapItem) => void;
};

function JiraCell({ item, props }: { item: RoadmapItem; props: Props }) {
  if (item.source !== 'JIRA') return <Dash />;
  const pending = props.refreshingId === item.id;
  return (
    <div className="space-y-1 text-xs text-gray-600">
      <div>{item.jiraStatus ?? 'unknown status'}</div>
      {item.jiraUnavailable && (
        <span
          title={UNAVAILABLE_TITLE}
          className="inline-block rounded border border-amber-600 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-700"
        >
          unavailable in Jira
        </span>
      )}
      {item.jiraSyncedAt && (
        <div title={new Date(item.jiraSyncedAt).toLocaleString()}>
          {item.jiraUnavailable ? 'Last fetched' : 'Fetched'} {relativeTime(item.jiraSyncedAt)}
        </div>
      )}
      <div className="flex items-center gap-1">
        <button
          type="button"
          disabled={props.refreshDisabled || pending}
          aria-label={`Refresh ${item.jiraKey} from Jira`}
          onClick={() => props.onRefreshItem(item)}
          className={SMALL_BUTTON}
        >
          {pending ? 'Refreshing…' : 'Refresh'}
        </button>
        {pending && <Spinner />}
      </div>
    </div>
  );
}

export function RoadmapTable(props: Props) {
  const { items, openIds, onToggle, focusId, onInputSaved } = props;
  const focusRef = useRef<HTMLHeadingElement>(null);
  const focused = useRef<string | undefined>(undefined);
  const unscored = items.filter((item) => item.rank === null).length;
  const firstUnscored = items.find((item) => item.rank === null)?.id;
  const focusOpen = focusId !== undefined && openIds.has(focusId) && items.some((item) => item.id === focusId);

  useEffect(() => {
    if (!focusOpen || focused.current === focusId) return;
    focused.current = focusId;
    const heading = focusRef.current;
    if (!heading) return;
    const smooth = !window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    heading.scrollIntoView?.({ behavior: smooth ? 'smooth' : 'auto', block: 'center' });
    heading.focus();
  }, [focusOpen, focusId]);

  return (
    <div className="overflow-x-auto rounded border border-gray-200 bg-white">
      <table className="min-w-full text-sm">
        <thead>
          <tr className="border-b border-gray-200">
            <th scope="col" className={`${TH} sticky left-0 w-16 bg-white`}>
              Rank
            </th>
            <th scope="col" className={`${TH} sticky left-16 bg-white`}>
              Item
            </th>
            <th scope="col" className={TH}>
              Score
            </th>
            <th scope="col" className={TH}>
              Reach
              <UnitLine unit={REACH_UNIT} />
            </th>
            <th scope="col" className={TH}>
              Impact
            </th>
            <th scope="col" className={TH}>
              Confidence
            </th>
            <th scope="col" className={TH}>
              Effort
              <UnitLine unit={EFFORT_UNIT} />
            </th>
            <th scope="col" className={TH}>
              Product
            </th>
            <th scope="col" className={TH}>
              Status
            </th>
            <th scope="col" className={TH}>
              Target quarter
            </th>
            <th scope="col" className={TH}>
              Owner
            </th>
            <th scope="col" className={TH}>
              Jira
            </th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => {
            const open = openIds.has(item.id);
            const muted = item.archived ? ' text-gray-500' : '';
            return (
              <Fragment key={item.id}>
                {item.id === firstUnscored && (
                  <tr>
                    <th
                      colSpan={COLUMN_COUNT}
                      scope="rowgroup"
                      className="bg-gray-50 px-4 py-2 text-left text-xs font-semibold text-gray-600"
                    >
                      Not scored yet ({unscored}). Oldest first.
                    </th>
                  </tr>
                )}
                <tr className={`border-b border-gray-100 hover:bg-gray-50${muted}`}>
                  <td className={`${TD} sticky left-0 w-16 bg-white font-semibold tabular-nums`}>
                    {item.rank === null ? (
                      <span className="text-gray-400" aria-label="Not ranked">
                        —
                      </span>
                    ) : (
                      item.rank
                    )}
                  </td>
                  <td className={`${TD} sticky left-16 bg-white`}>
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-semibold">{item.title}</span>
                      {item.archived && <span className="text-xs text-gray-500">archived</span>}
                      <button
                        type="button"
                        aria-expanded={open}
                        aria-controls={`item-${item.id}-detail`}
                        aria-label={`Details for ${item.title}`}
                        onClick={() => onToggle(item.id)}
                        className={SMALL_BUTTON}
                      >
                        Details
                      </button>
                    </div>
                    {item.source === 'JIRA' ? (
                      <div className="text-xs text-gray-600">{item.jiraKey}</div>
                    ) : (
                      <span className="inline-block rounded bg-gray-100 px-2 py-0.5 text-xs text-gray-600">Manual</span>
                    )}
                  </td>
                  <td className={`${TD} tabular-nums`}>
                    {item.scoreDisplay !== null ? (
                      <span className="font-semibold">{item.scoreDisplay}</span>
                    ) : (
                      <>
                        <span className="text-gray-500">Not scored</span>
                        <span className="block text-xs text-amber-700">Missing: {missingText(item)}</span>
                      </>
                    )}
                  </td>
                  <td className={TD}>
                    <InputCell item={item} field="REACH" disabled={item.archived} onSaved={onInputSaved} />
                  </td>
                  <td className={TD}>
                    <InputCell item={item} field="IMPACT" disabled={item.archived} onSaved={onInputSaved} />
                  </td>
                  <td className={TD}>
                    <InputCell item={item} field="CONFIDENCE" disabled={item.archived} onSaved={onInputSaved} />
                  </td>
                  <td className={TD}>
                    <InputCell item={item} field="EFFORT" disabled={item.archived} onSaved={onInputSaved} />
                  </td>
                  <td className={TD}>
                    {item.product ? (
                      <>
                        <span className={PRODUCT_CHIP}>{item.product.name}</span>
                        {item.product.archived && <span className="ml-1 text-xs text-gray-500">archived</span>}
                      </>
                    ) : (
                      <Dash title="No product" />
                    )}
                  </td>
                  <td className={TD}>{STATUS_LABELS[item.status]}</td>
                  <td className={TD}>{item.targetQuarter ?? <Dash />}</td>
                  <td className={TD}>{item.owner ?? <Dash />}</td>
                  <td className={TD}>
                    <JiraCell item={item} props={props} />
                  </td>
                </tr>
                {open && (
                  <ItemDetailRow
                    item={item}
                    colSpan={COLUMN_COUNT}
                    headingRef={item.id === focusId ? focusRef : undefined}
                  />
                )}
              </Fragment>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}
