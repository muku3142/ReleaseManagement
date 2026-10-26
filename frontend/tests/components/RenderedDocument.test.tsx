import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { MarkdownBody, md } from '../../src/components/RenderedDocument';

describe('MarkdownBody', () => {
  let container: HTMLDivElement;
  let root: Root;

  const render = (markdown: string, blockImages?: boolean) => {
    act(() => root.render(<MarkdownBody markdown={markdown} blockImages={blockImages} />));
    return container;
  };

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

  it('renders a blocked image as text with no img element', () => {
    const c = render('![logo](https://evil.example/x.png?q=1)', true);
    expect(c.querySelector('img')).toBeNull();
    expect(c.textContent).toContain('[image: logo]');
  });

  it('renders an empty alt as [image: ]', () => {
    expect(render('![](https://evil.example/x.png)', true).textContent).toContain('[image: ]');
  });

  it('renders an img as before when images are not blocked', () => {
    expect(render('![logo](https://example.com/x.png)').querySelector('img')).not.toBeNull();
  });

  it('adds rel and target to links only when blocking', () => {
    const blocked = render('[x](https://example.com)', true).querySelector('a')!;
    expect(blocked.getAttribute('rel')).toBe('noopener noreferrer');
    expect(blocked.getAttribute('target')).toBe('_blank');
    const plain = render('[x](https://example.com)').querySelector('a')!;
    expect(plain.getAttribute('rel')).toBeNull();
    expect(plain.getAttribute('target')).toBeNull();
  });

  it('escapes script in alt text', () => {
    const c = render('![<script>alert(1)</script>](https://e.example/x.png)', true);
    expect(c.querySelector('script')).toBeNull();
    expect(c.textContent).toContain('<script>');
  });

  it('keeps the unknown chip and escapes raw HTML in both modes', () => {
    for (const block of [true, false]) {
      const c = render('[[unknown: foo]] <b>x</b>', block);
      expect(c.querySelector('code')?.textContent).toBe('[[unknown: foo]]');
      expect(c.querySelector('b')).toBeNull();
    }
  });

  it('leaves other md.render callers unchanged', () => {
    expect(md.render('![a](https://example.com/x.png)')).toContain('<img');
  });
});
