import { useEffect, useState, type KeyboardEvent } from 'react';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import { useSetInputs, type RiceField, type RoadmapInputs, type RoadmapItem } from '../api/roadmap';
import { Spinner } from '../components/ExportButtons';
import { OverrideCell } from './OverrideCell';
import { CONFIDENCE_OPTIONS, FIELD_LABELS, IMPACT_OPTIONS } from './rubric';

const EFFORT_ERROR = 'Enter a number above 0, for example 0.5.';

type EnteredField = Exclude<RiceField, 'REACH'>;

const INPUT_KEYS: Record<EnteredField, keyof RoadmapInputs> = {
  IMPACT: 'impact',
  CONFIDENCE: 'confidence',
  EFFORT: 'effort',
};

const CONTROL = 'rounded border border-gray-300 px-2 py-1 text-sm disabled:opacity-50';

type Failure = { message: string; reason?: string };

type Props = {
  item: RoadmapItem;
  field: RiceField;
  disabled?: boolean;
  onSaved?: (item: RoadmapItem) => void;
};

export function InputCell(props: Props) {
  const { item, field } = props;
  if (field !== 'EFFORT' && item.calculatedFields.includes(field)) {
    return <OverrideCell {...props} field={field} />;
  }
  // Reach is always calculated, so only Impact, Confidence and Effort reach the editable cell.
  return <EditableInputCell {...props} field={field as EnteredField} />;
}

function EditableInputCell({ item, field, disabled, onSaved }: Omit<Props, 'field'> & { field: EnteredField }) {
  const key = INPUT_KEYS[field];
  const label = FIELD_LABELS[field];
  const saved = item[key];
  const save = useSetInputs();
  const [draft, setDraft] = useState(saved === null ? '' : String(saved));
  const [invalid, setInvalid] = useState<string | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);

  useEffect(() => {
    setDraft(saved === null ? '' : String(saved));
  }, [saved]);

  const errorId = `${item.id}-${key}-error`;
  const message = invalid ?? failure?.message ?? null;
  const describedBy = [`rubric-${key}`, message ? errorId : null].filter(Boolean).join(' ');
  const isDisabled = disabled === true || save.isPending;

  function send(value: number) {
    const entered = (field: EnteredField, current: number | null) =>
      item.calculatedFields.includes(field) ? null : current;
    const inputs: RoadmapInputs = {
      impact: entered('IMPACT', item.impact),
      confidence: entered('CONFIDENCE', item.confidence),
      effort: item.effort,
      [key]: value,
    };
    setFailure(null);
    save.mutate(
      { itemId: item.id, inputs },
      {
        onSuccess: (updated) => onSaved?.(updated),
        onError: (error) => {
          setDraft(saved === null ? '' : String(saved));
          setFailure(
            error instanceof ApiError
              ? { message: messageForReason(error.problem), reason: error.problem.reason }
              : { message: `Couldn't save ${label} — ${error.message}. Try again.` },
          );
        },
      },
    );
  }

  function commitNumber() {
    if (save.isPending) return;
    const text = draft.trim();
    if (text === '') {
      setDraft(saved === null ? '' : String(saved));
      setInvalid(null);
      return;
    }
    const value = Number(text);
    if (!Number.isFinite(value) || value <= 0) {
      setInvalid(EFFORT_ERROR);
      return;
    }
    setInvalid(null);
    if (value !== saved) send(value);
  }

  function onKeyDown(event: KeyboardEvent<HTMLInputElement>) {
    if (event.key === 'Enter') {
      event.preventDefault();
      commitNumber();
    } else if (event.key === 'Escape') {
      setDraft(saved === null ? '' : String(saved));
      setInvalid(null);
    }
  }

  const common = {
    'aria-label': `${label} for ${item.title}`,
    'aria-describedby': describedBy,
    'aria-busy': save.isPending || undefined,
    'aria-invalid': invalid ? (true as const) : undefined,
    disabled: isDisabled,
  };

  let control;
  if (field === 'IMPACT' || field === 'CONFIDENCE') {
    const options = field === 'IMPACT' ? IMPACT_OPTIONS : CONFIDENCE_OPTIONS;
    control = (
      <select
        {...common}
        value={saved ?? ''}
        onChange={(event) => send(Number(event.target.value))}
        className={`${CONTROL} ${field === 'IMPACT' ? 'w-32' : 'w-28'}`}
      >
        {saved === null && <option value="">Select…</option>}
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    );
  } else {
    control = (
      <input
        {...common}
        type="number"
        inputMode="decimal"
        value={draft}
        onChange={(event) => setDraft(event.target.value)}
        onBlur={commitNumber}
        onKeyDown={onKeyDown}
        className={`${CONTROL} w-20`}
      />
    );
  }

  return (
    <div>
      <div className="flex items-center gap-1">
        {control}
        {save.isPending && <Spinner />}
      </div>
      {message && (
        <small
          id={errorId}
          className="block text-xs text-red-600"
          {...(failure && !invalid ? { role: 'alert', 'data-reason': failure.reason } : {})}
        >
          {message}
        </small>
      )}
    </div>
  );
}
