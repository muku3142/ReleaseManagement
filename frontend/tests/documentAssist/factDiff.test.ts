import { describe, expect, it } from 'vitest';
import { factDiff } from '../../src/documentAssist/factDiff';

const none = { missingKeys: [], addedKeys: [], missingVersions: [], addedVersions: [] };

describe('factDiff', () => {
  it('reports missing and added keys and versions', () => {
    expect(factDiff('PROJ-1 and PROJ-2 in v4.2.0', 'PROJ-1 and PROJ-3 in 4.2.0')).toEqual({
      missingKeys: ['PROJ-2'],
      addedKeys: ['PROJ-3'],
      missingVersions: ['v4.2.0'],
      addedVersions: ['4.2.0'],
    });
  });

  it('ignores order and repetition', () => {
    expect(factDiff('PROJ-1 PROJ-2 PROJ-1 1.2.3', '1.2.3 PROJ-2 PROJ-1')).toEqual(none);
  });

  it('applies the key and version boundaries', () => {
    expect(factDiff('XPROJ-1x abc-12', '')).toEqual(none);
    expect(factDiff('PROJ-12', 'PROJ-120').missingKeys).toEqual(['PROJ-12']);
    expect(factDiff('1.2.3-rc.1', '').missingVersions).toEqual(['1.2.3-rc.1']);
    expect(factDiff('UTF-8', 'UTF-8')).toEqual(none);
  });

  it('sorts and de-duplicates', () => {
    expect(factDiff('B-2 A-1 B-2', '').missingKeys).toEqual(['A-1', 'B-2']);
  });

  it('does not warn on known blind spots', () => {
    expect(factDiff('- PROJ-1\n- PROJ-2', '- PROJ-2\n- PROJ-1\n')).toEqual(none);
    expect(factDiff('Deployed to UAT', 'Deployed to PROD')).toEqual(none);
    expect(factDiff('Released 2026-03-14', 'Released 2026-03-15')).toEqual(none);
  });
});
