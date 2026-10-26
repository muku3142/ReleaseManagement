import { useEffect, useState, type Ref } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useProducts } from '../api/products';
import {
  useArchiveItem,
  useRefreshItem,
  useRestoreItem,
  useUpdateDetails,
  type RoadmapDetails,
  type RoadmapItem,
  type RoadmapStatus,
} from '../api/roadmap';
import { Spinner } from '../components/ExportButtons';
import { SMALL_BUTTON } from '../components/EnvironmentLadderSection';
import { InputHistoryPanel } from './InputHistoryPanel';
import { relativeTime } from './format';
import { STATUS_LABELS } from './rubric';

const FIELD = 'w-full rounded border border-gray-300 px-3 py-2 text-sm';
const LABEL = 'block text-sm font-semibold text-gray-700';
const UNAVAILABLE_TITLE = 'Jira no longer returns this issue. The last fetched summary and status are kept.';
const NO_TITLE = 'Enter a title.';

type Failure = { message: string; reason?: string };

const orNull = (text: string) => (text.trim() === '' ? null : text.trim());

function toFailure(error: Error, jira = false): Failure {
  if (error instanceof ApiError) return { message: messageForReason(error.problem), reason: error.problem.reason };
  return {
    message: jira
      ? `Couldn't reach Jira — ${error.message}. Nothing was changed. Try again.`
      : `Couldn't save — ${error.message}. Try again.`,
  };
}

type Props = { item: RoadmapItem; colSpan: number; headingRef?: Ref<HTMLHeadingElement> };

