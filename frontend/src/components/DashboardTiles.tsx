import { useState } from 'react';
import { useCountUp } from '@/hooks/useCountUp';
import { cn } from '@/lib/utils';

export function Tile({ caption, children, unit }: { caption: string; children: React.ReactNode; unit?: string }) {
  return (
    <div className="rounded border border-gray-200 bg-white p-4">
      <div className="text-lg font-semibold tabular-nums text-gray-900">{children}</div>
      <div className="text-sm text-gray-600">{caption}</div>
      {unit && <div className="text-xs text-gray-500">{unit}</div>}
    </div>
  );
}

export function HelpToggle({ id, text }: { id: string; text: string }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <button
        type="button"
        aria-label="What does this count?"
        aria-expanded={open}
        aria-controls={id}
        title={text}
        onClick={() => setOpen((v) => !v)}
        className="absolute right-2 top-2 h-6 w-6 rounded border border-gray-300 text-xs font-semibold text-gray-700 focus-visible:ring-2 focus-visible:ring-blue-600"
      >
        ?
      </button>
      {open && (
        <p id={id} className="mt-2 text-xs text-gray-600">
          {text}
        </p>
      )}
    </>
  );
}

type FilterTileProps = {
  id: string;
  caption: string;
  figure: React.ReactNode;
  unit: string;
  pressed: boolean;
  onPress: () => void;
  help: string;
  disabledNote?: string;
};

export function FilterTile({ id, caption, figure, unit, pressed, onPress, help, disabledNote }: FilterTileProps) {
  const disabled = disabledNote !== undefined;
  const display = useCountUp(typeof figure === 'number' ? figure : null);
  return (
    <div className="relative">
      <button
        id={id}
        type="button"
        aria-pressed={pressed}
        aria-disabled={disabled ? 'true' : undefined}
        aria-describedby={disabled ? `${id}-note` : undefined}
        onClick={disabled ? undefined : onPress}
        className={cn(
          'w-full rounded border border-gray-200 bg-white p-4 text-left hover:bg-gray-50 focus-visible:ring-2 focus-visible:ring-blue-600',
          'transition-[translate,box-shadow,border-color] duration-(--motion-base) ease-(--ease-out) motion-safe:hover:-translate-y-0.5 hover:shadow-md hover:border-primary/40',
          pressed && 'bg-gray-50 ring-1 ring-gray-900',
        )}
      >
        <span
          data-count-display={display ?? undefined}
          className="count-up block text-lg font-semibold tabular-nums text-gray-900"
        >
          {figure}
        </span>
        <span className="block text-sm text-gray-600">{caption}</span>
        <span className="block text-xs text-gray-500">{unit}</span>
      </button>
      {disabled && (
        <p id={`${id}-note`} className="mt-1 text-xs text-amber-700">
          {disabledNote}
        </p>
      )}
      <HelpToggle id={`${id}-help`} text={help} />
    </div>
  );
}
