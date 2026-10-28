import { act, type ReactNode } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ProductOpsMark } from '../../src/components/brand/ProductOpsMark';
import { ProductOpsLogo } from '../../src/components/brand/ProductOpsLogo';
import { PRODUCT_NAME, PRODUCT_TAGLINE } from '../../src/components/brand/brand';
import { usePrefersReducedMotion } from '../../src/hooks/usePrefersReducedMotion';

vi.mock('../../src/hooks/usePrefersReducedMotion', () => ({ usePrefersReducedMotion: vi.fn(() => false) }));

const reducedMotion = vi.mocked(usePrefersReducedMotion);
const LINKS = ['M8 9l8 7', 'M24 9l-8 7', 'M8 23l8-7', 'M24 23l-8-7'];

let container: HTMLDivElement;
let root: Root;

function render(node: ReactNode) {
  act(() => root.render(node));
}

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

describe('ProductOpsMark', () => {
  it('renders a decorative sized svg whose rect fills from its own gradient', () => {
    render(<ProductOpsMark size={28} />);
    const svg = container.querySelector('svg')!;
    expect(container.querySelectorAll('svg')).toHaveLength(1);
    expect(svg.getAttribute('width')).toBe('28');
    expect(svg.getAttribute('height')).toBe('28');
    expect(svg.getAttribute('aria-hidden')).toBe('true');
    const gradientId = svg.querySelector('linearGradient')!.id;
    expect(gradientId).not.toBe('');
    expect(svg.querySelector('rect')!.getAttribute('fill')).toBe(`url(#${gradientId})`);
    expect([...svg.querySelectorAll('g path')].map((p) => p.getAttribute('d'))).toEqual(LINKS);
    const linkGroup = svg.querySelector('g path')!.parentElement!;
    expect(linkGroup.getAttribute('stroke')).toBe('#fff');
    expect(linkGroup.getAttribute('stroke-width')).toBe('2');
    expect(linkGroup.getAttribute('stroke-linecap')).toBe('round');
    expect(linkGroup.getAttribute('opacity')).toBe('.55');
    expect(svg.querySelectorAll('g circle')).toHaveLength(5);
    expect(svg.querySelector('[class*="animate-"]')).toBeNull();
  });

  it('draws its links then pops its nodes when animated', () => {
    render(<ProductOpsMark size={48} animate />);
    const paths = [...container.querySelectorAll('g path')];
    expect(paths).toHaveLength(4);
    for (const path of paths) {
      expect(path.getAttribute('pathLength')).toBe('1');
      expect(path.getAttribute('class')).toBe('motion-safe:animate-mark-link');
    }
    const circles = [...container.querySelectorAll('g circle')];
    expect(circles).toHaveLength(5);
    for (const circle of circles) expect(circle.getAttribute('class')).toBe('motion-safe:animate-mark-node');
  });

  it('stays static when animated under reduced motion', () => {
    reducedMotion.mockReturnValue(true);
    render(<ProductOpsMark size={48} animate />);
    expect(container.querySelector('[class*="animate-"]')).toBeNull();
  });

  it('gives each instance a different gradient id', () => {
    render(
      <>
        <ProductOpsMark size={16} />
        <ProductOpsMark size={16} />
      </>,
    );
    const ids = [...container.querySelectorAll('linearGradient')].map((g) => g.id);
    expect(ids).toHaveLength(2);
    expect(ids[0]).not.toBe(ids[1]);
  });

  it('is an accessible image when titled', () => {
    render(<ProductOpsMark size={16} title={PRODUCT_NAME} />);
    const svg = container.querySelector('svg')!;
    expect(svg.getAttribute('role')).toBe('img');
    expect(svg.querySelector('title')!.textContent).toBe(PRODUCT_NAME);
    expect(svg.hasAttribute('aria-hidden')).toBe(false);
  });
});

describe('ProductOpsLogo', () => {
  it('sidebar variant shows a 28px mark and only the wordmark', () => {
    render(<ProductOpsLogo variant="sidebar" />);
    expect(container.textContent).toBe(PRODUCT_NAME);
    expect(container.querySelector('svg')!.getAttribute('width')).toBe('28');
  });

  it('login variant shows a 48px mark, the wordmark and the tagline', () => {
    render(<ProductOpsLogo variant="login" />);
    expect(container.querySelector('svg')!.getAttribute('width')).toBe('48');
    expect(container.textContent).toContain(PRODUCT_NAME);
    expect(container.textContent).toContain(PRODUCT_TAGLINE);
  });

  it('passes animate through to its mark', () => {
    render(<ProductOpsLogo variant="login" animate />);
    expect(container.querySelector('.motion-safe\\:animate-mark-link')).not.toBeNull();
  });

  it('is static without animate', () => {
    render(<ProductOpsLogo variant="login" />);
    expect(container.querySelector('[class*="animate-"]')).toBeNull();
  });
});
