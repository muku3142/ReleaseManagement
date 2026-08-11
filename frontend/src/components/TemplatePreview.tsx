import { useMemo, type ReactNode } from 'react';
import Mustache from 'mustache';
import type { ChromeFields, ItemView, PlaceholderEntry, ReleaseView } from '../api/templates';
import { md, chipUnknownMarkers, RenderedDocument } from './RenderedDocument';

// D-04-05 again: mustache.js HTML-escapes interpolated values by default, which would turn an
// ampersand in a ticket summary into a visible &amp; once markdown-it escapes it a second time.
// Escaping happens exactly once, in markdown-it.
Mustache.escape = (value) => String(value);

// TMPL-05's client-side counterpart. Not the server's bound - the server refuses; the preview
// truncates and says so, because a half-typed template rendered against a 900-item release is a
// preview practicality, not a defect in the author's template.
const PREVIEW_ITEM_CAP = 200;

type Scope = PlaceholderEntry['scope'];

// The scope a SECTION token pushes into, mirroring PlaceholderEntry.pushes() on the backend - not
// exposed over the API (PlaceholderEntryResponse omits it), so it is hand-mirrored here from the
// small, stable set of section tokens the catalog actually defines. isManual is a boolean section
// and deliberately absent - it keeps the enclosing scope, same as the backend's TemplateScanner.
const SCOPE_PUSHES: Record<string, Scope> = {
  items: 'ITEM',
  groups: 'GROUP',
  comments: 'COMMENT',
  commits: 'COMMIT',
  pullRequests: 'PULL_REQUEST',
};

function toContext(view: ReleaseView) {
  const cap = (items: ItemView[]) => items.slice(0, PREVIEW_ITEM_CAP);
  return {
    release: {
      name: view.name,
      version: view.version,
      status: view.status,
      jiraProjectKey: view.jiraProjectKey,
      createdAt: view.createdAt,
      frozenAt: view.frozenAt,
      lastRefreshedAt: view.lastRefreshedAt,
    },
    items: cap(view.items),
    groups: view.groups.map((g) => ({ groupName: g.groupName, items: cap(g.items) })),
  };
}

function buildValidTokens(entries: PlaceholderEntry[] | undefined): Map<Scope, Set<string>> {
  const map = new Map<Scope, Set<string>>();
  for (const entry of entries ?? []) {
    const set = map.get(entry.scope) ?? new Set<string>();
    set.add(entry.token);
    map.set(entry.scope, set);
  }
  return map;
}

type MarkResult = { ok: true; source: string } | { ok: false };

// The client half of D-04-08, mirroring RenderService.markUnknownTokens's single left-to-right
// character pass and TemplateScanner's scope-stack shape. Unlike the backend, this never throws -
// mid-typing syntax the walk cannot make sense of degrades to { ok: false } (the neutral "Preview
// unavailable" panel), since the Save button, not the live preview, is the authoritative gate.
function markUnknownTokens(source: string, validByScope: Map<Scope, Set<string>>): MarkResult {
  const stack: { name: string; scope: Scope; unknown: boolean }[] = [];
  let out = '';
  const length = source.length;
  let i = 0;

  const isValid = (scope: Scope, name: string) => (validByScope.get(scope) ?? new Set()).has(name);

  while (i < length) {
    const tagStart = source.indexOf('{{', i);
    if (tagStart === -1) {
      out += source.slice(i);
      break;
    }
    out += source.slice(i, tagStart);

    const contentStart = tagStart + 2;
    const closeIdx = source.indexOf('}}', contentStart);
    if (closeIdx === -1) {
      return { ok: false };
    }

    const inner = source.slice(contentStart, closeIdx).trimStart();
    const sigil = inner.length === 0 ? ' ' : inner[0];
    const currentScope: Scope = stack.length === 0 ? 'ROOT' : stack[stack.length - 1].scope;

    if (sigil === '!') {
      out += source.slice(tagStart, closeIdx + 2);
    } else if (sigil === '#' || sigil === '^') {
      const name = inner.slice(1).trim();
      const valid = isValid(currentScope, name);
      if (!valid) {
        // Mirrors RenderService.markUnknownTokens's Deque<Boolean>: the frame carries whether ITS
        // own opening tag was unknown, so the matching close tag is blanked only for that reason -
        // not by recomputing validity against the scope active *inside* the section, which would
        // be the wrong scope (e.g. ITEM, once "items" has already pushed it).
        stack.push({ name, scope: currentScope, unknown: true });
        out += `[[unknown: ${name}]]`;
      } else {
        const pushedScope = SCOPE_PUSHES[name] ?? currentScope;
        stack.push({ name, scope: pushedScope, unknown: false });
        out += source.slice(tagStart, closeIdx + 2);
      }
    } else if (sigil === '/') {
      const name = inner.slice(1).trim();
      const top = stack.pop();
      if (!top || top.name !== name) {
        return { ok: false };
      }
      if (!top.unknown) {
        out += source.slice(tagStart, closeIdx + 2);
      }
      // else: this closed an unknown section, whose opening tag was already rewritten to the
      // marker text above - the closing tag itself contributes nothing, same as the backend.
    } else if (sigil === '&' || sigil === '>' || sigil === '=') {
      // Outside D-04-06's supported subset (unescaped variables, partials, custom delimiters) -
      // the backend rejects these at save time, so the live preview treats them the same way a
      // syntax error mid-typing is treated: neutral, not a crash.
      return { ok: false };
    } else {
      const name = inner.trim();
      out += isValid(currentScope, name) ? source.slice(tagStart, closeIdx + 2) : `[[unknown: ${name}]]`;
    }

    i = closeIdx + 2;
  }

  if (stack.length > 0) {
    return { ok: false };
  }
  return { ok: true, source: out };
}

