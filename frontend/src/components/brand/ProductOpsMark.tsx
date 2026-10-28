import { useId } from 'react';
import { usePrefersReducedMotion } from '@/hooks/usePrefersReducedMotion';

const LINKS = ['M8 9l8 7', 'M24 9l-8 7', 'M8 23l8-7', 'M24 23l-8-7'];

type ProductOpsMarkProps = {
  size: 16 | 24 | 28 | 32 | 48;
  title?: string;
  className?: string;
  animate?: boolean;
};

// animate plays a CSS connect-in once on mount (links draw, then nodes pop); CSS animations never replay on re-render.
export function ProductOpsMark({ size, title, className, animate = false }: ProductOpsMarkProps) {
  const id = useId();
  const reducedMotion = usePrefersReducedMotion();
  const play = animate && !reducedMotion;
  const linkClass = play ? 'motion-safe:animate-mark-link' : undefined;
  const nodeClass = play ? 'motion-safe:animate-mark-node' : undefined;
  const a11y = title ? { role: 'img' } : { 'aria-hidden': true };
  return (
    <svg viewBox="0 0 32 32" width={size} height={size} className={className} {...a11y}>
      {title && <title>{title}</title>}
      <defs>
        <linearGradient id={id} x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="var(--brand-from)" />
          <stop offset="1" stopColor="var(--brand-to)" />
        </linearGradient>
      </defs>
      <rect width="32" height="32" rx={9} fill={`url(#${id})`} />
      <g fill="#fff" transform="translate(6.08 6.08) scale(.62)">
        <g stroke="#fff" strokeWidth={2} strokeLinecap="round" opacity=".55">
          {LINKS.map((d) => (
            <path key={d} d={d} pathLength={1} className={linkClass} />
          ))}
        </g>
        <circle cx="16" cy="16" r="4" className={nodeClass} />
        <circle cx="8" cy="9" r="2.6" opacity=".85" className={nodeClass} />
        <circle cx="24" cy="9" r="2.6" opacity=".85" className={nodeClass} />
        <circle cx="8" cy="23" r="2.6" opacity=".85" className={nodeClass} />
        <circle cx="24" cy="23" r="2.6" opacity=".85" className={nodeClass} />
      </g>
    </svg>
  );
}
