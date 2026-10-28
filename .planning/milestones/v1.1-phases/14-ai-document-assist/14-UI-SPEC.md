---
phase: 14
slug: ai-document-assist
status: approved
reviewed_at: 2026-10-16
shadcn_initialized: false
preset: none
created: 2026-10-15
---

# Phase 14 — UI Design Contract: AI Document Assist

> Visual and interaction contract for ASST-09 and ASST-10 (Clean up, Summarize and Custom on a generated document; side-by-side compare; edit; save as an AI-assisted revision). Extends the Phase 5 document viewer and the Phase 13 assistant contract (`13-UI-SPEC.md`). No new component library, no editor, no new icon library, no new type size, no new colour family. New elements: (1) an AI assist bar in the viewer, (2) a side-by-side result card with Edit/Preview, (3) a fact-check warning above Save, (4) save-block and verify banners, (5) AI-assisted badge, instruction line and viewer banner, (6) a regenerate notice.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none. No `components.json`. Not reopened (decided in 13-UI-SPEC). |
| Preset | not applicable |
| Component library | none. Native `<button>`, `<select>`, `<textarea>`, `<details>`/`<summary>`. |
| Icon library | none. Plain glyphs only (`▸`/`▾`, `…`, `·`). Pending state is the existing spinner `h-4 w-4 animate-spin motion-reduce:animate-none rounded-full border-2 border-gray-300 border-t-blue-600` (the `Spinner` from `ExportButtons`). |
| Font | system default (Tailwind `sans`) for UI; `font-mono` for the raw Markdown textarea and streamed raw text only. |

Stack facts: React, Vite, TypeScript, TanStack Router and Query, Tailwind v4 (`@import "tailwindcss"` plus the typography plugin). Everything is written in Tailwind utilities. No editor dependency (D-14-05): Markdown is edited in a native `<textarea>` and previewed with the existing `markdown-it` instance (`md` from `RenderedDocument.tsx`, `html: false`). The stream uses `fetch` + `ReadableStream` + `AbortController` over the cookie session (`credentials: 'same-origin'`), the same shape as Phase 13. No rag-service key exists in the browser. Edit buffer, stream state and recent instructions live in memory only (D-13-12, D-14-09): no `localStorage`, no `sessionStorage`.

---

## Spacing Scale

Reuses the Phase 2-8 / 13 scale. No new tokens.

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4px | Chip gaps (`gap-1`), glyph gaps |
| sm | 8px | Button gaps (`gap-2`), banner-to-banner gap (`mt-2`), label-to-control gaps |
| md | 16px | Card padding (`p-4`), grid gap between the two columns (`gap-4`), banner padding |
| lg | 24px | Viewer horizontal margin (`mx-6`, `m-6`, existing) |
| xl | 32px | Not used |
| 2xl | 48px | Not used |
| 3xl | 64px | Not used |

Exceptions (inherited classes, matched exactly as in 05/08/13-UI-SPEC, not new tokens): `px-3 py-1.5` bordered controls (assist buttons, Copy, Download, Discard, Stop, Edit/Preview toggle, select); `px-4 py-2` for the filled primary buttons (Save, Run instruction); `px-2 py-0.5` for the AI-assisted badge and key chips (same as ReadinessPill and 13 citation chips); `p-3` for the gray informational banners (same as the existing superseded banner at `DocumentViewerRoute.tsx:166`).

Fixed dimensions: two-column grid from `lg` up (`grid grid-cols-1 gap-4 lg:grid-cols-2`), single column below `lg` (original first, result below). Result text region `min-h-96 max-h-[70vh] overflow-y-auto` (96 = 384px, on the 4px grid). Custom instruction textarea `rows={3}`, `maxLength={2000}` (matches the server cap in AI-SPEC §4b).

---

## Typography

Reuses the Phase 5 / 13 scale verbatim. No new size, no new weight (400 and 600 only).

| Role | Size | Weight | Line Height |
|------|------|--------|-------------|
| Body | 14px (`text-sm`) | 400 | 1.5 |
| Label | 14px (`text-sm`) | 600 | 1.5 |
| Heading | 16px (`text-base`) | 600 | 1.2 |
| Small (column labels, notes, chips, counters, helper lines) | 12px (`text-xs`) | 400, 600 inside chips and badges | 1.5 |

Usage:
- Result card title ("Cleaned-up version", "Summary", "Custom result"): Heading 16/600.
- "AI assist" label in the bar, banner titles ("Verify steps before use.", "This result is incomplete."): Label 14/600.
- Banner bodies, raw streamed text, textarea text: Body 14/400. Streamed raw Markdown and the edit textarea use `font-mono` at 14px (`text-sm`), `whitespace-pre-wrap break-words`.
- Column labels ("Original · Revision 3", "AI result"), helper lines, counters, the "Edited" tag, key chips, badge: Small 12.
- Preview renders through the existing `prose prose-gray max-w-none p-4` wrapper, identical to the viewer body.

