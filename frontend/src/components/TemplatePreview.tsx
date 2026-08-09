import { useMemo } from 'react';
import Mustache from 'mustache';
import MarkdownIt from 'markdown-it';
import type { ItemView, PlaceholderEntry, ReleaseView } from '../api/templates';

// D-04-05 on the client: html:false makes markdown-it escape raw HTML rather than pass it through,
// which is the same posture the backend takes with flexmark. UI-SPEC treats an inability to suppress
// raw HTML as a blocking library-selection defect, so this option is not negotiable.
const md = new MarkdownIt({ html: false, linkify: false, typographer: false });

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

// markdown-it has already escaped <, >, & and " in every text node, so the captured token is safe to
// interpolate. This is the one place this app builds HTML by hand, and it is confined to replacing a
// literal marker the renderer itself produced.
function chipUnknownMarkers(html: string): string {
  return html.replace(
    /\[\[unknown: ([^\]]*)\]\]/g,
    (_m, token: string) =>
      `<code class="rounded bg-amber-100 px-1 py-0.5 font-mono text-xs text-amber-900">[[unknown: ${token}]]</code>`,
  );
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

export function TemplatePreview(props: {
  body: string;
  view: ReleaseView | undefined;
  entries: PlaceholderEntry[] | undefined;
}) {
  const outcome = useMemo(
    () => computePreview(props.body, props.view, props.entries),
    [props.body, props.view, props.entries],
  );

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

// D-04-10, recorded deliberately: this renderer and the server's jmustache+flexmark pipeline are two
// independent implementations that can diverge. No golden-fixture parity test and no server verify
// action is built. The mitigation is library choice alone - a spec-conformant Mustache and a
// CommonMark-conformant Markdown renderer, configured to match D-04-05 and D-04-08. If drift ever
// bites, the additive fix is a shared fixture set asserted by both `mvn test` and `vitest`.
