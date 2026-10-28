import { Link } from '@tanstack/react-router';
import type { AppBriefingRun } from '../api/briefings';
import { PRODUCT_CHIP } from '../components/ProductsEditor';
import { attentionText, formatCairo } from './briefingFormat';
import { IssueKeyText } from './IssueKeyText';
import { StatusPill } from './StatusPill';

type LinkContext = { knownKeys: string[]; baseUrl?: string };

const LINK = 'text-blue-600 underline';

export const CREDENTIAL_COPY_BEFORE = "The scheduler's Jira credential is missing or expired. Choose 'Use my Jira credential' in ";

// Target of the link is /settings#briefings.
export function SettingsBriefingsLink() {
  return (
    <Link to="/settings" hash="briefings" className={LINK}>
      Settings &gt; Briefings
    </Link>
  );
}

function Body({ run, link }: { run: AppBriefingRun; link: LinkContext }) {
  if (run.status === 'needs_credential') {
    return (
      <p className="text-sm text-gray-700">
        {CREDENTIAL_COPY_BEFORE}
        <SettingsBriefingsLink />.
      </p>
    );
  }
  if (run.status === 'skipped') {
    return (
      <p className="text-sm text-gray-700">
        No Jira project is set for this product, so the app skipped it.{' '}
        <Link to="/portfolio" search={{ tab: 'products' }} className={LINK}>
          Set a Jira project
        </Link>
      </p>
    );
  }
  if (run.status === 'failed') {
    return <p className="text-sm text-gray-700">This run failed. The next scheduled run will try again.</p>;
  }
  return (
    <div className="space-y-3">
      {run.sections.map((section) => (
        <div key={section.title} className="space-y-1">
          <p className="text-sm font-semibold text-gray-900">{section.title}</p>
          {section.items.length === 0 ? (
            <p className="text-xs text-gray-600">Nothing to report.</p>
          ) : (
            <ul className="list-disc space-y-1 pl-5 text-sm text-gray-700">
              {section.items.map((item, i) => (
                <li key={i}>
                  <IssueKeyText text={item.text} linkKey={item.key} {...link} />
                </li>
              ))}
            </ul>
          )}
        </div>
      ))}
    </div>
  );
}

export function AppFallbackSection({ runs, link }: { runs: AppBriefingRun[]; link: LinkContext }) {
  if (runs.length === 0) return null;
  return (
    <div className="space-y-2" data-testid="app-fallback">
      <p className="flex flex-wrap items-center gap-2 text-xs text-gray-600">
        <span className={PRODUCT_CHIP}>App fallback</span>
        <span>Claude&apos;s result for this slot is missing or failed, so the app ran it.</span>
      </p>
      {runs.map((run) => (
        <details
          key={`${run.productId}-${run.runAt}`}
          open={runs.length === 1 ? true : undefined}
          className="rounded border border-gray-200 px-3 py-2"
        >
          <summary className="cursor-pointer focus-visible:ring-2 focus-visible:ring-blue-600">
            <span className="inline-flex flex-wrap items-center gap-2">
              <span className="text-sm font-semibold text-gray-900">{run.productName}</span>
              <StatusPill kind={run.status} />
              {run.attention !== null && run.attention > 0 && (
                <span className="text-xs tabular-nums text-gray-600">{attentionText(run.attention)}</span>
              )}
              <span className="text-xs text-gray-600">{formatCairo(run.runAt)}</span>
            </span>
            <span className="block text-sm text-gray-700">
              <IssueKeyText text={run.headline} {...link} />
            </span>
          </summary>
          <div className="mt-2 bg-gray-100 p-3">
            <Body run={run} link={link} />
          </div>
        </details>
      ))}
    </div>
  );
}