---

## Color

No new colour. Existing palette only.

| Role | Value | Usage |
|------|-------|-------|
| Dominant (60%) | `gray-50` page canvas, `white` document and result cards | Viewer surface (existing) |
| Secondary (30%) | `white` cards with `border-gray-200`; `gray-100` informational banners and the AI-assisted badge | Result card, assist bar, AI-assisted banner |
| Accent (10%) | `blue-600` | See reserved-for list |
| Destructive | `red-600` / `red-700` text, `red-50` bg | FAILED and MODEL_MISSING banners, save-failed alert, and the "Discard edits" confirm button only |
| Warning | `amber-700` text, `amber-50` bg, `amber-600` border | Verify banner, fact-check warning, incomplete-result and DEADLINE/OFFLINE/BUSY banners, too-long message, regenerate notice |
| Muted | `gray-600` text on `gray-100`, `border-gray-200` | AI-assisted banner, stopped marker, streaming status line |

Accent (`blue-600`) reserved for:
1. **Accept and save as revision N+1** (`bg-blue-600 text-white`), the one filled button in the result card.
2. **Run instruction** (`bg-blue-600 text-white`) in the Custom box. Save and Run never coexist: the assist buttons (and so the Custom box) are disabled while a result card is open.
3. The pending spinner's leading edge (`border-t-blue-600`).
4. `focus-visible:ring-2 focus-visible:ring-blue-600` on every new control.
5. Existing inherited links ("View latest", "Back to release"). Nothing new.

Clean up, Summarize, Custom…, Stop, Copy, Download, Discard, Edit/Preview toggle and "Try again" are gray-bordered (`border border-gray-300 text-gray-700`), not accent. The pressed toggle segment is `bg-gray-100 font-semibold text-gray-900`. The AI-assisted badge is gray (`border border-gray-300 bg-gray-100 text-gray-700`), deliberately not accent: it is a fact marker, not an action.

Colour is never the only signal: every banner has a bold text title, the badge says "AI-assisted", the stopped marker is text.

---

## Screen Layout: Document Viewer (`DocumentViewerRoute`)

Existing order is: header row, templateDeleted note, superseded banner, document card, parity hint. Phase 14 inserts, in this order:

```
header row (existing)
templateDeleted note (existing)
superseded banner (existing)
[A] AI-assisted banner + verify banner   (only when the viewed revision is AI-assisted)
[B] AI assist bar                        (only on the latest revision, only when assistant enabled)
[C] Custom instruction box               (only when Custom is open)
[D] too-long / pre-stream error message  (only when it applies)
[E] content area:  idle  = existing single document card
                   active = two-column grid: Original | AI result card
parity hint (existing)
```

### [B] AI assist bar

`mx-6 mt-2 rounded border border-gray-200 bg-white p-4`, one flex row that wraps (`flex flex-wrap items-center gap-2`):

1. Label **"AI assist"** (Label 14/600).
2. Three gray-bordered buttons, in this DOM order: **"Clean up"**, **"Summarize"**, **"Custom…"**. "Custom…" carries `aria-expanded` and `aria-controls="ai-custom-box"`. Clicking Clean up or Summarize starts the run immediately (no extra confirm: nothing is stored and the original is never changed).
3. Helper line below the row (`mt-2`, Small `text-gray-600`): **"Runs on this server's local model. Only this document's text is sent. Results can be saved as a new revision; the original is never changed."**

Visibility and enable rules:
- Rendered only when the Phase 13 assistant status query reports `enabled: true`. While loading or on failure it is not rendered (same safe default as the nav item).
- Rendered only when the viewed revision is the latest (`data.revision === data.latestRevision`). On a superseded revision the bar is absent and the existing "View latest" link is the path (assumption 1).
- All three buttons are `disabled` while a result card is open (any state except closed), with `title="Save or discard the current result first."`.
- All three are `disabled` when the document is over the size limit (see [D]).
- The Revision `<select>` in the header is `disabled` while a result card is open (`title="Save or discard the AI result first."`) so a revision switch cannot silently drop a result.

### [C] Custom instruction box

`id="ai-custom-box"`, `mx-6 mt-2 rounded border border-gray-200 bg-white p-4`, toggled by "Custom…" (a second click closes it; closing keeps the typed text in memory until reload or run).

