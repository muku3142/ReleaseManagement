import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { LadderDots } from '../../src/components/LadderDots';
import type { PromotionStage } from '../../src/api/promotions';
import { usePrefersReducedMotion } from '../../src/hooks/usePrefersReducedMotion';

vi.mock('../../src/hooks/usePrefersReducedMotion', () => ({ usePrefersReducedMotion: vi.fn(() => false) }));
const reducedMotion = vi.mocked(usePrefersReducedMotion);

function stage(id: string, name: string, position: number, extra: Partial<PromotionStage> = {}): PromotionStage {
  return { id, name, position, production: false, retired: false, ...extra };
}

const STAGES = [stage('dev', 'DEV', 1), stage('qa', 'QA', 2), stage('prod', 'Prod', 3, { production: true })];
const PULSE = 'motion-safe:animate-status-pulse';

let container: HTMLDivElement;
let root: Root;

function renderDots(currentStageId: string | null, failedStageId: string | null = null) {
  act(() => {
    root.render(<LadderDots stages={STAGES} currentStageId={currentStageId} failedStageId={failedStageId} />);
  });
}

const dot = (name: string) =>
  [...container.querySelectorAll('li')].find((li) => li.textContent?.startsWith(name))!.querySelector('span')!;
const pulsingDots = () => [...container.querySelectorAll('li > span:first-child')].filter((s) => s.classList.contains(PULSE));

describe('LadderDots', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    reducedMotion.mockReturnValue(false);
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
  });

  it('does not pulse on first render', () => {
    renderDots('qa', 'prod');
    expect(pulsingDots()).toHaveLength(0);
  });

  it('pulses only the new current stage once when a promotion is recorded', () => {
    renderDots('qa');
    renderDots('prod');
    expect(pulsingDots()).toEqual([dot('Prod')]);
    act(() => {
      dot('Prod').dispatchEvent(new Event('animationend', { bubbles: true }));
    });
    expect(pulsingDots()).toHaveLength(0);
    expect(container.querySelector('li[aria-current="step"]')!.textContent).toContain('Prod');
  });

  it('pulses the failed stage when a failure is recorded', () => {
    renderDots('dev');
    renderDots('dev', 'qa');
    expect(pulsingDots()).toEqual([dot('QA')]);
    expect(dot('QA').closest('li')!.getAttribute('data-state')).toBe('failed');
  });

  it('does not pulse under reduced motion', () => {
    reducedMotion.mockReturnValue(true);
    renderDots('dev');
    renderDots('qa', 'prod');
    expect(pulsingDots()).toHaveLength(0);
  });
});