// D-04-15's other half: a chrome field gets the exact same mark-unknown-tokens + Mustache.render
// steps the body uses (TMPL-08 - same placeholder rules, no second expression surface), but never
// markdown-it - a running header is one line of plain text, not a Markdown document. Unlike the
// body, resolution failure (unparseable syntax, or the release view not loaded yet) falls back to
// the raw unresolved field text rather than a dedicated "unavailable" panel per chrome field -
// there is no test or spec requirement for a per-field failure state, and the body's own
// "Preview unavailable" panel already covers the mid-typing-syntax-error case for the document
// as a whole.
function resolveChromeText(text: string, view: ReleaseView | undefined, validByScope: Map<Scope, Set<string>>): string {
  if (!view) return text;
  const marked = markUnknownTokens(text, validByScope);
  if (!marked.ok) return text;
  try {
    return Mustache.render(marked.source, toContext(view));
  } catch {
    return text;
  }
}

type PreviewOutcome =
  | { kind: 'empty' }
  | { kind: 'loading' }
  | { kind: 'unavailable' }
  | { kind: 'rendered'; html: string; truncated: boolean; itemCount: number };

function computePreview(
  body: string,
  view: ReleaseView | undefined,
  entries: PlaceholderEntry[] | undefined,
): PreviewOutcome {
  if (body.trim() === '') {
    return { kind: 'empty' };
  }
  if (!view) {
    return { kind: 'loading' };
  }

  const validByScope = buildValidTokens(entries);
  const marked = markUnknownTokens(body, validByScope);
  if (!marked.ok) {
    return { kind: 'unavailable' };
  }

  let mustacheOutput: string;
  try {
    mustacheOutput = Mustache.render(marked.source, toContext(view));
  } catch {
    return { kind: 'unavailable' };
  }

  const html = chipUnknownMarkers(md.render(mustacheOutput));
  return { kind: 'rendered', html, truncated: view.items.length > PREVIEW_ITEM_CAP, itemCount: view.items.length };
}

// The existing body container (plan 04-09), unchanged - factored out only so this plan can wrap it
// with the cover block and header/footer strips without disturbing its own four outcome states.
function renderBodyRegion(outcome: PreviewOutcome): ReactNode {
  if (outcome.kind === 'empty') {
    return <p className="p-4 text-sm italic text-gray-500">This template&apos;s body is empty.</p>;
  }

  if (outcome.kind === 'loading') {
    return (
      <div className="flex items-center gap-3 p-4">
        <span
          className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
          aria-hidden="true"
        />
        <p className="text-sm text-gray-600">Loading preview data…</p>
      </div>
    );
  }

  if (outcome.kind === 'unavailable') {
    return (
      <div className="m-4 bg-gray-50 p-4 text-sm text-gray-600">
        Preview unavailable — check your template syntax (e.g., every {'{{#x}}'} needs a matching {'{{/x}}'}).
      </div>
    );
  }

  return (
    <>
      {outcome.truncated && (
        <p className="border border-blue-200 bg-blue-50 px-3 py-2 text-xs text-blue-900">
          Preview truncated — this release has {outcome.itemCount} items; showing the first {PREVIEW_ITEM_CAP}.
          Generated documents are bounded separately when produced.
        </p>
      )}
      <div className="prose prose-gray max-w-none p-4" dangerouslySetInnerHTML={{ __html: outcome.html }} />
    </>
  );
}

export function TemplatePreview(props: {
  body: string;
  chrome: ChromeFields;
  view: ReleaseView | undefined;
  entries: PlaceholderEntry[] | undefined;
  logoUrl: string | null;
}) {
  const outcome = useMemo(
    () => computePreview(props.body, props.view, props.entries),
    [props.body, props.view, props.entries],
  );

  const chrome = props.chrome;
  const validByScope = useMemo(() => buildValidTokens(props.entries), [props.entries]);

  const resolvedChrome: ChromeFields = {
    ...chrome,
    coverTitle: resolveChromeText(chrome.coverTitle, props.view, validByScope),
    coverSubtitle: resolveChromeText(chrome.coverSubtitle, props.view, validByScope),
    headerText: resolveChromeText(chrome.headerText, props.view, validByScope),
    footerText: resolveChromeText(chrome.footerText, props.view, validByScope),
  };

  return (
    <RenderedDocument chrome={resolvedChrome} logoUrl={props.logoUrl}>
      {renderBodyRegion(outcome)}
    </RenderedDocument>
  );
}

// D-04-10, recorded deliberately: this renderer and the server's jmustache+flexmark pipeline are two
// independent implementations that can diverge. No golden-fixture parity test and no server verify
// action is built. The mitigation is library choice alone - a spec-conformant Mustache and a
// CommonMark-conformant Markdown renderer, configured to match D-04-05 and D-04-08. If drift ever
// bites, the additive fix is a shared fixture set asserted by both `mvn test` and `vitest`.
