import { describe, expect, it } from 'vitest';
import { chipFor } from '../../src/assistant/citations';
import type { SourceView } from '../../src/api/assistant';

const RID = '3f2b8c1e-5a4d-4e6f-9a7b-1c2d3e4f5a6b';

function source(overrides: Partial<SourceView>): SourceView {
  return {
    n: 1,
    kind: 'TICKET',
    title: null,
    releaseId: RID,
    releaseVersion: null,
    ticketKey: null,
    documentType: null,
    revision: null,
    ...overrides,
  };
}

describe('chipFor', () => {
  it('links a ticket to its row anchor', () => {
    expect(chipFor(source({ n: 2, ticketKey: 'PROJ-123' }))).toEqual({
      label: '[2] Ticket PROJ-123',
      target: { to: '/releases/$releaseId', params: { releaseId: RID }, hash: 'ticket-PROJ-123' },
    });
  });

  it('links a document revision with the revision search param', () => {
    expect(
      chipFor(source({ n: 3, kind: 'DOCUMENT', documentType: 'RELEASE_NOTES', releaseVersion: '4.2.0', revision: 3 })),
    ).toEqual({
      label: '[3] Release Notes 4.2.0 rev 3',
      target: {
        to: '/releases/$releaseId/documents/$documentType',
        params: { releaseId: RID, documentType: 'RELEASE_NOTES' },
        search: { revision: 3 },
      },
    });
  });

  it('links code links to the ticket row anchor', () => {
    const chip = chipFor(source({ n: 4, kind: 'CODE_LINKS', ticketKey: 'PROJ-123' }));
    expect(chip.label).toBe('[4] Code links PROJ-123');
    expect(chip.target).toEqual({ to: '/releases/$releaseId', params: { releaseId: RID }, hash: 'ticket-PROJ-123' });
  });

  it('links a manual item to the release', () => {
    expect(chipFor(source({ n: 5, kind: 'MANUAL_ITEM', title: 'Run migration' }))).toEqual({
      label: '[5] Manual item Run migration',
      target: { to: '/releases/$releaseId', params: { releaseId: RID } },
    });
  });

  it('strips the tool title prefix and links to the release when known', () => {
    const chip = chipFor(source({ n: 6, kind: 'TOOL', title: 'ReleaseManagement: release Atlas 4.2.0' }));
    expect(chip.label).toBe('[6] release Atlas 4.2.0');
    expect(chip.target).toEqual({ to: '/releases/$releaseId', params: { releaseId: RID } });
    expect(chipFor(source({ kind: 'TOOL', title: 'x', releaseId: null })).target).toBeNull();
  });

  it.each([
    ['javascript url as release id', source({ releaseId: 'javascript:alert(1)', ticketKey: 'PROJ-1' })],
    ['non-uuid release id', source({ releaseId: 'abc', ticketKey: 'PROJ-1' })],
    ['malformed ticket key', source({ ticketKey: 'x y' })],
    ['unknown document type', source({ kind: 'DOCUMENT', documentType: 'OTHER' as never, revision: 1, releaseVersion: '1' })],
    ['revision zero', source({ kind: 'DOCUMENT', documentType: 'RUNBOOK', revision: 0, releaseVersion: '1' })],
    ['negative revision', source({ kind: 'DOCUMENT', documentType: 'RUNBOOK', revision: -1, releaseVersion: '1' })],
  ])('gives an unlinked chip for %s', (_name, input) => {
    expect(chipFor(input).target).toBeNull();
  });

  it('falls back to the title for an unlinked ticket', () => {
    expect(chipFor(source({ n: 7, ticketKey: 'x y', title: 'Something' })).label).toBe('[7] Something');
  });
});
