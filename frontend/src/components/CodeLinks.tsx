import type { CodeLinks } from '../api/releases';

export const REASON_LABELS: Record<string, string> = {
  JIRA_UNAUTHORIZED: 'Jira credential rejected',
  JIRA_RATE_LIMITED: 'Jira rate limit',
  JIRA_UNREACHABLE: 'Jira unreachable',
  DEV_STATUS_ERROR: 'Jira development data error',
  DEV_STATUS_UNRECOGNISED: 'unrecognised Jira development data',
  TIMEOUT: 'timed out',
  ADO_UNAUTHORIZED: 'Azure DevOps token rejected',
  ADO_CREDENTIAL_EXPIRED: 'Azure DevOps token expired',
  ADO_CREDENTIAL_UNREADABLE: 'stored Azure DevOps token unreadable',
  ADO_ORG_MISMATCH: 'repository in another Azure DevOps organization',
  ADO_NOT_FOUND: 'not found in Azure DevOps',
  ADO_RATE_LIMITED: 'Azure DevOps rate limit',
  ADO_UNREACHABLE: 'Azure DevOps unreachable',
  ADO_ERROR: 'Azure DevOps error',
  COMMIT_LIMIT: 'too many commits; list truncated',
};

function reasonLabel(reason: string | null): string {
  if (reason === null) return 'unknown reason';
  return REASON_LABELS[reason] ?? reason;
}

// T-06-29: URLs come from repo users, so only an Azure DevOps https URL may become an href.
export function safeAdoHref(url: string): string | null {
  return url.startsWith('https://dev.azure.com/') ? url : null;
}

function plural(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? '' : 's'}`;
}

const CHIP_COLOURS: Record<CodeLinks['status'], string> = {
  LINKED: 'bg-blue-50 text-blue-800',
  NO_LINK: 'bg-gray-100 text-gray-600',
  FAILED: 'bg-red-50 text-red-700',
  NOT_ATTEMPTED: 'bg-gray-50 text-gray-500',
};

export function CodeLinksChip(props: { links: CodeLinks; onRetry?: () => void; retryDisabled?: boolean }) {
  const { links } = props;
  let text: string;
  switch (links.status) {
    case 'LINKED':
      text = `${plural(links.commits.length, 'commit')} · ${plural(links.pullRequests.length, 'PR')}`;
      break;
    case 'NO_LINK':
      text = 'No link';
      break;
    case 'FAILED':
      text = `Couldn't fetch (${reasonLabel(links.reason)})`;
      break;
    default:
      text = 'Not checked';
  }
  return (
    <span className="inline-flex flex-wrap items-center gap-2">
      <span data-status={links.status} className={`rounded px-2 py-0.5 text-xs ${CHIP_COLOURS[links.status]}`}>
        {text}
      </span>
      {links.status === 'LINKED' && links.commitsSource === 'KEY_TAGGED' && (
        <span
          className="rounded bg-gray-50 px-2 py-0.5 text-xs text-gray-600"
          title="Commits are those whose message names the ticket key, as reported by Jira"
        >
          key-tagged only
        </span>
      )}
      {links.status === 'LINKED' && links.incomplete && (
        <span className="rounded bg-amber-50 px-2 py-0.5 text-xs text-amber-800">incomplete</span>
      )}
      {links.status === 'FAILED' && props.onRetry && (
        <button
          type="button"
          onClick={props.onRetry}
          disabled={props.retryDisabled}
          className="text-xs text-blue-600 underline disabled:opacity-50"
        >
          Retry
        </button>
      )}
    </span>
  );
}

function OpenLink({ url }: { url: string }) {
  const href = safeAdoHref(url);
  if (href === null) return <span className="text-gray-500">{url}</span>;
  return (
    <a href={href} target="_blank" rel="noopener noreferrer" className="text-blue-600 underline">
      Open
    </a>
  );
}

export function CodeLinksDetail({ links }: { links: CodeLinks }) {
  const failed = links.status === 'FAILED';
  const hasContent = links.commits.length > 0 || links.pullRequests.length > 0;
  return (
    <>
      <h3 className="mt-4 text-sm font-semibold text-gray-900">Code links</h3>
      {failed && (
        <p className="mt-2 text-sm text-red-700">
          {hasContent
            ? `Couldn't refresh (${reasonLabel(links.reason)}). Showing the last known links.`
            : `Couldn't fetch (${reasonLabel(links.reason)}).`}
        </p>
      )}
      {links.status === 'LINKED' && links.incomplete && (
        <p className="mt-2 text-sm text-amber-800">
          Some detail could not be fetched: {reasonLabel(links.reason)}
        </p>
      )}
      {links.commits.length > 0 && (
        <ul className="mt-2 space-y-1">
          {links.commits.map((commit) => (
            <li key={commit.sha} className="text-sm text-gray-700">
              <code>{commit.shortSha}</code> {commit.message} · {commit.author ?? '—'} ·{' '}
              {new Date(commit.authoredAt).toLocaleString()} · <OpenLink url={commit.url} />
            </li>
          ))}
        </ul>
      )}
      {links.pullRequests.length > 0 && (
        <ul className="mt-2 space-y-1">
          {links.pullRequests.map((pr) => (
            <li key={pr.id} className="text-sm text-gray-700">
              {pr.title} · {pr.status}
              {!pr.countsTowardCommits && (
                <span className="ml-1 rounded bg-gray-100 px-1 text-xs text-gray-600">environment merge</span>
              )}{' '}
              · <OpenLink url={pr.url} />
            </li>
          ))}
        </ul>
      )}
      {links.branches.map((branch) => (
        <p key={branch} className="mt-2 text-sm text-gray-600">
          Branch: {branch}
        </p>
      ))}
      {!hasContent && !failed && links.branches.length === 0 && (
        <p className="mt-2 text-sm text-gray-600">No linked code.</p>
      )}
    </>
  );
}
