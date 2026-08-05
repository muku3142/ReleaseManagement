import { describe, expect, it } from 'vitest';
import { addResultMessage } from '../../src/routes/ReleaseDetailRoute';

describe('add-result banner copy (D-03-13)', () => {
  it('reports the keys Jira did not return rather than folding them into a plain success', () => {
    expect(addResultMessage(1, 0, 2, 'Sprint 42', '2026.08')).toBe(
      '1 added, 0 already on this release, 2 not found in Jira.',
    );
    expect(addResultMessage(0, 0, 3, 'Sprint 42', '2026.08')).toBe(
      '0 added, 0 already on this release, 3 not found in Jira.',
    );
  });

  it('keeps the three contracted variants byte-for-byte when nothing was missing', () => {
    expect(addResultMessage(3, 0, 0, 'Sprint 42', '2026.08')).toBe(
      '3 ticket(s) added to Sprint 42 2026.08.',
    );
    expect(addResultMessage(0, 3, 0, 'Sprint 42', '2026.08')).toBe(
      'All 3 selected tickets were already on this release.',
    );
    expect(addResultMessage(2, 1, 0, 'Sprint 42', '2026.08')).toBe(
      '2 added, 1 already on this release.',
    );
  });
});
