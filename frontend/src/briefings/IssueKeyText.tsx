import { Fragment } from 'react';
import { useJiraSite } from '../api/jiraSite';
import { useProducts } from '../api/products';

const ISSUE_KEY = /[A-Z][A-Z0-9_]+-\d+/g;
const STRUCTURED_KEY = /^[A-Z][A-Z0-9_]{1,9}-\d+$/;

type Props = { text: string; knownKeys: string[]; baseUrl?: string; linkKey?: string | null };

export function useIssueLinkContext(): { knownKeys: string[]; baseUrl?: string } {
  const products = useProducts();
  const site = useJiraSite();
  const knownKeys = [...new Set((products.data?.products ?? []).flatMap((p) => p.jiraProjectKeys ?? []))];
  return { knownKeys, baseUrl: site.data?.baseUrl };
}

function isLinkable(key: string, knownKeys: string[], baseUrl?: string): boolean {
  return !!baseUrl && knownKeys.includes(key.slice(0, key.lastIndexOf('-')));
}

function KeyLink({ issueKey, baseUrl }: { issueKey: string; baseUrl: string }) {
  return (
    <>
      <a
        href={`${baseUrl.replace(/\/+$/, '')}/browse/${issueKey}`}
        target="_blank"
        rel="noopener noreferrer"
        className="text-blue-600 hover:underline"
      >
        {issueKey}
      </a>
      <span className="sr-only"> (opens in a new tab)</span>
    </>
  );
}

export function IssueKeyText({ text, knownKeys, baseUrl, linkKey }: Props) {
  const structured = linkKey && STRUCTURED_KEY.test(linkKey) && isLinkable(linkKey, knownKeys, baseUrl) ? linkKey : null;
  const parts: Array<string | { key: string }> = [];
  let last = 0;
  for (const match of text.matchAll(ISSUE_KEY)) {
    const key = match[0];
    if (!(isLinkable(key, knownKeys, baseUrl) || key === structured)) continue;
    if (match.index > last) parts.push(text.slice(last, match.index));
    parts.push({ key });
    last = match.index + key.length;
  }
  if (last < text.length) parts.push(text.slice(last));
  return (
    <>
      {parts.map((part, i) =>
        typeof part === 'string' ? (
          <Fragment key={i}>{part}</Fragment>
        ) : (
          <KeyLink key={i} issueKey={part.key} baseUrl={baseUrl!} />
        ),
      )}
    </>
  );
}
