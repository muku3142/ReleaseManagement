import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReadinessPill } from '../../src/components/ReadinessPill';
import type { DocumentReadinessState } from '../../src/api/documents';
import { usePrefersReducedMotion } from '../../src/hooks/usePrefersReducedMotion';

vi.mock('../../src/hooks/usePrefersReducedMotion', () => ({ usePrefersReducedMotion: vi.fn(() => false) }));
const reducedMotion = vi.mocked(usePrefersReducedMotion);

type LinkProps = {
  children?: ReactNode;
  to?: string;
  params?: Record<string, string>;
  search?: Record<string, unknown>;
  hash?: string;
  className?: string;
  onAnimationEnd?: () => void;
};

vi.mock('@tanstack/react-router', () => ({
  Link: (props: LinkProps) => {
    let href = props.to ?? '';
    for (const [key, value] of Object.entries(props.params ?? {})) {
      href = href.replace(`$${key}`, value);
    }
    if (props.search) {
      href += `?${new URLSearchParams(Object.entries(props.search).map(([k, v]) => [k, String(v)])).toString()}`;
    }
    if (props.hash) href += `#${props.hash}`;
    return (
      <a href={href} className={props.className} onAnimationEnd={props.onAnimationEnd}>
        {props.children}
      </a>
    );
  },
}));

let container: HTMLDivElement;
let root: Root;

function renderPill(state: DocumentReadinessState) {
  act(() => {
    root.render(<ReadinessPill state={state} releaseId="r1" documentType="RELEASE_NOTES" />);
  });
  return container.querySelector('a')!;
}

describe('ReadinessPill', () => {
  beforeAll(() => {
    (globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
  });

  beforeEach(() => {
    container = document.createElement('div');
    document.body.appendChild(container);
    root = createRoot(container);
  });

  afterEach(() => {
    act(() => root.unmount());
    container.remove();
  });

  it('UP_TO_DATE renders the "Up to date" success badge with a decorative icon, linking to the document viewer', () => {
    const anchor = renderPill('UP_TO_DATE');
    expect(anchor.textContent).toBe('Up to date');
    expect(anchor.getAttribute('href')).toBe('/releases/r1/documents/RELEASE_NOTES');
    expect(anchor.className).toContain('text-success');
    expect(anchor.querySelector('svg')!.getAttribute('aria-hidden')).toBe('true');
  });

  it('STALE renders the "Out of date" warning badge with a decorative icon, linking to the document viewer', () => {
    const anchor = renderPill('STALE');
    expect(anchor.textContent).toBe('Out of date');
    expect(anchor.getAttribute('href')).toBe('/releases/r1/documents/RELEASE_NOTES');
    expect(anchor.className).toContain('text-warning');
    expect(anchor.querySelector('svg')!.getAttribute('aria-hidden')).toBe('true');
  });

  it('NOT_GENERATED renders the neutral "Not generated" badge linking to the release detail Documents section', () => {
    const anchor = renderPill('NOT_GENERATED');
    expect(anchor.textContent).toBe('Not generated');
    expect(anchor.getAttribute('href')).toBe('/releases/r1#documents');
    expect(anchor.className).toContain('bg-muted');
    expect(anchor.querySelector('svg')).toBeNull();
  });

  it('NO_TEMPLATE renders the neutral "No template" badge linking to /templates', () => {
    const anchor = renderPill('NO_TEMPLATE');
    expect(anchor.textContent).toBe('No template');
    expect(anchor.getAttribute('href')).toBe('/templates');
    expect(anchor.className).toContain('bg-muted');
  });

  it('upToDateAndStaleUseDistinctVariants', () => {
    const upToDate = renderPill('UP_TO_DATE');
    expect(upToDate.className).toContain('text-success');
    expect(upToDate.className).not.toContain('text-warning');

    const stale = renderPill('STALE');
    expect(stale.className).toContain('text-warning');
    expect(stale.className).not.toContain('text-success');
  });

  it('everyPillIsTheBadgeAnchorWithHoverUnderline', () => {
    const states: DocumentReadinessState[] = ['UP_TO_DATE', 'STALE', 'NOT_GENERATED', 'NO_TEMPLATE'];
    for (const state of states) {
      const anchor = renderPill(state);
      expect(anchor.className).toContain('rounded-full');
      expect(anchor.className).toContain('hover:underline');
    }
  });

  it('everyStateIsAKeyboardReachableLink', () => {
    const states: DocumentReadinessState[] = ['UP_TO_DATE', 'STALE', 'NOT_GENERATED', 'NO_TEMPLATE'];
    for (const state of states) {
      const anchor = renderPill(state);
      expect(anchor.tagName).toBe('A');
      expect(anchor.getAttribute('href')).toBeTruthy();
    }
  });
  describe('status pulse', () => {
    const PULSE = 'motion-safe:animate-status-pulse';

    beforeEach(() => reducedMotion.mockReturnValue(false));

    it('does not pulse on first render, pulses once on a state change, and stops on animation end', () => {
      expect(renderPill('STALE').classList).not.toContain(PULSE);
      const pill = renderPill('UP_TO_DATE');
      expect(pill.classList).toContain(PULSE);
      act(() => {
        pill.dispatchEvent(new Event('animationend', { bubbles: true }));
      });
      expect(container.querySelector('a')!.classList).not.toContain(PULSE);
      expect(renderPill('UP_TO_DATE').classList).not.toContain(PULSE);
    });

    it('does not pulse under reduced motion', () => {
      reducedMotion.mockReturnValue(true);
      renderPill('STALE');
      expect(renderPill('UP_TO_DATE').classList).not.toContain(PULSE);
    });
  });
});
