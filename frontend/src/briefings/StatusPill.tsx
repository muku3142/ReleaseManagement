import type { BriefingStatus } from '../api/briefings';
import { PILL_CLASSES, STATUS_LABELS } from './briefingFormat';

type PillKind = BriefingStatus | 'not_run' | 'unreadable';

const LABELS: Record<PillKind, string> = { ...STATUS_LABELS, not_run: 'Not run yet', unreadable: 'Unreadable' };

export function StatusPill({ kind }: { kind: PillKind }) {
  return (
    <span data-status={kind} className={'rounded border px-2 py-0.5 text-xs font-semibold ' + PILL_CLASSES[kind]}>
      {LABELS[kind]}
    </span>
  );
}
