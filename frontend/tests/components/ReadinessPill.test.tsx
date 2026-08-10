import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { ReadinessPill } from '../../src/components/ReadinessPill';
import type { DocumentReadinessState } from '../../src/api/documents';

type LinkProps = { children?: ReactNode; to?: string; params?: Record<string, string>; hash?: string };

vi.mock('@tanstack/react-router', () => ({
  Link: (props: LinkProps) => {
    let href = props.to ?? '';
    for (const [key, value] of Object.entries(props.params ?? {})) {
      href = href.replace(`$${key}`, value);
    }
    if (props.hash) href += `#${props.hash}`;
    return <a href={href}>{props.children}</a>;
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

  it('UP_TO_DATE renders the "Up to date" text linking to the document viewer', () => {
    const anchor = renderPill('UP_TO_DATE');
    expect(anchor.textContent).toBe('Up to date');
    expect(anchor.getAttribute('href')).toBe('/releases/r1/documents/RELEASE_NOTES');
  });

  it('STALE renders the "Stale" text linking to the document viewer', () => {
    const anchor = renderPill('STALE');
    expect(anchor.textContent).toBe('Stale');
    expect(anchor.getAttribute('href')).toBe('/releases/r1/documents/RELEASE_NOTES');
  });

  it('NOT_GENERATED renders the "Not generated" text linking to the release detail Documents section', () => {
    const anchor = renderPill('NOT_GENERATED');
    expect(anchor.textContent).toBe('Not generated');
    expect(anchor.getAttribute('href')).toBe('/releases/r1#documents');
  });

  it('NO_ACTIVE_TEMPLATE renders the "No active template" text linking to /templates', () => {
    const anchor = renderPill('NO_ACTIVE_TEMPLATE');
    expect(anchor.textContent).toBe('No active template');
    expect(anchor.getAttribute('href')).toBe('/templates');
  });

  it('upToDateAndStaleUseDistinctColours', () => {
    const upToDate = renderPill('UP_TO_DATE');
    expect(upToDate.className).toContain('text-green-700');
    expect(upToDate.className).not.toContain('text-amber-600');

    const stale = renderPill('STALE');
    expect(stale.className).toContain('text-amber-600');
    expect(stale.className).not.toContain('text-green-700');
  });

  it('everyStateIsAKeyboardReachableLink', () => {
    const states: DocumentReadinessState[] = ['UP_TO_DATE', 'STALE', 'NOT_GENERATED', 'NO_ACTIVE_TEMPLATE'];
    for (const state of states) {
      const anchor = renderPill(state);
      expect(anchor.tagName).toBe('A');
      expect(anchor.getAttribute('href')).toBeTruthy();
    }
  });
});
