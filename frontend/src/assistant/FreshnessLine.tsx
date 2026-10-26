import { useAssistantFreshness } from '../api/assistant';

const STALE_AFTER_MS = 24 * 60 * 60 * 1000;
const UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['day', 24 * 60 * 60 * 1000],
  ['hour', 60 * 60 * 1000],
  ['minute', 60 * 1000],
  ['second', 1000],
];

const formatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

function relativeTime(ageMs: number): string {
  const [unit, size] = UNITS.find(([, ms]) => ageMs >= ms) ?? UNITS[UNITS.length - 1];
  return formatter.format(-Math.floor(ageMs / size), unit);
}

export function FreshnessLine({ enabled }: { enabled: boolean }) {
  const { data } = useAssistantFreshness(enabled);
  if (!data) return null;

  if (data.lastIndexedAt === null) {
    return <p className="text-xs text-amber-700">Search index hasn&apos;t been built yet.</p>;
  }

  const indexedAt = new Date(data.lastIndexedAt);
  const ageMs = Math.max(0, Date.now() - indexedAt.getTime());
  const stale = ageMs > STALE_AFTER_MS;
  return (
    <p className={`text-xs ${stale ? 'text-amber-700' : 'text-gray-500'}`} title={indexedAt.toLocaleString()}>
      {`Search index last updated ${relativeTime(ageMs)}`}
      {stale && ' · Recent changes may be missing.'}
    </p>
  );
}
