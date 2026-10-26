import { CONFIDENCE_OPTIONS, EFFORT_UNIT } from './rubric';

const NOT_SET = 'not set';

export function relativeTime(iso: string, now: Date = new Date()): string {
  const seconds = Math.max(0, Math.floor((now.getTime() - new Date(iso).getTime()) / 1000));
  if (seconds < 60) return 'just now';
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes}m ago`;
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return `${hours}h ago`;
  return `${Math.floor(hours / 24)}d ago`;
}

function confidenceLabel(value: number): string {
  const option = CONFIDENCE_OPTIONS.find((o) => o.value === value);
  return option ? `${option.label.split(' ')[0]} (${value}%)` : `${value}%`;
}

function valueText(field: string, value: number | null): string {
  if (value === null) return NOT_SET;
  return field === 'CONFIDENCE' ? confidenceLabel(value) : String(value);
}

export function formatInputValue(field: string, value: number | null): string {
  const text = valueText(field, value);
  return field === 'EFFORT' && value !== null ? `${text} ${EFFORT_UNIT}` : text;
}

export function formatChange(field: string, oldValue: number | null, newValue: number | null): string {
  const unit = field === 'EFFORT' ? ` ${EFFORT_UNIT}` : '';
  return `${valueText(field, oldValue)} → ${valueText(field, newValue)}${unit}`;
}
