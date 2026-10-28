import type { PromotionStage } from '../api/promotions';
import { usePulseOnChange } from '@/hooks/usePulseOnChange';

export function LadderDots({
  stages,
  currentStageId,
  failedStageId = null,
  ariaLabel = 'Environment ladder',
  compact = false,
}: {
  stages: PromotionStage[];
  currentStageId: string | null;
  failedStageId?: string | null;
  ariaLabel?: string;
  compact?: boolean;
}) {
  const currentPulse = usePulseOnChange(currentStageId);
  const failedPulse = usePulseOnChange(failedStageId);
  const current = stages.find((s) => s.id === currentStageId);
  const drawn = stages.filter((s) => !s.retired || s.id === currentStageId);
  return (
    <ol
      aria-label={ariaLabel}
      className={`${compact ? '' : 'mt-3 '}flex flex-wrap items-center ${compact ? 'gap-2' : 'gap-3'}`}
    >
      {drawn.map((stage) => {
        const reached = current !== undefined && stage.position <= current.position;
        const isCurrent = stage.id === currentStageId;
        const failed = !reached && stage.id === failedStageId;
        const label = `${stage.name}${stage.retired ? ' (retired)' : ''}`;
        const dotColour = failed ? 'border-2 border-red-600 bg-white' : reached ? 'bg-blue-600' : 'bg-gray-300';
        const pulse = isCurrent && currentPulse.pulsing ? currentPulse : stage.id === failedStageId && failedPulse.pulsing ? failedPulse : null;
        return (
          <li
            key={stage.id}
            data-state={failed ? 'failed' : reached ? 'reached' : 'ahead'}
            aria-current={isCurrent ? 'step' : undefined}
            title={compact ? label : undefined}
            className="flex items-center gap-1"
          >
            <span
              className={`inline-block h-3 w-3 rounded-full ${dotColour} ${isCurrent ? 'ring-2 ring-blue-300' : ''}${pulse ? ' motion-safe:animate-status-pulse' : ''}`}
              onAnimationEnd={pulse?.onAnimationEnd}
            />
            {compact ? (
              <span className="sr-only">{label}</span>
            ) : (
              <>
                <span className="text-xs text-gray-700">{label}</span>
                {stage.production && <span className="text-xs text-gray-500">Production</span>}
              </>
            )}
          </li>
        );
      })}
    </ol>
  );
}
