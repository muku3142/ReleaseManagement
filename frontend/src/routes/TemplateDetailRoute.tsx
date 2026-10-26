import { useEffect, useRef, useState } from 'react';
import { Link, useBlocker, useParams } from '@tanstack/react-router';
import { ApiError } from '../api/client';
import { messageForReason } from '../api/reasonMessages';
import {
  DOCUMENT_TYPE_LABELS,
  GROUPING_DIMENSIONS,
  GROUPING_DIMENSION_LABELS,
  useActivateTemplate,
  usePlaceholderCatalog,
  useRenameTemplate,
  useRenderContext,
  useSaveTemplateContent,
  useTemplate,
  type ChromeFields,
  type GroupingDimension,
} from '../api/templates';
import { useReleases } from '../api/releases';
import { ConfirmDialog } from '../components/ConfirmDialog';
import { TemplatePreview } from '../components/TemplatePreview';
import { ChromeFieldsForm, type ChromeFieldName } from '../components/ChromeFieldsForm';
import { LogoUpload } from '../components/LogoUpload';
import { PlaceholderDrawer } from '../components/PlaceholderDrawer';

// ChromeFieldsForm (plan 04-10) renders its four text inputs in this fixed order and is not
// modified by this plan - insertion locates the right chrome input by position within the
// editor pane rather than by a ref forwarded through that component.
const CHROME_FIELD_ORDER: ChromeFieldName[] = ['coverTitle', 'coverSubtitle', 'headerText', 'footerText'];