- `<label>` **"Custom instruction"** (Label), then `<textarea rows={3} maxLength={2000} placeholder="e.g. Rewrite for executives, keep every ticket key and version">`. Counter shown only above 1800 characters, Small amber `"{n}/2000"`.
- Recent instructions: native `<select aria-label="Recent instructions">` placed above the textarea, rendered only when at least one instruction has been run this session. First option `"Recent instructions…"` (selected, `disabled`). Then up to **5** entries, most recent first, de-duplicated by exact text, each option text truncated to 80 characters with `…` (full text in `title`). Choosing an entry replaces the textarea content and moves focus to the textarea; it does not run (D-14-09). The list is held in a module-level store (survives in-app navigation, cleared on reload or logout), consistent with 13 chat state.
- **"Run instruction"** (blue-600 filled), disabled for empty or whitespace-only text and while over the size limit. Enter inserts a line break; Ctrl/Cmd+Enter runs.
- Small helper under the textarea (`text-gray-600`): **"The model sees only this document and your instruction. The instruction is saved with the revision if you save the result."**
- On run: the instruction is added to the recent list, the box closes, and the result card opens.

### [D] Too-long message and pre-stream errors (D-14-10)

When the limits lookup says the document plus the shortest instruction exceeds the limit, the three assist buttons are `disabled` and this message sits directly under the bar (`mx-6 mt-2 rounded border border-amber-600 bg-amber-50 p-4 text-sm text-amber-700`, `role="status"`):

- Title (Label): **"This document is too long for the AI model on this server (about {N} of {M} characters)."**
- Body (Small): **"Nothing was sent. A larger model on the server raises this limit."**

`{N}` is the estimated document characters, `{M}` the limit, both formatted with `toLocaleString()`. For Custom, the Run button is disabled with the same message when document plus the typed instruction exceeds the limit (`M` recomputed for that instruction length). A 413 from the server (client estimate was too low) shows the identical message in the same slot using the `N` and `M` from the problem body, and no result card opens. If the limits lookup fails, the buttons stay enabled and the server 413 is the authority (assumption 5).

### [E] Side-by-side content area (D-14-04)

Idle: the existing `m-6` document card, unchanged.

Active (result card open): replace the single card with `mx-6 mt-2 mb-6 grid grid-cols-1 gap-4 lg:grid-cols-2`:

- **Left column, Original.** Small `text-gray-500 font-semibold` label **"Original · Revision {n}"** above the existing `RenderedDocument` card (`rounded border border-gray-200 bg-white`), exactly as the idle card. Unchanged and read-only. Below `lg` it stacks first.
- **Right column, AI result card.** Described below. Below `lg` it stacks under the original; on run start the card scrolls into view (`scrollIntoView({ block: 'start' })`, no smooth behaviour).

### AI result card

`rounded border border-gray-200 bg-white`, sections top to bottom:

1. **Header** (`flex items-center gap-2 border-b border-gray-200 p-4`): title (Heading), a Small gray **"Edited"** tag when the buffer differs from the model output, `grow` spacer, then the view toggle.
2. **View toggle**: `role="group" aria-label="Result view"` with two buttons using `aria-pressed`: **"Edit"** and **"Preview"**. For read-only results (incomplete results) the first label is **"Source"** instead of "Edit". The toggle is `disabled` while streaming and while no text exists.
3. **Verify banner** (runbook and approval documents only, for every mode including Summarize and Custom): `border-b border-amber-600 bg-amber-50 p-4`, title (Label, `text-amber-700`) **"Verify steps before use."** body (Body `text-amber-700`) **"AI-assisted text can change commands, order, values or risk wording. Compare every step with the original before you rely on it."** Shown from the moment the card opens, above the result. No role (static).
4. **Body region** (`min-h-96 max-h-[70vh] overflow-y-auto`):
   - **Waiting** (no token yet): spinner + **"Waiting for the model…"** (Small `text-gray-600`).
   - **Streaming**: raw Markdown appended to one text node in a `<div class="whitespace-pre-wrap break-words p-4 font-mono text-sm">`. No caret animation. Above the text, a status line: spinner + **"Writing… {n} characters so far"** (Small, updated at most once per second, not announced) and below it **"Long documents can take a few minutes on this server. You can stop at any time."** (Small `text-gray-600`).
   - **Edit** (complete Clean up/Custom result): `<textarea aria-label="Edited result (Markdown)" spellCheck={false} class="block min-h-96 w-full resize-y p-4 font-mono text-sm focus-visible:ring-2 focus-visible:ring-blue-600">`. The buffer is plain Markdown (never HTML).
   - **Source** (read-only): the same text in a `<div tabIndex={-1}>` (not a textarea), selectable.
   - **Preview**: `renderBody(buffer)` (the existing function, `prose prose-gray max-w-none p-4`). See Preview safety below. No cover, header or footer chrome is drawn: only the body.
5. **Messages above the actions** (in this order, each only when it applies): stopped marker, incomplete/blocked message, fact-check warning, copy/save error.
6. **Action row** (`flex flex-wrap items-center gap-2 border-t border-gray-200 p-4`) and the save helper line.

### Preview safety (security-critical)

