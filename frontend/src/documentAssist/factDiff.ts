// Keep these two regexes byte-identical with the harness copy in plan 14-13.
export const TICKET_KEY = /(?<![A-Za-z0-9_])[A-Z][A-Z0-9_]*-\d+(?![A-Za-z0-9_])/g;
export const VERSION = /(?<![\w.])v?\d+(?:\.\d+){1,3}(?:-[0-9A-Za-z][0-9A-Za-z.]*)?(?![\w])/g;

export type FactDiff = {
  missingKeys: string[];
  addedKeys: string[];
  missingVersions: string[];
  addedVersions: string[];
};

function extract(text: string, pattern: RegExp): Set<string> {
  return new Set(text.match(pattern) ?? []);
}

function difference(a: Set<string>, b: Set<string>): string[] {
  return [...a].filter((x) => !b.has(x)).sort();
}

export function factDiff(original: string, result: string): FactDiff {
  const originalKeys = extract(original, TICKET_KEY);
  const resultKeys = extract(result, TICKET_KEY);
  const originalVersions = extract(original, VERSION);
  const resultVersions = extract(result, VERSION);
  return {
    missingKeys: difference(originalKeys, resultKeys),
    addedKeys: difference(resultKeys, originalKeys),
    missingVersions: difference(originalVersions, resultVersions),
    addedVersions: difference(resultVersions, originalVersions),
  };
}
