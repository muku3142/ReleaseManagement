import type { ReactNode } from 'react';
import MarkdownIt from 'markdown-it';
import type { ChromeFields } from '../api/templates';

// D-04-05 on the client: html:false makes markdown-it escape raw HTML rather than pass it through,
// which is the same posture the backend takes with flexmark. UI-SPEC treats an inability to suppress
// raw HTML as a blocking library-selection defect, so this option is not negotiable.
export const md = new MarkdownIt({ html: false, linkify: false, typographer: false });

// markdown-it has already escaped <, >, & and " in every text node, so the captured token is safe to
// interpolate. This is the one place this app builds HTML by hand, and it is confined to replacing a
// literal marker the renderer itself produced.
export function chipUnknownMarkers(html: string): string {
  return html.replace(
    /\[\[unknown: ([^\]]*)\]\]/g,
    (_m, token: string) =>
      `<code class="rounded bg-amber-100 px-1 py-0.5 font-mono text-xs text-amber-900">[[unknown: ${token}]]</code>`,
  );
}

// The React-children counterpart of chipUnknownMarkers: chrome strings are rendered as plain text
// children, never through the raw-HTML-injection prop the body container below uses, so the
// marker is split into an array of strings and <code> elements instead of HTML.
function renderChromeNodes(text: string): ReactNode[] {
  const nodes: ReactNode[] = [];
  const regex = /\[\[unknown: ([^\]]*)\]\]/g;
  let lastIndex = 0;
  let match: RegExpExecArray | null;
  let key = 0;
  while ((match = regex.exec(text)) !== null) {
    if (match.index > lastIndex) nodes.push(text.slice(lastIndex, match.index));
    nodes.push(
      <code key={key++} className="rounded bg-amber-100 px-1 py-0.5 font-mono text-xs text-amber-900">
        {`[[unknown: ${match[1]}]]`}
      </code>,
    );
    lastIndex = match.index + match[0].length;
  }
  if (lastIndex < text.length) nodes.push(text.slice(lastIndex));
  return nodes;
}

// D-04-15: this preview is an approximation, not a layout specification. Nothing paginates until
// Phase 5, so DOC-08's PDF/DOCX parity cannot be verified from what is on screen here. The document
// viewer (05-12) reuses this same placeholder for the same reason: the stored snapshot is
// pre-pagination Markdown, so there is no resolved page count to show there either.
const PAGE_NUMBER_PLACEHOLDER = <span className="ml-2 italic text-gray-400">Page X of Y</span>;

// The cover/header/footer chrome shared by the authoring preview (TemplatePreview) and the document
// viewer (DocumentViewerRoute) - one set of markup so a generated document looks pixel-identical to
// what the author saw while authoring. Callers pass already-resolved chrome text: this component
// renders, it does not resolve Mustache placeholders.
export function RenderedDocument(props: { chrome: ChromeFields; logoUrl: string | null; children: ReactNode }) {
  const chrome = props.chrome;
  const headerVisible = chrome.headerText.trim() !== '';
  const footerVisible = chrome.footerText.trim() !== '';

  return (
    <>
      {chrome.coverEnabled && (
        <div className="mb-6 rounded border border-gray-300 bg-white p-8 text-center shadow-sm">
          {props.logoUrl && <img src={props.logoUrl} alt="" className="mx-auto max-h-20" />}
          <p className="text-2xl font-semibold">{renderChromeNodes(chrome.coverTitle)}</p>
          <p className="mt-2 text-base text-gray-600">{renderChromeNodes(chrome.coverSubtitle)}</p>
        </div>
      )}

      {headerVisible && (
        <div className="border-b border-gray-300 bg-gray-100 px-4 py-2 text-xs text-gray-700">
          {renderChromeNodes(chrome.headerText)}
          {chrome.pageNumbersEnabled && PAGE_NUMBER_PLACEHOLDER}
        </div>
      )}

      {props.children}

      {footerVisible && (
        <div className="border-t border-gray-300 bg-gray-100 px-4 py-2 text-xs text-gray-700">
          {renderChromeNodes(chrome.footerText)}
          {chrome.pageNumbersEnabled && !headerVisible && PAGE_NUMBER_PLACEHOLDER}
        </div>
      )}
    </>
  );
}