Preview uses the existing `md` instance (`html: false`, `linkify: false`) and `chipUnknownMarkers`. In addition, **remote images must never load** from model output (a planted instruction in a document, golden T03, could otherwise exfiltrate data through an image URL). In Preview, an image token renders as the plain text `[image: {alt}]`; no `<img>` element is created. Links render as plain anchors with `rel="noopener noreferrer" target="_blank"`. The saved revision viewer is unchanged (it renders with the existing renderer; AI text is saved as Markdown, never HTML, D-05-05, and any image rule for the saved viewer is the planner's decision, see assumption 9).

---

## Result states and action rows

The result card is always in exactly one of these states. The action row differs per state.

| State | Body | Action row (DOM and Tab order) |
|-------|------|-------------------------------|
| WAITING / STREAMING | Waiting line or raw stream | **Stop** only |
| COMPLETE, Clean up or Custom | Edit (default) or Preview | **Accept and save as revision N+1**, Copy, Download, Discard |
| COMPLETE, Summarize | Same as COMPLETE, Clean up: Edit (default) or Preview | Save, Copy, Download, Discard (D-14-13) |
| INCOMPLETE (length, truncated, DEADLINE, FAILED with text, stopped, stream ended without `done`) | Source (read-only) or Preview | Save (`aria-disabled="true"`, never activates), Copy, Discard. Download is `disabled`. |
| ERROR WITHOUT TEXT (OFFLINE, BUSY, MODEL_MISSING, FAILED, nothing streamed) | Banner replaces the body | Try again (except MODEL_MISSING), **Close** |
| SAVING | As COMPLETE; all controls disabled, textarea read-only | Save shows spinner + **"Saving…"** |

"Complete" means `done` with `finishReason = stop` and `truncated = false` (AI-SPEC §6). Anything else is INCOMPLETE.

### Streaming and Stop

- Run starts: the assist buttons disable, the card opens, and **focus moves to Stop** (the control the user pressed becomes disabled, so focus must land somewhere useful).
- **Stop**: gray-bordered, label **"Stop"**, `aria-label="Stop generating"`. Click or **Escape** (while focus is anywhere inside the result card or Custom box) aborts the fetch, which frees the server's single in-flight slot. The partial text stays. The card becomes INCOMPLETE with the marker **"Stopped. This result is incomplete."** (Small `text-gray-500`) and the blocked message **"A stopped result can't be saved."** Focus moves to the Source region (`tabIndex={-1}`).
- Leaving the viewer route or closing the card while streaming aborts the stream the same way.
- Escape never discards a completed result and never closes the card; it only stops a running stream or cancels the discard confirmation.

### Save, blocked states and the fact-check warning

**Accept and save as revision N+1** (blue-600 filled, `px-4 py-2 text-sm font-semibold`), first in the action row. Helper line under the row (Small `text-gray-600`, `mt-2`, linked by `aria-describedby`): **"Saving adds Revision {latest+1} as the current revision, so exports and the assistant will use it. Revision {latest} stays in the history unchanged."**

Save uses `aria-disabled="true"` (not the `disabled` attribute) in INCOMPLETE and empty-buffer states so it stays in the Tab order and screen readers hear why: `aria-describedby` points at the blocked message. Its click handler does nothing. Style: `opacity-50 cursor-not-allowed`.

Blocked messages (Label title + Body, `role="alert"` on first render, amber banner `rounded border border-amber-600 bg-amber-50 p-4 text-sm text-amber-700`, shown directly above the action row):

| Cause | Title | Body |
|-------|-------|------|
| `finishReason = length` or `truncated` | **"This result is incomplete."** | **"The model ran out of room before the end of the document, so the last sections may be missing. It can't be saved. Copy what you need, or discard it and try again."** |
| `error{DEADLINE}` | **"That took too long and was stopped."** | **"This result is incomplete and can't be saved. Try again, or use a shorter document."** (+ **Try again**) |
| `error{FAILED}` with partial text, network drop, or stream ended without `done` | **"Something went wrong."** | **"The result is incomplete and can't be saved. Your document is unchanged. Try again."** (+ **Try again**) |
| Stopped by the user | (marker only) | **"A stopped result can't be saved."** |
| Empty buffer (user cleared the textarea) | **"Nothing to save."** | **"The result is empty."** |

"Try again" (gray-bordered) re-runs the same mode and instruction, replacing the partial result, and moves focus to Stop.

**Fact-check warning (D-14-11)** appears only in COMPLETE Clean up/Custom state, directly above the action row (after any blocked message, which cannot coexist with it). It is computed on the current buffer (so it updates as the user edits, debounced 500 ms) against the original. It **never disables Save**.

- With differences: `rounded border border-amber-600 bg-amber-50 p-4 text-sm text-amber-700`. Title (Label): **"Check before saving: ticket keys or versions differ from the original."** Then up to two labelled lists, each a `flex flex-wrap gap-1` row of chips (`rounded border border-amber-600 bg-white px-2 py-0.5 font-mono text-xs text-amber-700`, wrapped in `<ul>`/`<li>`):
  - **"Missing from the result"** (only if any)
  - **"Not in the original"** (only if any)
  Closing line (Small): **"You can still save. Look at each one beside the original first."**
- With no differences: a muted Small line `text-gray-600`: **"All ticket keys and versions in the original are present, and none were added. Other wording changes still need your review."**
- The region is `aria-live="off"` (it changes while typing). Save's `aria-describedby` includes its id, so a screen-reader user hears the warning on reaching Save.
- Lists cap at 10 chips per group, then **"and {n} more"** (Small).

**Save result**
- Success: the card closes, the route moves to the new latest revision (clear the `?revision` search), a polite announcement says **"Saved as Revision {n}."**, and focus moves to the AI-assisted banner at the top of the viewer (`tabIndex={-1}`). The toast-less path is intentional: the banner is the confirmation.
- Failure: red alert under the actions, `role="alert"`: **"Couldn't save the revision — {messageForReason}. Your result is still here. Try again."** The edit buffer is kept; Save becomes active again.

### Copy, Download, Discard

- **Copy**: copies the current buffer (edited text if edited). Label becomes **"Copied"** for 2 seconds; polite announcement **"Copied to clipboard."** If the clipboard call fails: Small `text-amber-700` line **"Couldn't copy. Select the text and copy it manually."**
- **Download**: saves the current buffer as a `.md` file via a Blob, filename `{documentType}-{release name}-{version}-{ai-mode}.md` (kebab-case, characters outside `a-z0-9-` removed; `ai-mode` is `clean-up`, `summary` or `custom`). No server call.
- **Discard** (gray-bordered, last in the row). Nothing is stored (SC: Discard persists nothing).
  - Unedited result (or any state without edits): discards immediately, closes the card, restores the single-column view, announces **"Result discarded."**, and returns focus to the assist button that started the run.
  - Edited buffer: the action row is replaced in place by an inline confirmation (`role="alertdialog"`-free, plain region with `aria-label="Confirm discard"`): Label **"Discard your edits?"**, Body **"Your changes to this result will be lost. The original revision is not affected."**, buttons **"Keep editing"** (gray-bordered, receives focus first) and **"Discard edits"** (`border border-red-600 text-red-700`). Escape cancels.
- "Close" (error-without-text state) behaves like an unedited Discard.

---

## Pre-stream and stream error states (reused from Phase 13)

Shown in the result card body in place of the result (no text yet), or as a banner above the actions when partial text exists (then Save is blocked as in the table above). Same styles and roles as `13-UI-SPEC` ASST-08.

| Outcome | Style | Copy | Actions |
|---------|-------|------|---------|
| OFFLINE | amber, `role="alert"` | **"The assistant is offline."** Body: **"The AI service isn't reachable right now. Your document is unchanged and document generation still works. Try again in a few minutes."** | **Try again**, **Close** |
| BUSY (429) | amber, `role="status"` | **"The assistant is busy with another question."** Body: **"Try again in {n} seconds."** (`Retry-After`); if absent: **"Try again in a moment."** | **Try again** disabled until the countdown ends (visible counter `aria-hidden`, initial value announced once), **Close** |
| MODEL_MISSING (503) | red, `role="alert"` | **"The assistant's language model isn't installed."** Body: **"Ask an administrator to pull the configured chat model on the assistant host."** | **Close** (no retry) |
| TOO_LARGE (413) | amber, `role="status"` | The too-long message from [D]; no card opens | none |
| DEADLINE | amber, `role="alert"` | See blocked-message table | **Try again**, partial text kept |
| FAILED | red, `role="alert"` | **"Something went wrong."** Body: **"The assistant couldn't finish this result. Your document is unchanged. Try again."** (no text) | **Try again**, **Close** |
| 401 | redirect to `/login` like every other call | | |
| CLIENT_GONE | never displayed (caused by Stop) | | |

Document viewing, exporting and generating are never blocked by any of these (SC3): the original card, header and exports remain fully usable while a banner shows.

---

## Saved AI revisions: viewer, history and regenerate notice

### AI-assisted banner and verify banner in the viewer (D-14-06, D-14-12)

Shown above the document card, below the superseded banner, whenever the viewed revision has `aiAssisted = true` (any revision, not only the latest). Not part of `RenderedDocument` chrome, so they never reach PDF or DOCX exports (exports are not stamped).

1. **AI-assisted banner** (`mx-6 mt-2 rounded border border-gray-200 bg-gray-100 p-3 text-sm text-gray-700`, `id="ai-revision-banner"`, `tabIndex={-1}`, `role="note"`):
   Title (Label): **"AI-assisted revision."** Body: **"This revision was {Cleaned up | edited with a custom instruction} with the AI model and saved by {savedBy}. Compare it with the earlier revisions before relying on it."**
   Then a native `<details>` (Small, `mt-2`): summary **"Instruction used"**, content the full instruction text as a text node in `whitespace-pre-wrap` (`mt-1 rounded bg-white p-3 text-xs text-gray-700`). Collapsed by default; for Clean up this shows the configured Clean up instruction for that document type.
2. **Verify banner**, runbook and approval only: the same amber banner and copy as in the result card (**"Verify steps before use."** / **"AI-assisted text can change commands, order, values or risk wording. Compare every step with the original before you rely on it."**), `mx-6 mt-2 rounded border border-amber-600 bg-amber-50 p-3`. Release notes revisions get the AI-assisted banner only.

### Revision history (`DocumentsSection` → `RevisionRow`)

For a revision with `aiAssisted = true`:
- First line is unchanged except "generated" becomes "saved": `Revision 4 (latest) · saved {date} by {user} · template {name}`, and an **"AI-assisted"** badge (`ml-2 rounded border border-gray-300 bg-gray-100 px-2 py-0.5 text-xs font-semibold text-gray-700`) follows the "(latest)/(superseded)" text.
- A second line (`mt-0.5 text-xs text-gray-600`, `truncate`, full text in `title`): for Clean up **`AI instruction: Clean up`**; for Summarize **`AI instruction: Summarize`**; for Custom **`AI instruction: "{instruction text}"`** (one line, truncated with an ellipsis).
- The same badge is added to the latest-revision summary line in `DocumentRow` ("Revision {n} of {total} …") when the latest revision is AI-assisted.
- View and export buttons are unchanged. Non-AI revisions are unchanged.

### Regenerate notice (D-14-02)

In `DocumentRow`, when the latest revision is AI-assisted and a template exists (the Regenerate button is shown): a persistent note directly under the button row, `mt-1 text-xs text-amber-700`, `id` referenced by the Regenerate button's `aria-describedby`:

**"The latest revision was AI-assisted; regenerating replaces it as current."**

No dialog and no extra confirmation (assumption 6). The AI revision stays in the history. The note disappears as soon as a non-AI revision becomes latest.

---

## Copywriting Contract

| Element | Copy |
|---------|------|
| Primary CTA (result) | **"Accept and save as revision N+1"** |
| Primary CTA (custom) | **"Run instruction"** |
| Assist bar label | **"AI assist"** |
| Assist buttons | **"Clean up"**, **"Summarize"**, **"Custom…"** |
| Assist helper | **"Runs on this server's local model. Only this document's text is sent. Results can be saved as a new revision; the original is never changed."** |
| Disabled-while-open hint | **"Save or discard the current result first."** |
| Revision select hint | **"Save or discard the AI result first."** |
| Column labels | **"Original · Revision {n}"**; result titles **"Cleaned-up version"** / **"Summary"** / **"Custom result"** |
| View toggle | **"Edit"** / **"Source"** and **"Preview"**; group label **"Result view"** |
| Edited tag | **"Edited"** |
| Custom label / placeholder | **"Custom instruction"** / **"e.g. Rewrite for executives, keep every ticket key and version"** |
| Custom helper | **"The model sees only this document and your instruction. The instruction is saved with the revision if you save the result."** |
| Recent instructions | **"Recent instructions…"** (placeholder option) |
| Waiting / writing | **"Waiting for the model…"**, **"Writing… {n} characters so far"**, **"Long documents can take a few minutes on this server. You can stop at any time."** |
| Stop / marker | **"Stop"**; **"Stopped. This result is incomplete."** |
| Too long (D-14-10) | **"This document is too long for the AI model on this server (about {N} of {M} characters)."** + **"Nothing was sent. A larger model on the server raises this limit."** |
| Verify banner (D-14-12) | **"Verify steps before use."** + **"AI-assisted text can change commands, order, values or risk wording. Compare every step with the original before you rely on it."** |
| Fact-check warning (D-14-11) | **"Check before saving: ticket keys or versions differ from the original."**; lists **"Missing from the result"**, **"Not in the original"**; **"You can still save. Look at each one beside the original first."**; clean: **"All ticket keys and versions in the original are present, and none were added. Other wording changes still need your review."** |
| Save helper | **"Saving adds Revision {n+1} as the current revision, so exports and the assistant will use it. Revision {n} stays in the history unchanged."** |
| Summary-only note (D-14-03) | Removed by D-14-13: summaries are saved like Clean up results. |
| Blocked: length/truncated | **"This result is incomplete."** + **"The model ran out of room before the end of the document, so the last sections may be missing. It can't be saved. Copy what you need, or discard it and try again."** |
| Blocked: DEADLINE | **"That took too long and was stopped."** + **"This result is incomplete and can't be saved. Try again, or use a shorter document."** |
| Blocked: FAILED | **"Something went wrong."** + **"The result is incomplete and can't be saved. Your document is unchanged. Try again."** |
| Blocked: stopped | **"A stopped result can't be saved."** |
| Blocked: empty | **"Nothing to save."** + **"The result is empty."** |
| Offline / busy / model missing | See "Pre-stream and stream error states". |
| Save failed | **"Couldn't save the revision — {messageForReason}. Your result is still here. Try again."** |
| Save success (announced) | **"Saved as Revision {n}."** |
| Copy | **"Copy"**, **"Copied"**, **"Copied to clipboard."**, failure **"Couldn't copy. Select the text and copy it manually."** |
| Download | **"Download"** |
| Discard | **"Discard"**; announced **"Result discarded."**; error-without-text button **"Close"** |
| Destructive confirmation | **Discard edits**: **"Discard your edits?"** / **"Your changes to this result will be lost. The original revision is not affected."** Buttons **"Keep editing"** and **"Discard edits"**. Applies only when the buffer was edited. |
| Empty state | There is no empty state: the assist bar is the entry and the card only opens on a run. A document that doesn't exist yet already shows the existing "This document doesn't exist yet." |
| AI-assisted badge | **"AI-assisted"** |
| History instruction line | **`AI instruction: Clean up`** / **`AI instruction: Summarize`** / **`AI instruction: "{text}"`** |
| Viewer banner | **"AI-assisted revision."** + **"This revision was {Cleaned up \| Summarized \| edited with a custom instruction} with the AI model and saved by {savedBy}. Compare it with the earlier revisions before relying on it."**; details summary **"Instruction used"** |
| Regenerate notice (D-14-02) | **"The latest revision was AI-assisted; regenerating replaces it as current."** |

---

## Interaction and Accessibility

- **Live regions.** The raw stream text container is `aria-live="off"` with `aria-busy="true"` while tokens arrive and `"false"` after. A separate visually hidden `role="status" aria-live="polite" aria-atomic="true"` region inside the AI panel announces only discrete events: **"Assistant is working."** on run, **"Result ready."** on complete, **"Result incomplete."** on any incomplete end, **"Stopped."**, **"Copied to clipboard."**, **"Saved as Revision {n}."**, **"Result discarded."**. The char counter and the fact-check warning are not announced as they change. Error and blocked banners use `role="alert"` (announced once on mount); BUSY and the too-long message use `role="status"`.
- **Keyboard order.**
  - Assist bar: "Clean up", "Summarize", "Custom…", then (if open) the Custom box: Recent instructions select, textarea, Run instruction.
  - Run → focus on **Stop** (streaming). Stop is the only action control in the card until the stream ends. Escape also stops.
  - Stream ends or is stopped → focus moves to the result body (the textarea for editable results, the `tabIndex={-1}` Source region otherwise), so the focused Stop button vanishing never drops focus to the page.
  - Card Tab order after completion: view toggle (Edit/Source, Preview) → result body → **Accept and save as revision N+1** → Copy → Download → **Discard**. Save always precedes Discard; Discard is last so a stray Enter on Save cannot discard. The fact-check warning and blocked message are not focusable; they are exposed through Save's `aria-describedby`.
  - Edited-discard confirmation: focus moves to **Keep editing**; Escape or Keep editing returns focus to Discard.
  - Discard / Close → focus returns to the assist button that started the run (Custom… for custom runs). Save success → focus to `#ai-revision-banner`. Try again → focus to Stop.
- **Native semantics.** All controls are native; the Edit/Preview toggle uses `aria-pressed`; "Custom…" uses `aria-expanded`/`aria-controls`; `<details>` for the instruction; no focus trap, no modal, no `ConfirmDialog`.
- **Contrast.** Amber and red banner text on tinted backgrounds meets 4.5:1 (`amber-700` on `amber-50`, `red-700` on `red-50`); essential messages use `gray-600` or darker, `gray-500` only for non-essential metadata labels.
- **Reduced motion.** The spinner is the only animation: `motion-reduce:animate-none`, label text always shown. No caret blink, no smooth scrolling.
- **Plain-text safety.** The streamed text and textarea content are React text children and `value`, never `dangerouslySetInnerHTML`. Preview goes only through `md` (`html: false`) with remote images disabled (see Preview safety). The instruction text in the banner and history is a text node. Model, instruction and document text are never written to `localStorage`, `sessionStorage`, the URL, or the console.
- **No persistence.** Edit buffer, stream state, view mode and the recent-instructions list are in memory only; reload clears them (D-14-09, D-13-12). The limits and assistant-status queries are ordinary TanStack Query data (non-sensitive).
- **Auth.** Same cookie session and 401 redirect as every other call; no key in the browser.
- **Responsive.** Desktop-first internal tool. Only treatment: the grid stacks below `lg` (original above result), the assist bar and action row wrap.

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none | not applicable, no shadcn tool in use |
| third-party | none | not applicable |

---

## Requirement-to-UI Traceability

| Requirement / Decision | UI Contract Element |
|---|---|
| ASST-09 | Assist bar (Clean up, Summarize, Custom…), side-by-side original and streaming result, Stop, Edit/Preview, Save as new revision |
| ASST-10 | Instruction recorded: Custom box, "Instruction used" details in the viewer banner, `AI instruction:` line and AI-assisted badge in history |
| D-14-01 | Save helper line (current revision, exports and assistant use it), no export stamping, AI revision viewable like any other |
| D-14-02 | Regenerate notice under the Regenerate button |
| D-14-03 (superseded by D-14-13) | Summarize saves like Clean up: editable result, Accept, Copy and Download; no summary-only note |
| D-14-04 | Two-column grid from `lg`, stacked below |
| D-14-05 | Textarea + Preview toggle using `md`; no editor dependency |
| D-14-06 | AI-assisted badge and instruction in history; viewer banner; banners outside `RenderedDocument` (not in exports) |
| D-14-09 | Free-text Custom box, recent-5 select held in memory only |
| D-14-10 | Too-long message with N and M, buttons disabled before sending, 413 shows the same message |
| D-14-11 | Fact-check warning listing missing and new keys/versions above Save, never disables it |
| D-14-12 | "Verify steps before use." banner above the result and on saved runbook/approval AI revisions, absent from exports |
| AI-SPEC §6 | Save `aria-disabled` with explanatory message on length, truncated, DEADLINE, FAILED, stopped and no-`done` ends |
| SC3 / ASST-08 | Phase 13 states reused (OFFLINE, BUSY, MODEL_MISSING, DEADLINE, FAILED), generation and exports unaffected |
| D-13-12 | No browser storage; in-memory only |
| SAFE-02 (carried) | Plain text for the stream, `html: false` Preview, no remote images in Preview |

---

## Assumptions recorded (not settled by CONTEXT, AI-SPEC or prior UI-SPECs)

1. The assist bar appears only on the **latest** revision. The server always transforms the stored revision (AI-SPEC §4), and a saved result becomes N+1 of the latest; offering it on a superseded revision would make the source ambiguous.
2. While a result card is open, the three assist buttons and the Revision select are disabled. Starting a second run or switching revision would silently replace unsaved edits, and this also keeps the one-in-flight server slot simple.
3. Discard confirms only when the buffer was edited; an unedited result is discarded at once. A completed generation can cost minutes of GPU time, but the click is explicit and the result is regenerable.
4. Incomplete results are read-only (Source), Download is disabled for them, Copy stays available (AI-SPEC §6: Discard and copy stay available). Download is disabled so a partial file is never mistaken for a full document.
5. If the limits lookup fails, the assist buttons stay enabled and the server 413 is the authority (AI-SPEC §4 pitfall 2). `M` for Custom is recomputed from the typed instruction length with the same formula; the planner supplies the limits endpoint shape.
6. The regenerate notice is persistent text, not a blocking dialog. CONTEXT specifies the notice copy, not a confirmation.
7. The fact-check warning recomputes on the edited buffer (not only the raw model output), so an edit that restores a missing key clears the warning. Chips cap at 10 per group.
8. Recent instructions are de-duplicated and stored in a module-level store (survives in-app navigation, cleared on reload), mirroring Phase 13's session store. A Clean up/Summarize run does not add to the list.
9. Preview blocks remote images. The saved revision viewer already uses the existing renderer for AI text; whether it also blocks remote images for AI-assisted revisions is left to the planner (recommended: yes, render alt text only when `aiAssisted`).
10. The saved revision exposes `aiAssisted`, `aiMode` (`CLEAN_UP | CUSTOM`), `aiInstruction` and the saving user (reuse `generatedBy`) to the viewer and history; the viewer banner and history line read them. Field names are the planner's call.
11. The viewer banner and history use "saved" in place of "generated" for AI revisions, because the revision was saved by a person from model output, not generated from a template.
12. The Clean up instruction shown under "Instruction used" is the configured text for that type; it can be long, hence the collapsed `<details>`. The history line shows only the mode name for Clean up to stay on one line.
13. No router navigation blocker for unsaved edits. Navigating away or reloading drops the result without a prompt, consistent with Phase 13's in-memory chat. Revisit if the user wants a leave-page warning.
14. Summarize on runbook and approval documents still shows the verify banner (D-14-12 says "results" of those types).
15. Download filename pattern and the Save-success path (card closes, route moves to the new latest revision, focus to the banner) are chosen here; CONTEXT does not specify them.

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS
- [ ] Dimension 2 Visuals: PASS
- [ ] Dimension 3 Color: PASS
- [ ] Dimension 4 Typography: PASS
- [ ] Dimension 5 Spacing: PASS
- [ ] Dimension 6 Registry Safety: PASS

**Approval:** pending