export function TemplateDetailRoute() {
  const { templateId } = useParams({ from: '/authenticated/templates/$templateId' });
  const template = useTemplate(templateId);
  const releases = useReleases('');
  const catalog = usePlaceholderCatalog();

  const [body, setBody] = useState('');
  const [chrome, setChrome] = useState<ChromeFields | null>(null);
  const [groupingDimension, setGroupingDimension] = useState<GroupingDimension>('ISSUE_TYPE');
  const [saved, setSaved] = useState<
    { body: string; chrome: ChromeFields; groupingDimension: GroupingDimension } | null
  >(null);
  const [tab, setTab] = useState<'body' | 'chrome'>('body');

  const [renaming, setRenaming] = useState(false);
  const [renameValue, setRenameValue] = useState('');

  const [previewReleaseId, setPreviewReleaseId] = useState<string | undefined>(undefined);
  const renderContext = useRenderContext(previewReleaseId);

  const [saveOutcome, setSaveOutcome] = useState<'clean' | 'warnings' | null>(null);
  const [warnings, setWarnings] = useState<string[]>([]);
  const [showWarningDetails, setShowWarningDetails] = useState(false);

  const bodyRef = useRef<HTMLTextAreaElement>(null);
  // Remembers the last cursor position of whichever field - body or a chrome input - was last
  // focused, so plan 04-11's placeholder drawer can insert into it without a change here.
  const selectionRef = useRef<{ field: 'body' | ChromeFieldName; start: number; end: number } | null>(null);
  // Holds both tabs' content so a chrome field can be located by DOM position even while the
  // Body tab is active (the field itself isn't mounted then, but focus can only have landed on
  // it while it was, so this ref is only read once we already know which field was targeted).
  const editorPaneRef = useRef<HTMLDivElement>(null);
  const [drawerOpen, setDrawerOpen] = useState(false);

  const rename = useRenameTemplate();
  const save = useSaveTemplateContent();
  const activate = useActivateTemplate();

  const dirty =
    saved !== null &&
    (body !== saved.body ||
      JSON.stringify(chrome) !== JSON.stringify(saved.chrome) ||
      groupingDimension !== saved.groupingDimension);
  // A stale outcome banner never sits above edits it does not describe: becoming dirty again
  // hides it, without needing a second effect racing the seed-from-server effect below.
  const showOutcome = saveOutcome !== null && !dirty;

  // Switching to a different template resets ephemeral UI state. Keyed on the route param
  // (always available immediately) rather than the loaded template, so it fires exactly once
  // per template id, not on every subsequent save of the same template.
  useEffect(() => {
    setTab('body');
    setSaveOutcome(null);
    setWarnings([]);
    setShowWarningDetails(false);
  }, [templateId]);

  // Seeds the draft from the server. Keyed on id *and* updatedAt so a successful save (which
  // changes updatedAt) re-seeds `saved` to match what was just persisted, without touching the
  // outcome banner state above.
  useEffect(() => {
    if (!template.data) return;
    setBody(template.data.body);
    setChrome(template.data.chrome);
    setGroupingDimension(template.data.groupingDimension);
    setSaved({
      body: template.data.body,
      chrome: template.data.chrome,
      groupingDimension: template.data.groupingDimension,
    });
  }, [template.data?.id, template.data?.updatedAt]);

  useEffect(() => {
    if (previewReleaseId !== undefined) return;
    if (releases.data && releases.data.releases.length > 0) {
      setPreviewReleaseId(releases.data.releases[0].id);
    }
  }, [releases.data, previewReleaseId]);

  const blocker = useBlocker({ shouldBlockFn: () => dirty, withResolver: true });

  useEffect(() => {
    if (!dirty) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [dirty]);

  const problem = template.isError && template.error instanceof ApiError ? template.error.problem : undefined;

  const actionProblem =
    (rename.isError && rename.error instanceof ApiError && rename.error.problem) ||
    (activate.isError && activate.error instanceof ApiError && activate.error.problem) ||
    undefined;

  const saveProblem = save.isError && save.error instanceof ApiError ? save.error.problem : undefined;

  if (template.isPending) {
    return (
      <div className="flex items-center gap-3">
        <span
          className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600"
          aria-hidden="true"
        />
        <p className="text-sm text-gray-600">Loading template…</p>
      </div>
    );
  }

  if (problem && problem.reason === 'TEMPLATE_NOT_FOUND') {
    return (
      <div>
        <p role="alert" data-reason="TEMPLATE_NOT_FOUND" className="text-sm text-red-600">
          This template doesn&apos;t exist or has been deleted.
        </p>
        <Link to="/templates" className="mt-2 inline-block text-sm text-blue-600 underline">
          Back to templates
        </Link>
      </div>
    );
  }

  if (problem) {
    return (
      <div>
        <p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
          {messageForReason(problem)}
        </p>
        <button
          type="button"
          onClick={() => template.refetch()}
          className="mt-2 rounded border border-gray-300 px-3 py-1.5 text-sm font-semibold text-gray-700"
        >
          Try again
        </button>
      </div>
    );
  }

  const data = template.data;
  if (!data) return null;

  const releaseOptions = releases.data?.releases ?? [];
  const currentName = data.name;

  function startRename() {
    setRenameValue(currentName);
    setRenaming(true);
  }

  function commitRename() {
    const value = renameValue.trim();
    if (value === '' || value === currentName) {
      setRenaming(false);
      return;
    }
    rename.mutate({ templateId, name: value }, { onSuccess: () => setRenaming(false) });
  }

  function rememberSelection(e: React.SyntheticEvent<HTMLTextAreaElement>) {
    selectionRef.current = {
      field: 'body',
      start: e.currentTarget.selectionStart,
      end: e.currentTarget.selectionEnd,
    };
  }

  function rememberChromeSelection(field: ChromeFieldName, start: number, end: number) {
    selectionRef.current = { field, start, end };
  }

  function chromeFieldElement(field: ChromeFieldName): HTMLInputElement | null {
    const inputs = editorPaneRef.current?.querySelectorAll<HTMLInputElement>('input[type="text"]');
    if (!inputs) return null;
    return inputs[CHROME_FIELD_ORDER.indexOf(field)] ?? null;
  }

  // Refocuses whichever field the token was inserted into and places the caret right after it.
  // The rAF delay lets React commit the new value first - setSelectionRange on a controlled
  // input/textarea before its value prop updates would place the caret against stale content.
  function focusAfterInsert(field: 'body' | ChromeFieldName, caret: number) {
    selectionRef.current = { field, start: caret, end: caret };
    requestAnimationFrame(() => {
      const el = field === 'body' ? bodyRef.current : chromeFieldElement(field);
      el?.focus();
      el?.setSelectionRange(caret, caret);
    });
  }

  function insertToken(token: string) {
    const text = `{{${token}}}`;
    const target = selectionRef.current;

    if (!target) {
      // Nothing has ever been focused: append to the body and switch to it rather than a dead
      // click on a field the author never interacted with.
      setTab('body');
      const next = body + text;
      setBody(next);
      focusAfterInsert('body', next.length);
      return;
    }

    if (target.field === 'body') {
      setBody(body.slice(0, target.start) + text + body.slice(target.end));
    } else if (chrome) {
      const current = chrome[target.field];
      const next = current.slice(0, target.start) + text + current.slice(target.end);
      setChrome({ ...chrome, [target.field]: next });
    }
    focusAfterInsert(target.field, target.start + text.length);
  }

  function handleSave() {
    if (!chrome) return;
    save.mutate(
      { templateId, body, chrome, groupingDimension },
      {
        onSuccess: (t) => {
          setSaved({ body: t.body, chrome: t.chrome, groupingDimension: t.groupingDimension });
          setSaveOutcome(t.warnings.length === 0 ? 'clean' : 'warnings');
          setWarnings(t.warnings);
        },
      },
    );
  }

  return (
    <div className="w-full">
      <div className="bg-white p-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          {renaming ? (
            <input
              type="text"
              aria-label="Template name"
              autoFocus
              value={renameValue}
              onChange={(e) => setRenameValue(e.target.value)}
              onBlur={commitRename}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  e.currentTarget.blur();
                } else if (e.key === 'Escape') {
                  setRenaming(false);
                }
              }}
              className="rounded border border-gray-300 px-3 py-1.5 text-sm focus-visible:ring-2 focus-visible:ring-blue-600"
            />
          ) : (
            <>
              <h2 className="text-base font-semibold text-gray-900">{data.name}</h2>
              <button type="button" onClick={startRename} className="text-sm text-blue-600 underline">
                Rename
              </button>
            </>
          )}
          <span className="text-sm text-gray-600">{DOCUMENT_TYPE_LABELS[data.documentType]} template</span>
          <label htmlFor="groupBy" className="text-sm text-gray-600">
            Group by
          </label>
          <select
            id="groupBy"
            aria-label="Group by"
            value={groupingDimension}
            onChange={(e) => setGroupingDimension(e.target.value as GroupingDimension)}
            className="rounded border border-gray-300 px-2 py-1 text-sm focus-visible:ring-2 focus-visible:ring-blue-600"
          >
            {GROUPING_DIMENSIONS.map((value) => (
              <option key={value} value={value}>
                {GROUPING_DIMENSION_LABELS[value]}
              </option>
            ))}
          </select>
          <span className="text-xs text-gray-500">
            Determines what {'{{groupName}}'} and {'{{#groups}}'} resolve to when this template renders.
          </span>
          {data.active ? (
            <span className="rounded-full bg-gray-800 px-2 py-0.5 text-sm text-white">Active</span>
          ) : (
            <button
              type="button"
              onClick={() => activate.mutate(templateId)}
              className="text-sm text-gray-600 hover:text-gray-900"
            >
              Make active
            </button>
          )}
        </div>

        <div className="flex items-center gap-3">
          <button type="button" data-placeholder-toggle onClick={() => setDrawerOpen((v) => !v)}
                  aria-expanded={drawerOpen}
                  className={drawerOpen
                    ? 'rounded border border-blue-300 bg-blue-50 px-3 py-1.5 text-sm text-blue-700'
                    : 'rounded border border-gray-300 px-3 py-1.5 text-sm text-gray-700'}>
            Placeholders
          </button>
          {dirty && <span className="text-sm text-gray-600">Unsaved changes</span>}
          <button
            type="button"
            onClick={handleSave}
            disabled={save.isPending}
            className="rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50"
          >
            {save.isPending ? 'Saving…' : 'Save template'}
          </button>
        </div>
      </div>

      {actionProblem && (
        <p role="alert" data-reason={actionProblem.reason} className="mx-6 mt-2 text-sm text-red-600">
          {messageForReason(actionProblem)}
        </p>
      )}

      {showOutcome && saveOutcome === 'clean' && (
        <p
          role="status"
          className="mx-6 mt-4 rounded border border-blue-200 bg-blue-50 px-3 py-2 text-sm text-blue-900"
        >
          Template saved.
        </p>
      )}

      {showOutcome && saveOutcome === 'warnings' && (
        <div
          role="status"
          className="mx-6 mt-4 rounded border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-900"
        >
          <p>
            Template saved with {warnings.length} unknown placeholder warning{warnings.length === 1 ? '' : 's'}.
          </p>
          <button
            type="button"
            onClick={() => setShowWarningDetails((v) => !v)}
            className="mt-1 text-sm underline"
          >
            Show details
          </button>
          {showWarningDetails && (
            <ul className="mt-2 list-disc pl-5 font-mono text-xs">
              {warnings.map((w) => (
                <li key={w}>{w}</li>
              ))}
            </ul>
          )}
        </div>
      )}

      {saveProblem?.reason === 'TEMPLATE_MALFORMED' && (
        <p role="alert" data-reason="TEMPLATE_MALFORMED" className="mx-6 mt-4 text-sm text-red-600">
          Template not saved — {saveProblem.detail}
        </p>
      )}
      {saveProblem?.reason === 'RENDER_BOUND_EXCEEDED' && (
        <p role="alert" data-reason="RENDER_BOUND_EXCEEDED" className="mx-6 mt-4 text-sm text-red-600">
          Template not saved — validation took too long or produced too much content to render safely. Simplify the template (fewer nested sections or shorter loops) and try again.
        </p>
      )}
      {saveProblem && saveProblem.reason !== 'TEMPLATE_MALFORMED' && saveProblem.reason !== 'RENDER_BOUND_EXCEEDED' && (
        <p role="alert" data-reason={saveProblem.reason} className="mx-6 mt-4 text-sm text-red-600">
          {messageForReason(saveProblem)}
        </p>
      )}

      <div className="flex gap-4 border-b border-gray-200 bg-white px-6">
        <button
          type="button"
          onClick={() => setTab('body')}
          className={
            tab === 'body'
              ? 'border-b-2 border-blue-600 py-2 text-sm font-semibold text-gray-900'
              : 'py-2 text-sm text-gray-600'
          }
        >
          Body
        </button>
        <button
          type="button"
          onClick={() => setTab('chrome')}
          className={
            tab === 'chrome'
              ? 'border-b-2 border-blue-600 py-2 text-sm font-semibold text-gray-900'
              : 'py-2 text-sm text-gray-600'
          }
        >
          Chrome &amp; Logo
        </button>
      </div>

      <div className="relative flex h-[calc(100vh-260px)] min-h-[420px] flex-col lg:flex-row">
        <div ref={editorPaneRef} className="overflow-y-auto lg:basis-[45%] border-r border-gray-200 bg-white">
          {tab === 'body' ? (
            <>
              {/* D-04-19: a plain styled textarea, no editor library. A code-editor component and a
              rich-text editor component were both considered and rejected - the live preview is what
              shows the author what they are getting, and syntax highlighting is not what makes a
              template correct. */}
              <textarea
                ref={bodyRef}
                value={body}
                onChange={(e) => setBody(e.target.value)}
                onBlur={rememberSelection}
                onSelect={rememberSelection}
                placeholder="Write Markdown + {{ }} placeholders…"
                className="h-full w-full resize-none border-0 p-4 font-mono text-sm leading-relaxed focus:outline-none"
                aria-label="Template body"
              />
            </>
          ) : (
            chrome && (
              <ChromeFieldsForm chrome={chrome} onChange={setChrome} onFieldFocus={rememberChromeSelection}>
                <LogoUpload templateId={templateId} logo={data.logo} limits={catalog.data?.logoLimits} />
              </ChromeFieldsForm>
            )
          )}
        </div>

        <div className="overflow-y-auto lg:basis-[55%] bg-white">
          <p className="border-b border-gray-200 bg-gray-100 px-3 py-1.5 text-xs text-gray-600">
            Preview approximation — actual page breaks, headers, and footers are determined when the document is generated.
          </p>

          <div className="flex items-center gap-2 border-b border-gray-200 px-3 py-2">
            <label htmlFor="preview-release" className="text-sm text-gray-600">
              Preview against:
            </label>
            <select
              id="preview-release"
              aria-label="Preview against"
              value={previewReleaseId ?? ''}
              onChange={(e) => setPreviewReleaseId(e.target.value)}
              disabled={releaseOptions.length === 0}
              className="rounded border border-gray-300 px-2 py-1 text-sm"
            >
              {releaseOptions.length === 0 ? (
                <option value="">No releases available</option>
              ) : (
                releaseOptions.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.name}
                  </option>
                ))
              )}
            </select>
          </div>

          {releaseOptions.length === 0 ? (
            <div className="m-4 rounded bg-gray-50 p-6 text-center">
              <p className="text-sm text-gray-600">
                Create a release first to preview this template against real data.
              </p>
              <Link to="/releases" className="mt-2 inline-block text-sm text-blue-600 underline">
                Go to releases
              </Link>
            </div>
          ) : (
            <div data-testid="preview-content">
              {chrome && (
                <TemplatePreview
                  body={body}
                  chrome={chrome}
                  view={renderContext.data}
                  entries={catalog.data?.entries}
                  logoUrl={data.logo ? `/api/templates/${templateId}/logo?v=${data.logo.uploadedAt}` : null}
                />
              )}
            </div>
          )}
        </div>

        {drawerOpen && (
          <PlaceholderDrawer
            open={drawerOpen}
            entries={catalog.data?.entries}
            onInsert={insertToken}
            onClose={() => setDrawerOpen(false)}
          />
        )}
      </div>

      <ConfirmDialog
        open={blocker.status === 'blocked'}
        title="Discard unsaved changes?"
        body={`You have unsaved edits to '${data.name}'. Leaving now will discard them.`}
        confirmLabel="Discard changes"
        confirmClassName="rounded border border-red-600 px-4 py-2 text-sm font-semibold text-red-600 disabled:opacity-50"
        pending={false}
        onConfirm={() => blocker.proceed?.()}
        onCancel={() => blocker.reset?.()}
      />
    </div>
  );
}