export function ItemDetailRow({ item, colSpan, headingRef }: Props) {
  const products = useProducts();
  const update = useUpdateDetails();
  const refresh = useRefreshItem();
  const archive = useArchiveItem();
  const restore = useRestoreItem();

  const [title, setTitle] = useState(item.title);
  const [quarter, setQuarter] = useState(item.targetQuarter ?? '');
  const [owner, setOwner] = useState(item.owner ?? '');
  const [notes, setNotes] = useState(item.notes ?? '');
  const [titleError, setTitleError] = useState(false);
  const [failure, setFailure] = useState<Failure | null>(null);

  useEffect(() => setTitle(item.title), [item.title]);
  useEffect(() => setQuarter(item.targetQuarter ?? ''), [item.targetQuarter]);
  useEffect(() => setOwner(item.owner ?? ''), [item.owner]);
  useEffect(() => setNotes(item.notes ?? ''), [item.notes]);

  const isManual = item.source === 'MANUAL';
  const busy = update.isPending || archive.isPending || restore.isPending;

  function save(changes: Partial<RoadmapDetails>) {
    const details: RoadmapDetails = {
      title: isManual ? item.title : null,
      status: item.status,
      productId: item.product?.id ?? null,
      targetQuarter: item.targetQuarter,
      owner: item.owner,
      notes: item.notes,
      ...changes,
    };
    setFailure(null);
    update.mutate({ itemId: item.id, details }, { onError: (error) => setFailure(toFailure(error)) });
  }

  function saveTitle() {
    if (!isManual || title === item.title) return;
    if (title.trim() === '') {
      setTitleError(true);
      return;
    }
    setTitleError(false);
    save({ title: title.trim() });
  }

  function saveText(key: 'targetQuarter' | 'owner' | 'notes', text: string, current: string | null) {
    const next = orNull(text);
    if (next !== current) save({ [key]: next });
  }

  function toggleArchive() {
    setFailure(null);
    const mutation = item.archived ? restore : archive;
    mutation.mutate(item.id, { onError: (error) => setFailure(toFailure(error)) });
  }

  const catalog = products.data?.products ?? [];
  const options = catalog.filter((p) => !p.archived || p.id === item.product?.id);
  const chosen = item.product ? catalog.find((p) => p.id === item.product?.id) : undefined;
  const hint = item.product && chosen
    ? `${item.product.name} has ${chosen.clientCount} linked ${chosen.clientCount === 1 ? 'client' : 'clients'}`
    : null;

  const syncedLabel = item.jiraUnavailable ? 'Last fetched' : 'Fetched';

  return (
    <tr id={`item-${item.id}-detail`}>
      <td colSpan={colSpan} className="bg-gray-100 p-4">
        <div className="space-y-4">
          <section className="space-y-3">
            <h3 ref={headingRef} tabIndex={-1} className="text-base font-semibold text-gray-900">
              Details
            </h3>
            <div className="grid gap-4 sm:grid-cols-2">
              <div>
                <label className={LABEL} htmlFor={`${item.id}-title`}>
                  Title
                </label>
                <input
                  id={`${item.id}-title`}
                  className={FIELD}
                  value={title}
                  readOnly={!isManual}
                  aria-invalid={titleError ? true : undefined}
                  aria-describedby={titleError ? `${item.id}-title-error` : undefined}
                  onChange={(event) => setTitle(event.target.value)}
                  onBlur={saveTitle}
                />
                {!isManual && <small className="block text-xs text-gray-600">Comes from Jira. Refresh to update it.</small>}
                {titleError && (
                  <small id={`${item.id}-title-error`} className="block text-xs text-red-600">
                    {NO_TITLE}
                  </small>
                )}
              </div>

              <div>
                <label className={LABEL} htmlFor={`${item.id}-status`}>
                  Status
                </label>
                <select
                  id={`${item.id}-status`}
                  className={FIELD}
                  value={item.status}
                  onChange={(event) => save({ status: event.target.value as RoadmapStatus })}
                >
                  {Object.entries(STATUS_LABELS).map(([value, label]) => (
                    <option key={value} value={value}>
                      {label}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <label className={LABEL} htmlFor={`${item.id}-product`}>
                  Product
                </label>
                <select
                  id={`${item.id}-product`}
                  className={FIELD}
                  value={item.product?.id ?? ''}
                  onChange={(event) => save({ productId: event.target.value === '' ? null : event.target.value })}
                >
                  <option value="">No product</option>
                  {options.map((p) => (
                    <option key={p.id} value={p.id}>
                      {`${p.name}${p.archived ? ' (archived)' : ''}`}
                    </option>
                  ))}
                  {item.product && !options.some((p) => p.id === item.product?.id) && (
                    <option value={item.product.id}>
                      {`${item.product.name}${item.product.archived ? ' (archived)' : ''}`}
                    </option>
                  )}
                </select>
                {hint && <small className="block text-xs text-gray-600">{hint}</small>}
              </div>

              <div>
                <label className={LABEL} htmlFor={`${item.id}-quarter`}>
                  Target quarter
                </label>
                <input
                  id={`${item.id}-quarter`}
                  className={FIELD}
                  placeholder="2027-Q1"
                  value={quarter}
                  onChange={(event) => setQuarter(event.target.value)}
                  onBlur={() => saveText('targetQuarter', quarter, item.targetQuarter)}
                />
                <small className="block text-xs text-gray-600">For example 2027-Q1.</small>
              </div>

              <div>
                <label className={LABEL} htmlFor={`${item.id}-owner`}>
                  Owner
                </label>
                <input
                  id={`${item.id}-owner`}
                  className={FIELD}
                  value={owner}
                  onChange={(event) => setOwner(event.target.value)}
                  onBlur={() => saveText('owner', owner, item.owner)}
                />
              </div>

              <div className="sm:col-span-2">
                <label className={LABEL} htmlFor={`${item.id}-notes`}>
                  Notes
                </label>
                <textarea
                  id={`${item.id}-notes`}
                  className={FIELD}
                  rows={4}
                  value={notes}
                  onChange={(event) => setNotes(event.target.value)}
                  onBlur={() => saveText('notes', notes, item.notes)}
                />
              </div>
            </div>
          </section>

          {item.source === 'JIRA' && (
            <section className="space-y-2">
              <h3 className="text-base font-semibold text-gray-900">Jira</h3>
              <div className="flex flex-wrap items-center gap-2 text-xs text-gray-600">
                <span>
                  {item.jiraKey} · {item.jiraStatus ?? 'unknown status'}
                  {item.jiraSyncedAt && ` · ${syncedLabel} ${relativeTime(item.jiraSyncedAt)}`}
                </span>
                {item.jiraUnavailable && (
                  <span
                    title={UNAVAILABLE_TITLE}
                    className="rounded border border-amber-600 bg-amber-50 px-2 py-0.5 text-xs font-semibold text-amber-700"
                  >
                    unavailable in Jira
                  </span>
                )}
                <button
                  type="button"
                  disabled={refresh.isPending}
                  onClick={() => {
                    setFailure(null);
                    refresh.mutate(item.id, { onError: (error) => setFailure(toFailure(error, true)) });
                  }}
                  className={SMALL_BUTTON}
                >
                  {refresh.isPending ? 'Refreshing…' : 'Refresh from Jira'}
                </button>
                {refresh.isPending && <Spinner />}
              </div>
            </section>
          )}

          <section className="space-y-2">
            <h3 className="text-base font-semibold text-gray-900">Input history</h3>
            <InputHistoryPanel itemId={item.id} />
          </section>

          <section className="space-y-1">
            <button type="button" disabled={busy} onClick={toggleArchive} className={SMALL_BUTTON}>
              {item.archived ? 'Restore item' : 'Archive item'}
            </button>
            <small className="block text-xs text-gray-600">
              Archived items are hidden from the ranking. Their history is kept.
            </small>
          </section>

          {failure && (
            <p role="alert" data-reason={failure.reason} className="text-sm text-red-600">
              {failure.message}
            </p>
          )}
        </div>
      </td>
    </tr>
  );
}
