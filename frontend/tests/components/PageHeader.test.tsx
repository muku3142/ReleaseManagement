import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { PageHeader } from '../../src/components/layout/PageHeader';
import { PageContainer } from '../../src/components/layout/PageContainer';

let container: HTMLDivElement;
let root: Root;

describe('PageHeader and PageContainer', () => {
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

  it('renders one h1, the description and the actions slot', () => {
    act(() => {
      root.render(<PageHeader title="Releases" description="Every release." actions={<button type="button">New</button>} />);
    });
    const headings = container.querySelectorAll('h1');
    expect(headings).toHaveLength(1);
    expect(headings[0].textContent).toBe('Releases');
    expect(container.querySelector('p')!.textContent).toBe('Every release.');
    expect(container.querySelector('button')!.textContent).toBe('New');
  });

  it('omits the description paragraph when none is given', () => {
    act(() => {
      root.render(<PageHeader title="Releases" />);
    });
    expect(container.querySelector('p')).toBeNull();
  });

  it('applies the wide width by default and the narrow width on request', () => {
    act(() => {
      root.render(<PageContainer>content</PageContainer>);
    });
    expect((container.firstChild as HTMLElement).className).toContain('max-w-screen-2xl');

    act(() => {
      root.render(<PageContainer width="narrow">content</PageContainer>);
    });
    expect((container.firstChild as HTMLElement).className).toContain('max-w-3xl');
  });
});
