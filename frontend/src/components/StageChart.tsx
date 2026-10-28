import { Bar, BarChart, Cell, LabelList, XAxis, YAxis } from 'recharts';
import { ChartContainer, ChartTooltip, ChartTooltipContent, type ChartConfig } from '@/components/ui/chart';
import { usePrefersReducedMotion } from '../hooks/usePrefersReducedMotion';

export type StageChartRow = { label: string; count: number; kind: 'stage' | 'notPromoted' };

const ROW_HEIGHT = 28;
const LABEL_WIDTH = 112;

// ChartStyle writes this object into a <style> tag, so keys and colours stay static; stage names only reach the data rows.
const chartConfig = { count: { label: 'Releases', color: 'var(--chart-1)' } } satisfies ChartConfig;

// Gradient ids are constants so no user text ever reaches an SVG id or url() reference.
export const STAGE_FILLS = [
  { id: 'stage-chart-fill-1', color: 'var(--chart-1)' },
  { id: 'stage-chart-fill-2', color: 'var(--chart-2)' },
  { id: 'stage-chart-fill-3', color: 'var(--chart-3)' },
  { id: 'stage-chart-fill-4', color: 'var(--chart-4)' },
  { id: 'stage-chart-fill-5', color: 'var(--chart-5)' },
] as const;
export const NOT_PROMOTED_FILL = { id: 'stage-chart-fill-muted', color: 'var(--muted-foreground)' } as const;

const GRADIENTS = [...STAGE_FILLS, NOT_PROMOTED_FILL];

export default function StageChart({ rows }: { rows: StageChartRow[] }) {
  const reduced = usePrefersReducedMotion();
  const summary = `Releases per stage: ${rows.map((r) => `${r.label} ${r.count}`).join(', ')}.`;
  let stageIndex = 0;
  const data = rows.map((row) => {
    const fill = row.kind === 'notPromoted' ? NOT_PROMOTED_FILL : STAGE_FILLS[stageIndex++ % STAGE_FILLS.length];
    return { ...row, fill: fill.color, gradientId: fill.id };
  });
  return (
    <ChartContainer
      config={chartConfig}
      className="aspect-auto w-full"
      style={{ height: rows.length * ROW_HEIGHT }}
      role="img"
      aria-label={summary}
    >
      <BarChart accessibilityLayer data={data} layout="vertical" margin={{ left: 0, right: 32 }}>
        <defs>
          {GRADIENTS.map((g) => (
            <linearGradient key={g.id} id={g.id} x1="0" y1="0" x2="1" y2="0">
              <stop offset="0%" style={{ stopColor: g.color, stopOpacity: 0.7 }} />
              <stop offset="100%" style={{ stopColor: g.color, stopOpacity: 1 }} />
            </linearGradient>
          ))}
        </defs>
        <YAxis
          type="category"
          dataKey="label"
          tickLine={false}
          axisLine={false}
          width={LABEL_WIDTH}
          tick={{ fontSize: 12, fontWeight: 500, fill: 'var(--foreground)' }}
        />
        <XAxis type="number" dataKey="count" hide />
        <ChartTooltip
          cursor={false}
          isAnimationActive={!reduced}
          content={<ChartTooltipContent className="bg-popover text-popover-foreground border-border shadow-md" />}
        />
        {/* Recharts drops zero-width bars together with their label, so a 0 count keeps a 2px stub to show its value. */}
        <Bar
          dataKey="count"
          minPointSize={2}
          barSize={20}
          radius={10}
          background={{ fill: 'var(--muted)', radius: 10 }}
          activeBar={{ className: 'brightness-125 saturate-125' }}
          isAnimationActive={!reduced}
          animationDuration={400}
          animationEasing="ease-out"
        >
          {data.map((row) => (
            <Cell key={row.label} fill={`url(#${row.gradientId})`} />
          ))}
          <LabelList dataKey="count" position="right" className="fill-foreground text-xs font-bold tabular-nums" />
        </Bar>
      </BarChart>
    </ChartContainer>
  );
}
