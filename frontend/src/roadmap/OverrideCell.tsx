import { useState } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useOverrideInput, useResetOverride, type CalculatedField, type RoadmapItem } from '../api/roadmap';
import { Spinner } from '../components/ExportButtons';
import { CONFIDENCE_OPTIONS, FIELD_LABELS, IMPACT_OPTIONS } from './rubric';

const CONTROL = 'rounded border border-gray-300 px-2 py-1 text-sm disabled:opacity-50';
const LINK = 'text-xs text-blue-700 underline disabled:opacity-50';
const REASON_MAX = 200;

const KEYS = { REACH: 'reach', IMPACT: 'impact', CONFIDENCE: 'confidence' } as const;

function display(field: CalculatedField, value: number | null): string {
  if (value === null) return 'Not set';
  if (field === 'REACH') return String(value);
  const options = field === 'IMPACT' ? IMPACT_OPTIONS : CONFIDENCE_OPTIONS;
  return options.find((o) => o.value === value)?.label ?? String(value);
}

type Props = {
  item: RoadmapItem;
  field: CalculatedField;
  disabled?: boolean;
  onSaved?: (item: RoadmapItem) => void;
};

export function OverrideCell({ item, field, disabled, onSaved }: Props) {
  const key = KEYS[field];
  const label = FIELD_LABELS[field];
  const reason = item.overrides[key];
  const override = useOverrideInput();
  const reset = useResetOverride();
  const [editing, setEditing] = useState(false);
  const [value, setValue] = useState('');
  const [reasonDraft, setReasonDraft] = useState('');
  const [failure, setFailure] = useState<{ message: string; reason?: string } | null>(null);

  const pending = override.isPending || reset.isPending;
  const isDisabled = disabled === true || pending;
  const numeric = Number(value);
  const validValue =
    value.trim() !== '' && (field === 'REACH' ? Number.isInteger(numeric) && numeric >= 0 : Number.isFinite(numeric));
  const canSave = validValue && reasonDraft.trim() !== '' && !isDisabled;

  const callbacks = {
    onSuccess: (updated: RoadmapItem) => {
      setEditing(false);
      onSaved?.(updated);
    },
    onError: (error: Error) =>
      setFailure(
        error instanceof ApiError
          ? { message: messageForReason(error.problem), reason: error.problem.reason }
          : { message: `Couldn't save ${label} — ${error.message}. Try again.` },
      ),
  };

  function open() {
    setValue(item[key] === null ? '' : String(item[key]));
    setReasonDraft('');
    setFailure(null);
    setEditing(true);
  }

  function save() {
    setFailure(null);
    override.mutate({ itemId: item.id, field, value: numeric, reason: reasonDraft.trim() }, callbacks);
  }

  function resetToCalculated() {
    setFailure(null);
    reset.mutate({ itemId: item.id, field }, callbacks);
  }

  const valueLabel = `${label} override for ${item.title}`;
  const valueControl =
    field === 'REACH' ? (
      <input
        type="number"
        inputMode="numeric"
        min={0}
        aria-label={valueLabel}
        aria-describedby={`rubric-${key}`}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        disabled={isDisabled}
        className={`${CONTROL} w-20`}
      />
    ) : (
      <select
        aria-label={valueLabel}
        aria-describedby={`rubric-${key}`}
        value={value}
        onChange={(event) => setValue(event.target.value)}
        disabled={isDisabled}
        className={`${CONTROL} w-32`}
      >
        <option value="">Select…</option>
        {(field === 'IMPACT' ? IMPACT_OPTIONS : CONFIDENCE_OPTIONS).map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    );

  return (
    <div>
      <div className="flex items-center gap-1">
        <span>{display(field, item[key])}</span>
        {pending && <Spinner />}
      </div>
      {reason === null ? (
        <small className="block text-xs text-gray-600">Calculated</small>
      ) : (
        <>
          <small className="block text-xs text-amber-700" title={reason}>
            Overridden: {reason}
          </small>
          <small className="block text-xs text-gray-600">Calculated: {display(field, item.calculated[key])}</small>
        </>
      )}
      {editing ? (
        <div className="mt-1 flex flex-col gap-1">
          {valueControl}
          <input
            type="text"
            aria-label={`Reason for overriding ${label} for ${item.title}`}
            placeholder="Reason (required)"
            maxLength={REASON_MAX}
            value={reasonDraft}
            onChange={(event) => setReasonDraft(event.target.value)}
            disabled={isDisabled}
            className={CONTROL}
          />
          <div className="flex gap-2">
            <button type="button" className={LINK} onClick={save} disabled={!canSave}>
              Save
            </button>
            <button type="button" className={LINK} onClick={() => setEditing(false)} disabled={pending}>
              Cancel
            </button>
          </div>
        </div>
      ) : (
        <div className="flex gap-2">
          <button type="button" className={LINK} onClick={open} disabled={isDisabled}>
            Override
          </button>
          {reason !== null && (
            <button type="button" className={LINK} onClick={resetToCalculated} disabled={isDisabled}>
              Reset to calculated
            </button>
          )}
        </div>
      )}
      {failure && (
        <small className="block text-xs text-red-600" role="alert" data-reason={failure.reason}>
          {failure.message}
        </small>
      )}
    </div>
  );
}
