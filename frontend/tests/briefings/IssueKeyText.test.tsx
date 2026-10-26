import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { IssueKeyText } from '../../src/briefings/IssueKeyText';

let container: HTMLDivElement;
let root: Root;

beforeAll(() => {
  (globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;
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

const render = (ui: React.ReactElement) => act(() => root.render(ui));

describe('IssueKeyText', () => {
  it('links only known project keys and leaves the rest as text', () => {
    render(<IssueKeyText text="CNV-12 blocked by ISO-20022 and UTF-8" knownKeys={['CNV']} baseUrl="https://x.atlassian.net" />);
    const links = container.querySelectorAll('a');
    expect(links).toHaveLength(1);
    expect(links[0].getAttribute('href')).toBe('https://x.atlassian.net/browse/CNV-12');
    expect(links[0].getAttribute('target')).toBe('_blank');
    expect(links[0].getAttribute('rel')).toBe('noopener noreferrer');
    expect(links[0].textContent).toBe('CNV-12');
    expect(container.textContent).toBe('CNV-12 (opens in a new tab) blocked by ISO-20022 and UTF-8');
  });

  it('renders no link without a base URL or known keys', () => {
    render(<IssueKeyText text="CNV-12" knownKeys={['CNV']} />);
    expect(container.querySelector('a')).toBeNull();
    render(<IssueKeyText text="CNV-12" knownKeys={[]} baseUrl="https://x.atlassian.net" />);
    expect(container.querySelector('a')).toBeNull();
    expect(container.textContent).toBe('CNV-12');
  });

  it('renders markup literally with no element or anchor', () => {
    const text = '<img src=x onerror=alert(1)> javascript:alert(1)';
    render(<IssueKeyText text={text} knownKeys={['CNV']} baseUrl="https://x.atlassian.net" />);
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('a')).toBeNull();
    expect(container.textContent).toBe(text);
  });

  it('links a structured key only when it is well formed and its prefix is known', () => {
    render(<IssueKeyText text="CNV-7 title" linkKey="CNV-7" knownKeys={['CNV']} baseUrl="https://x.atlassian.net" />);
    expect(container.querySelectorAll('a')).toHaveLength(1);
    render(<IssueKeyText text="title" linkKey="evil/../x" knownKeys={['CNV']} baseUrl="https://x.atlassian.net" />);
    expect(container.querySelector('a')).toBeNull();
    render(<IssueKeyText text="OTH-7 title" linkKey="OTH-7" knownKeys={['CNV']} baseUrl="https://x.atlassian.net" />);
    expect(container.querySelector('a')).toBeNull();
  });
});
