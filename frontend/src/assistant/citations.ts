import type { SourceView } from '../api/assistant';
import { DOCUMENT_TYPE_LABELS, type DocumentType } from '../api/templates';

export type ChipTarget =
  | { to: '/releases/$releaseId'; params: { releaseId: string }; hash?: string }
  | {
      to: '/releases/$releaseId/documents/$documentType';
      params: { releaseId: string; documentType: DocumentType };
      search: { revision: number };
    }
  | null;

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const TICKET_KEY = /^[A-Z][A-Z0-9_]+-\d+$/;
const TOOL_TITLE_PREFIX = 'ReleaseManagement: ';

const KIND_WORD: Record<SourceView['kind'], string> = {
  TICKET: 'Ticket',
  DOCUMENT: 'Document',
  MANUAL_ITEM: 'Manual item',
  CODE_LINKS: 'Code links',
  TOOL: 'Lookup',
};

function validReleaseId(source: SourceView): string | null {
  return source.releaseId !== null && UUID.test(source.releaseId) ? source.releaseId : null;
}

function validTicketKey(source: SourceView): string | null {
  return source.ticketKey !== null && TICKET_KEY.test(source.ticketKey) ? source.ticketKey : null;
}

function releaseTarget(releaseId: string | null, ticketKey?: string | null): ChipTarget {
  if (releaseId === null) return null;
  return ticketKey
    ? { to: '/releases/$releaseId', params: { releaseId }, hash: `ticket-${ticketKey}` }
    : { to: '/releases/$releaseId', params: { releaseId } };
}

function documentTarget(source: SourceView, releaseId: string | null): ChipTarget {
  const type = source.documentType;
  if (releaseId === null || type === null || !(type in DOCUMENT_TYPE_LABELS)) return null;
  const revision = source.revision;
  if (revision === null || !Number.isInteger(revision) || revision < 1) return null;
  return {
    to: '/releases/$releaseId/documents/$documentType',
    params: { releaseId, documentType: type },
    search: { revision },
  };
}

function fallbackLabel(source: SourceView): string {
  return source.title ?? KIND_WORD[source.kind];
}

// Labels and targets come only from the server's SourceView fields, validated again here; no external URL is ever built.
export function chipFor(source: SourceView): { label: string; target: ChipTarget } {
  const prefix = `[${source.n}] `;
  const releaseId = validReleaseId(source);
  const ticketKey = validTicketKey(source);

  switch (source.kind) {
    case 'TICKET':
      return {
        label: prefix + (ticketKey ? `Ticket ${ticketKey}` : fallbackLabel(source)),
        target: ticketKey ? releaseTarget(releaseId, ticketKey) : null,
      };
    case 'CODE_LINKS':
      return {
        label: prefix + (ticketKey ? `Code links ${ticketKey}` : fallbackLabel(source)),
        target: ticketKey ? releaseTarget(releaseId, ticketKey) : null,
      };
    case 'DOCUMENT': {
      const target = documentTarget(source, releaseId);
      const { documentType, releaseVersion, revision } = source;
      const label =
        documentType !== null && documentType in DOCUMENT_TYPE_LABELS && releaseVersion !== null && revision !== null
          ? `${DOCUMENT_TYPE_LABELS[documentType]} ${releaseVersion} rev ${revision}`
          : fallbackLabel(source);
      return { label: prefix + label, target };
    }
    case 'MANUAL_ITEM':
      return { label: `${prefix}Manual item ${source.title ?? ''}`.trimEnd(), target: releaseTarget(releaseId) };
    case 'TOOL': {
      const title = source.title ?? KIND_WORD.TOOL;
      return {
        label: prefix + (title.startsWith(TOOL_TITLE_PREFIX) ? title.slice(TOOL_TITLE_PREFIX.length) : title),
        target: releaseTarget(releaseId),
      };
    }
  }
}
