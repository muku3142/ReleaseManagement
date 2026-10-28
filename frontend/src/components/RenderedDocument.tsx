import type { ReactNode } from 'react';
import MarkdownIt from 'markdown-it';
import type { ChromeFields } from '../api/templates';

// D-04-05 on the client: html:false makes markdown-it escape raw HTML rather than pass it through,
// which is the same posture the backend takes with flexmark. UI-SPEC treats an inability to suppress
// raw HTML as a blocking library-selection defect, so this option is not negotiable.
export const md = new MarkdownIt({ html: false, linkify: false, typographer: false });

// Model output can carry a remote image URL that exfiltrates data the moment it renders, so AI text
// is rendered with env.blockImages: images become text and links open without opener access.
// Other callers pass no env and get markdown-it's default output.
const defaultImage = md.renderer.rules.image;
md.renderer.rules.image = (tokens, idx, options, env, self) => {
  if ((env as { blockImages?: boolean } | undefined)?.blockImages) {
    return `[image: ${md.utils.escapeHtml(tokens[idx].content)}]`;
  }
  return defaultImage ? defaultImage(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options);
};

const defaultLinkOpen = md.renderer.rules.link_open;
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  if ((env as { blockImages?: boolean } | undefined)?.blockImages) {
    tokens[idx].attrSet('rel', 'noopener noreferrer');
    tokens[idx].attrSet('target', '_blank');
  }
  return defaultLinkOpen ? defaultLinkOpen(tokens, idx, options, env, self) : self.renderToken(tokens, idx, options);
};

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

// The snapshot is already Mustache-resolved (D-05-05), so this needs markdown-it only. A render
// failure here is defensive, not an expected path - DOC generation only succeeds from a template
// that already passed save-time validation - so it falls back to TemplatePreview's own neutral panel.
export function MarkdownBody({ markdown, blockImages = false }: { markdown: string; blockImages?: boolean }) {
  try {
    const html = chipUnknownMarkers(md.render(markdown, { blockImages }));
    return (
      <div
        className="paper prose prose-gray max-w-none bg-white p-4 text-zinc-950"
        dangerouslySetInnerHTML={{ __html: html }}
      />
    );
  } catch {
    return (
      <div className="m-4 bg-gray-50 p-4 text-sm text-gray-600">
        Preview unavailable — check your template syntax (e.g., every {'{{#x}}'} needs a matching {'{{/x}}'}).
      </div>
    );
  }
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

  // D-23-06: a generated document is never themed, so it stays light paper inside a dark app.
  return (
    <div className="paper border border-border bg-white text-zinc-950 shadow-xs">
      {chrome.coverEnabled && (
        <div className="mb-6 rounded border border-gray-300 bg-white p-8 text-center shadow-sm">
          {/* Inline style rather than a class: Tailwind's max-h-* scale is fixed steps with no mm unit,
          and no arbitrary-value variant is used anywhere else in this codebase, so building a dynamic
          class string would be more machinery than a style prop for the same result. mm is the stored
          unit precisely so this value needs no conversion here, in the PDF's CSS, or in DOCX's EMU.
          max-height, not height: a logo shorter than the configured height keeps its natural size
          (D-05.1-06), which is the same behaviour the PDF and DOCX renderers implement. */}
          {props.logoUrl && (
            <img
              src={props.logoUrl}
              alt=""
              className="mx-auto"
              style={{ maxHeight: `${chrome.logoRenderHeightMm}mm` }}
            />
          )}
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
    </div>
  );
}
