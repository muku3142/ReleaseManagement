import { act } from 'react';
import { createRoot, type Root } from 'react-dom/client';
import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { CodeLinksChip, CodeLinksDetail, safeAdoHref } from '../../src/components/CodeLinks';
import type { CodeLinks } from '../../src/api/releases';

const ADO = 'https://dev.azure.com/acme/proj/_git/repo';

function links(overrides: Partial<CodeLinks> = {}): CodeLinks {
  return {
    status: 'LINKED',
    reason: null,
    incomplete: false,
    attemptedAt: '2026-10-12T00:00:00Z',
    commitsSource: 'ADO_COMPLETE',
    pullRequests: [],
    commits: [],
    branches: [],
    ...overrides,
  };
}

function commit(n: number, url = `${ADO}/commit/${n}`) {
  return {
    sha: `abcdef${n}0000`,
    shortSha: `abcdef${n}`,
    message: `Fix thing ${n}`,
    author: 'dev@acme.com',
    authoredAt: '2026-10-01T10:00:00Z',
    url,
  };
}

function pr(id: string, overrides: Partial<CodeLinks['pullRequests'][number]> = {}) {
  return {
    id,
    title: `PR ${id}`,
    status: 'completed',
    url: `${ADO}/pullrequest/${id}`,
    sourceBranch: 'feature/x',
    targetBranch: 'main',
    countsTowardCommits: true,
    ...overrides,
  };
}

let container: HTMLDivElement;
let root: Root;

async function render(node: React.ReactNode) {
  await act(async () => root.render(node));
}

describe('CodeLinks components', () => {
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
  });

  it('shows commit and PR counts, pluralised', async () => {
    await render(
      <CodeLinksChip links={links({ commits: [commit(1), commit(2), commit(3)], pullRequests: [pr('1')] })} />,
    );
    expect(container.textContent).toBe('3 commits · 1 PR');
    await render(
      <CodeLinksChip links={links({ commits: [commit(1)], pullRequests: [pr('1'), pr('2')] })} />,
    );
    expect(container.textContent).toBe('1 commit · 2 PRs');
  });

  it('labels key-tagged commits and incomplete detail', async () => {
    await render(
      <CodeLinksChip links={links({ commitsSource: 'KEY_TAGGED', incomplete: true, reason: 'ADO_NOT_FOUND' })} />,
    );
    expect(container.textContent).toContain('key-tagged only');
    expect(container.textContent).toContain('incomplete');
    expect(container.querySelector('[title]')?.getAttribute('title')).toContain('names the ticket key');

    await render(<CodeLinksDetail links={links({ incomplete: true, reason: 'ADO_NOT_FOUND' })} />);
    expect(container.textContent).toContain('Some detail could not be fetched: not found in Azure DevOps');
  });

  it('renders No link and Not checked distinctly from a failure', async () => {
    await render(<CodeLinksChip links={links({ status: 'NO_LINK' })} />);
    expect(container.textContent).toBe('No link');
    expect(container.querySelector('[data-status="NO_LINK"]')).not.toBeNull();
    await render(<CodeLinksChip links={links({ status: 'NOT_ATTEMPTED' })} />);
    expect(container.textContent).toBe('Not checked');
  });

  it('shows the failure reason and a Retry button that calls onRetry', async () => {
    const onRetry = vi.fn();
    await render(<CodeLinksChip links={links({ status: 'FAILED', reason: 'TIMEOUT' })} onRetry={onRetry} />);
    expect(container.textContent).toContain("Couldn't fetch (timed out)");
    expect(container.querySelector('[data-status="FAILED"]')).not.toBeNull();
    await act(async () => container.querySelector('button')!.click());
    expect(onRetry).toHaveBeenCalledOnce();
  });

  it('offers no Retry without onRetry', async () => {
    await render(<CodeLinksChip links={links({ status: 'FAILED', reason: 'TIMEOUT' })} />);
    expect(container.querySelector('button')).toBeNull();
  });

  it('falls back to the raw reason when it is unknown', async () => {
    await render(<CodeLinksChip links={links({ status: 'FAILED', reason: 'SOMETHING_NEW' })} />);
    expect(container.textContent).toContain("Couldn't fetch (SOMETHING_NEW)");
  });

  it('lists the last known links on a failed refresh', async () => {
    await render(
      <CodeLinksDetail links={links({ status: 'FAILED', reason: 'ADO_UNREACHABLE', commits: [commit(1)] })} />,
    );
    expect(container.textContent).toContain(
      "Couldn't refresh (Azure DevOps unreachable). Showing the last known links.",
    );
    expect(container.textContent).toContain('abcdef1');
    expect(container.textContent).toContain('Fix thing 1');
  });

  it('shows a branch as context for a branch-only ticket, with no lists', async () => {
    await render(<CodeLinksDetail links={links({ status: 'NO_LINK', branches: ['feature/EXAMPLE-1'] })} />);
    expect(container.textContent).toContain('Branch: feature/EXAMPLE-1');
    expect(container.querySelector('ul')).toBeNull();
  });

  it('says No linked code when there is nothing to list', async () => {
    await render(<CodeLinksDetail links={links({ status: 'NO_LINK' })} />);
    expect(container.textContent).toContain('No linked code.');
  });

  it('renders only Azure DevOps urls as anchors, with noopener noreferrer', async () => {
    await render(
      <CodeLinksDetail
        links={links({ commits: [commit(1), commit(2, 'javascript:alert(1)')], pullRequests: [pr('9')] })}
      />,
    );
    const anchors = [...container.querySelectorAll('a')];
    expect(anchors).toHaveLength(2);
    for (const a of anchors) {
      expect(a.getAttribute('href')?.startsWith('https://dev.azure.com/')).toBe(true);
      expect(a.getAttribute('target')).toBe('_blank');
      expect(a.getAttribute('rel')).toBe('noopener noreferrer');
    }
    expect(container.textContent).toContain('javascript:alert(1)');
  });

  it('labels environment-merge PRs', async () => {
    await render(<CodeLinksDetail links={links({ pullRequests: [pr('1', { countsTowardCommits: false })] })} />);
    expect(container.textContent).toContain('environment merge');
  });

  it('safeAdoHref accepts only the Azure DevOps prefix', () => {
    expect(safeAdoHref(ADO)).toBe(ADO);
    expect(safeAdoHref('http://dev.azure.com/x')).toBeNull();
    expect(safeAdoHref('https://dev.azure.com.evil.com/x')).toBeNull();
  });
});
