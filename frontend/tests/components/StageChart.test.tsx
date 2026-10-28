import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import StageChart, { NOT_PROMOTED_FILL, STAGE_FILLS, type StageChartRow } from '../../src/components/StageChart';
import { usePrefersReducedMotion } from '../../src/hooks/usePrefersReducedMotion';

const barProps = vi.hoisted(() => [] as Array<{ isAnimationActive?: unknown }>);

vi.mock('recharts', async (importOriginal) => {
  const actual = await importOriginal<typeof import('recharts')>();
  const Bar = (props: Parameters<typeof actual.Bar>[0]) => {
    barProps.push(props);
    return <actual.Bar {...props} />;
  };
  return { ...actual, Bar };
});

const ROWS: StageChartRow[] = [
  { label: 'QA', count: 1, kind: 'stage' },
  { label: 'Prod', count: 0, kind: 'stage' },
  { label: 'Not promoted', count: 2, kind: 'notPromoted' },
];

type Listener = () => void;

function stubMatchMedia(initial: boolean) {
  const state = { matches: initial, listeners: new Set<Listener>() };
  vi.spyOn(window, 'matchMedia').mockImplementation(
    (query: string) =>
      ({
        get matches() {
          return query.includes('prefers-reduced-motion') && state.matches;
        },
        media: query,
        addEventListener: (_: string, l: Listener) => state.listeners.add(l),
        removeEventListener: (_: string, l: Listener) => state.listeners.delete(l),
      }) as unknown as MediaQueryList,
  );
  return {
    set(matches: boolean) {
      state.matches = matches;
      state.listeners.forEach((l) => l());
    },
  };
}

let container: HTMLDivElement;
let root: Root;

async function render(node: React.ReactNode) {
  await act(async () => root.render(node));
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0));
  });
}

function MotionProbe() {
  return <span data-testid="probe">{String(usePrefersReducedMotion())}</span>;
}

describe('usePrefersReducedMotion', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    vi.restoreAllMocks();
  });

  it('is false with the default polyfill', async () => {
    await render(<MotionProbe />);
    expect(container.textContent).toBe('false');
  });

  it('is true when the query matches and follows change events', async () => {
    const media = stubMatchMedia(true);
    await render(<MotionProbe />);
    expect(container.textContent).toBe('true');
    await act(async () => media.set(false));
    expect(container.textContent).toBe('false');
  });
});

describe('StageChart', () => {
  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
    barProps.length = 0;
    // jsdom has no layout; Recharts' ResponsiveContainer would measure 0x0 and draw nothing.
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockReturnValue(
      { width: 320, height: 84, top: 0, left: 0, right: 320, bottom: 84, x: 0, y: 0, toJSON: () => ({}) } as DOMRect,
    );
  });

  afterEach(async () => {
    await act(async () => root.unmount());
    container.remove();
    document.documentElement.classList.remove('dark');
    vi.restoreAllMocks();
  });

  it('labels the chart with every value', async () => {
    await render(<StageChart rows={ROWS} />);
    const chart = container.querySelector('[role="img"]')!;
    expect(chart.getAttribute('aria-label')).toBe('Releases per stage: QA 1, Prod 0, Not promoted 2.');
  });

  it('sizes the chart at 28px per row', async () => {
    await render(<StageChart rows={ROWS} />);
    expect((container.querySelector('[data-slot="chart"]') as HTMLElement).style.height).toBe('84px');
  });

  it('gives each stage its own --chart-N gradient and Not promoted the muted one, in dark mode', async () => {
    document.documentElement.classList.add('dark');
    stubMatchMedia(true);
    await render(<StageChart rows={ROWS} />);
    const fills = [...container.querySelectorAll('.recharts-bar-rectangle path')]
      .map((p) => p.getAttribute('fill'))
      .filter((f) => f?.startsWith('url('));
    expect(fills).toEqual(['url(#stage-chart-fill-1)', 'url(#stage-chart-fill-2)', 'url(#stage-chart-fill-muted)']);
    const stops = (id: string) =>
      [...container.querySelectorAll(`linearGradient#${id} stop`)].map((s) => (s as SVGStopElement).style.stopColor);
    expect(stops('stage-chart-fill-1')).toEqual(['var(--chart-1)', 'var(--chart-1)']);
    expect(stops('stage-chart-fill-2')).toEqual(['var(--chart-2)', 'var(--chart-2)']);
    expect(stops('stage-chart-fill-muted')).toEqual(['var(--muted-foreground)', 'var(--muted-foreground)']);
  });

  it('uses constant gradient ids backed only by chart tokens', async () => {
    expect(STAGE_FILLS.map((f) => [f.id, f.color])).toEqual(
      [1, 2, 3, 4, 5].map((n) => [`stage-chart-fill-${n}`, `var(--chart-${n})`]),
    );
    expect(NOT_PROMOTED_FILL).toEqual({ id: 'stage-chart-fill-muted', color: 'var(--muted-foreground)' });
    await render(<StageChart rows={[{ label: 'url(#evil)', count: 1, kind: 'stage' }]} />);
    const ids = [...container.querySelectorAll('linearGradient')].map((g) => g.id);
    expect(ids).toEqual([...STAGE_FILLS.map((f) => f.id), NOT_PROMOTED_FILL.id]);
  });

  it('prints each value at the bar end over a soft track, with a tooltip and no x axis', async () => {
    stubMatchMedia(true);
    await render(<StageChart rows={ROWS} />);
    const labels = [...container.querySelectorAll('.recharts-label-list text')].map((t) => t.textContent);
    expect(labels).toEqual(['1', '0', '2']);
    expect(container.querySelectorAll('.recharts-bar-background-rectangle')).toHaveLength(ROWS.length);
    expect(container.querySelector('.recharts-tooltip-wrapper')).not.toBeNull();
    expect(container.querySelector('.recharts-xAxis')).toBeNull();
  });

  it('animates the bars unless reduced motion is requested', async () => {
    await render(<StageChart rows={ROWS} />);
    expect(barProps.at(-1)!.isAnimationActive).toBe(true);
    await act(async () => root.unmount());
    root = createRoot(container);
    stubMatchMedia(true);
    await render(<StageChart rows={ROWS} />);
    expect(barProps.at(-1)!.isAnimationActive).toBe(false);
  });
});
