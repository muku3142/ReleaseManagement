import type { BriefingStatus } from '../api/briefings';

export const STATUS_LABELS: Record<BriefingStatus, string> = {
  ok: 'All clear',
  attention: 'Needs attention',
  quiet: 'Quiet',
  failed: 'Run failed',
  needs_credential: 'Needs credential',
  skipped: 'Skipped',
};

const AMBER = 'border-amber-600 bg-amber-50 text-amber-700';
const GRAY = 'border-gray-300 bg-gray-100 text-gray-700';

export const PILL_CLASSES: Record<BriefingStatus | 'not_run' | 'unreadable', string> = {
  ok: 'border-green-600 bg-green-50 text-green-700',
  attention: AMBER,
  quiet: GRAY,
  failed: 'border-red-600 bg-red-50 text-red-700',
  needs_credential: AMBER,
  skipped: GRAY,
  not_run: 'border-gray-300 bg-white text-gray-600',
  unreadable: AMBER,
};

export const WORST_FIRST: BriefingStatus[] = ['failed', 'needs_credential', 'attention', 'ok', 'quiet', 'skipped'];

export function worstStatus(statuses: BriefingStatus[]): BriefingStatus | null {
  return WORST_FIRST.find((s) => statuses.includes(s)) ?? null;
}

const CAIRO = new Intl.DateTimeFormat('en-GB', {
  timeZone: 'Africa/Cairo',
  weekday: 'short',
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  hourCycle: 'h23',
});

export function formatCairo(iso: string): string {
  const time = Date.parse(iso);
  if (Number.isNaN(time)) return 'Unknown time';
  const part = Object.fromEntries(CAIRO.formatToParts(time).map((p) => [p.type, p.value]));
  return `${part.weekday} ${part.day} ${part.month}, ${part.hour}:${part.minute}`;
}

export function attentionText(n: number): string {
  return n === 1 ? '1 item needs attention' : `${n} items need attention`;
}
