---
phase: 13
slug: assistant-screen-quality-gate
status: approved
reviewed_at: 2026-10-15
shadcn_initialized: false
preset: none
created: 2026-10-15
---

# Phase 13 — UI Design Contract: Assistant Screen

> Visual and interaction contract for ASST-02, ASST-05, ASST-06, ASST-07, ASST-08 and SAFE-02 (plain-text answers). The quality gate (SAFE-04) has no UI and is not covered. Extends the Phase 2-8 design language: no new component library, no Markdown renderer for answers, no new icon library, no new type size, no new colour family. New elements: (1) a chat transcript, (2) a scope chip with release picker, (3) a right-hand drawer, (4) citation chips, (5) a collapsed trace panel, (6) state banners.

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none. No `components.json`. Not reopened. |
| Preset | not applicable |
| Component library | none. Native `<button>`, `<select>`, `<textarea>`, `<details>`/`<summary>`, `<ol>`, `<a>`. |
| Icon library | none. Plain glyphs only (`×`, `▸`/`▾`, `·`, `…`). Pending state is the existing spinner `h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-blue-600`. |
| Font | system default (Tailwind `sans`), unchanged. |

Correction to the brief: the frontend **does** use Tailwind v4 (`frontend/src/index.css` is `@import "tailwindcss"` plus the typography plugin; every component uses utility classes). This contract is therefore written in Tailwind utilities, as Phases 1-8 are. The `typography` plugin and `markdown-it` must NOT be used for answers (D-13-09): answers render as a text node inside an element with `whitespace-pre-wrap`.

Stack facts that shape the contract: Vite, React, TypeScript, TanStack Router + Query. Streaming uses `fetch` + `ReadableStream` + `AbortController` (SSE over POST, Claude's discretion in CONTEXT). Chat state lives in React memory only (D-13-12): no `localStorage`, no `sessionStorage`.

---

## Spacing Scale

Reuses the Phase 2-8 scale. No new tokens.

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4px | Gap between chips (`gap-1`), glyph gaps |
| sm | 8px | Gap between message turns' inner elements, chip-row top margin (`mt-2`), button gaps |
| md | 16px | Transcript padding, composer padding, turn-to-turn gap (`space-y-4`) |
| lg | 24px | Page section padding |
| xl | 32px | Not used |
| 2xl | 48px | Empty-state top padding (`py-12`) |
| 3xl | 64px | Not used |

Exceptions (inherited classes, matched exactly as in 08-UI-SPEC, not new tokens): `py-0.5` / `px-2` pills and citation chips (same as ReadinessPill and CodeLinks chips); `px-3 py-1.5` bordered controls (select, "New chat", "Stop", "Show how I answered" summary); the tag chip uses `px-2 py-0.5` (normalised from `PlaceholderDrawer`'s `px-1.5` to stay on the 4px grid).

Fixed dimensions: drawer width 380px (matches `PlaceholderDrawer`, `w-[380px]`); below the `lg` breakpoint it becomes a bottom sheet `max-h-[70vh]`, same responsive classes as `PlaceholderDrawer`. Assistant page content column `max-w-3xl` (768px), centered by `mx-auto`. Composer textarea min height 3 rows (`rows={3}`), grows to a max of 8 rows then scrolls.

---

## Typography

Reuses the Phase 5 / 8 scale verbatim. No new size, no new weight (400 and 600 only).

| Role | Size | Weight | Line Height |
|------|------|--------|-------------|
| Body | 14px (`text-sm`) | 400 | 1.5 |
| Label | 14px (`text-sm`) | 600 | 1.5 |
| Heading | 16px (`text-base`) | 600 | 1.2 |
| Display | 18px (`text-lg`) | 600 | 1.2 |
| Small (chips, status, notes) | 12px (`text-xs`) | 400, 600 inside chips | 1.5 |

Usage:
- Page title "Assistant" and drawer title "Ask about this release": Heading 16/600.
- Answer text and user question text: Body 14/400, `whitespace-pre-wrap break-words` so line breaks survive (D-13-09).
- Turn author label ("You", "Assistant"): Small 12/600 `text-gray-500`.
- Citation chips, "index last updated" line, status line, stopped marker, "older turns not used" notice, trace panel body: Small 12.
- Inline `[n]` markers are part of the plain text: they are not styled, not links, not superscripted (see Citations).
- Tool arguments and ids in the trace panel use `font-mono text-xs`.

---

## Color

No new colour. Existing palette only.

| Role | Value | Usage |
|------|-------|-------|
| Dominant (60%) | `gray-50` | Page canvas, transcript background |
| Secondary (30%) | `white` | Assistant turn card (`rounded border border-gray-200 bg-white`), composer, drawer surface |
| Accent (10%) | `blue-600` | See reserved-for list |
| Destructive | `red-600` / `red-700`, `red-50` bg | FAILED and MODEL_MISSING alerts only. No destructive action exists in this phase. |
| Warning | `amber-700` text, `amber-50` bg, `amber-600` border | BUSY, DEADLINE, OFFLINE banners; "older turns not used" notice; index-stale line when over threshold |
| Muted | `gray-600` text on `gray-100` bg, `border-gray-200` | Refusal note, stopped marker, status line |

Accent (`blue-600`) reserved for:
1. The "Send" primary button (`bg-blue-600 text-white`, the one filled button on the screen).
2. The tool-phase spinner's leading edge (`border-t-blue-600`), as in every existing spinner.
3. Citation chip text and the link affordance on chips (`text-blue-600`, `hover:underline`), as release links are blue in Phase 8.
4. `focus-visible:ring-2 focus-visible:ring-blue-600` on every control (matches `PlaceholderDrawer` filter input).
5. The nav item's active state is unchanged (`font-semibold text-gray-900`, not accent).

Starter-question buttons, scope chip, "New chat", and "Stop" are gray-bordered (`border border-gray-300 text-gray-700`), not accent. User turns are distinguished by alignment and a `bg-gray-100` card, not by accent colour.

Colour is never the only signal: every state has a text label (see Copywriting), the refusal note is prefixed by text, the stopped marker is text.

---

## Screen Layout

### Navigation (D-13-05)

`AppShell` gets one new `Link to="/assistant"` labelled **Assistant**, placed after "Releases" and before "Templates", with the same `className` and `activeProps` as its siblings. It renders only when the assistant status query reports `enabled: true`. While the status query is loading or has failed, the item is not rendered (hidden is the safe default; no flash of a link that then disappears). When disabled, navigating to `/assistant` directly redirects to `/`.

### Assistant page (`/assistant`)

Inside `main`, a single column `mx-auto flex max-w-3xl flex-col`, top to bottom:

1. **Header row** (`flex items-center justify-between`): `<h2>` "Assistant" (Heading) on the left; "New chat" bordered button on the right (disabled when the transcript is empty).
2. **Index freshness line**, directly under the header, Small `text-gray-500`: "Search index last updated {relative time}" with the absolute timestamp in `title` and also in a visually hidden suffix for screen readers. See ASST-08 states.
3. **State banner slot** (ASST-08), shown only when a state applies.
4. **Transcript** (`flex-1 space-y-4`, normal page scroll; no inner scroll container on the page variant so the browser's find and scroll work). Contains the empty state or the turns.
5. **Composer** (sticky to the viewport bottom: `sticky bottom-0 bg-gray-50 pt-2 pb-4`):
   - Scope row: scope chip, or on the page the release picker when unscoped.
   - Textarea + Send/Stop button on the right.

Page chat and drawer chat are separate sessions (D-13-13); both survive in-app navigation by living in a module/provider-level store above the router outlet, and are cleared by reload, logout, or "New chat".

### Release-detail drawer (D-13-01)

- A button **"Ask about this release"** (gray-bordered, `px-3 py-1.5`) in the release detail header action area. Rendered only when assistant is enabled. Carries `aria-expanded` and `aria-controls="assistant-drawer"`.
- The drawer follows the `PlaceholderDrawer` overlay pattern: non-modal `role="region"` `aria-label="Ask about this release"`, `absolute right-0 top-0 z-10 h-full w-[380px] overflow-y-auto border-l border-gray-300 bg-white shadow-lg`, bottom sheet below `lg`. Non-modal on purpose: the release stays visible and interactive (D-13-01).
- Header: Heading "Ask about this release", `grow` spacer, close button `×` with `aria-label="Close assistant"`, same markup as `PlaceholderDrawer`, plus "New chat".
- The drawer reuses the exact same `AssistantChat` body (transcript, status, composer) as the page. Differences: the scope chip is fixed to the current release and its `×` is **not** shown (removing scope would turn a release drawer into an unscoped chat; the page is for that). The release picker is absent.
- The drawer's chat is keyed by `releaseId`. Opening the drawer on a different release shows a fresh chat (D-13-03, D-13-13). Closing the drawer does not clear the chat; reopening on the same release restores it for the lifetime of the page session.
- Dismissal: `×`, the toggle button, and Escape. Unlike `PlaceholderDrawer`, **click outside does NOT close it**, because the user will click release content while reading citations and a stray click must not discard a streaming view. Closing the drawer while streaming aborts the stream like Stop and marks the turn "stopped" (an invisible streaming request would hold the one-in-flight slot with no way to cancel it).

### Scope chip and release picker (D-13-02, D-13-03)

- Chip: `inline-flex items-center gap-1 rounded border border-gray-300 bg-white px-2 py-0.5 text-xs text-gray-700`, text **"Release: {name}"** and a `×` `<button aria-label="Remove release scope {name}">`. Unscoped state shows no chip.
- Release picker (page only, shown when unscoped): a native `<select aria-label="Scope to a release">`, first option **"All releases"** (selected), then releases newest first. Choosing one adds the chip and replaces the picker.
- Changing scope (adding, changing, or removing the chip) with a non-empty transcript starts a new chat. No confirmation dialog (the chat is ephemeral and nothing is saved), but the change is announced: a brief inline Small line replaces the transcript: **"Started a new chat for {Release: name | all releases}."** in the polite live region, and the transcript is cleared. If a stream is in flight it is aborted first.
- With an empty transcript, changing scope just changes scope (no announcement).

### Empty state with starter questions (D-13-15)

Shown when the transcript has no turns. `py-12 text-center`:
- Heading (Heading role): **"Ask about your releases"** (unscoped) / **"Ask about {release name}"** (scoped).
- Body Small `text-gray-600`: **"Answers come from your releases, tickets, deployments, documents and linked code. Every answer shows where it came from."**
- Below, 3 starter buttons stacked (`mx-auto flex max-w-md flex-col gap-2`), each a full-width gray-bordered button, `text-left`. Click sends the question immediately (not just fills the input). Lists come from config (`app.assistant.*`, D-13-17) via the status endpoint; the list is fetched, not hard-coded in the bundle. If the configured list is empty, the starter block is omitted and the heading and body remain.
- `{release}` in a starter is replaced with the release name client-side before display.

Default starters (used when config supplies none; these are the config defaults):
- Unscoped: **"What shipped to Production last week?"**, **"Which releases are not ready to ship?"**, **"Which documents are out of date?"**
- Scoped: **"Is {release} ready for Production?"**, **"What changed in {release}?"**, **"Which tickets in {release} have no linked code?"**

### Turns (ASST-05, ASST-06)

User turn: right-aligned card `ml-12 rounded bg-gray-100 p-4`, label "You" (Small 12/600), text Body `whitespace-pre-wrap`.

Assistant turn: `mr-12 rounded border border-gray-200 bg-white p-4`, label "Assistant", then in order:
1. Tool-phase status line (only while running)
2. Answer text (streams here)
3. Marker line (stopped), if any
4. Citation chip row
5. "How I answered" panel
6. Refusal replaces 3-5 when applicable; error states replace the answer area (see ASST-08).

**Tool-phase status line (D-06)**: while the orchestrator is in tool rounds, show spinner + Small `text-gray-600` text, e.g. **"Checking deployments…"**. Label per tool comes from the server's tool event and is mapped client-side from a fixed table; an unknown tool shows **"Looking things up…"**. Default mapping, any tool not listed uses the fallback:

| Tool intent | Status text |
|---|---|
| `releases`, `tickets` | "Looking up release details…" |
| `deployments` | "Checking deployments…" |
| `documents` | "Checking documents…" |
| any other tool | "Looking things up…" |

No model tokens are displayed during tool rounds (up to about 150 characters are held back per round, D-13-21; "Searching documents…" and "Checking linked code…" have no tool and are not used). When a `roundDiscarded` event arrives after tokens were buffered, the buffer is dropped silently and the status line returns to a tool label. The first token of the final round replaces the status line with streaming text. Before any event arrives (request sent) show **"Thinking…"** with the spinner.

**Streaming text**: appended to a single text node. While streaming, the turn shows a **Stop** button in place of Send in the composer (see Interaction). A blinking caret is not used (avoids motion issues).

**Stopped marker**: Small italic-free line `text-xs text-gray-500` under the partial text: **"Stopped. This answer is incomplete."** Partial text stays. No chips and no trace panel are shown for a stopped turn unless the server already sent them (they arrive at the end of a stream, so normally absent).

**Inline `[n]` markers**: remain literal text in the answer (`[1]`, `[2]`). They are not links and not restyled (plain-text rule, D-13-09, and avoids parsing model output into markup). The matching chip row below carries the same `[n]` in its label so a reader pairs them visually.

**Citation chip row (D-13-07)**: `mt-2 flex flex-wrap gap-1`, label for the group `role="list"` with `aria-label="Sources"`. One chip per source in `[n]` order, even if the text did not cite one (the server returns the sources used). Chip: `rounded border border-gray-200 bg-gray-50 px-2 py-0.5 text-xs text-blue-600 hover:underline` as an `<a>` inside `role="listitem"`; `max-w-full truncate`, full label in `title`.

Chip label format: **`[n] {Kind} {identifier}`**, built from the server's citation record, never from model text:

| Source | Label | Link target |
|---|---|---|
| Ticket | `[2] Ticket PROJ-123` | `/releases/$releaseId#ticket-PROJ-123`; unlinked when the citation has no valid release id |
| Document revision | `[3] Release Notes 4.2.0 rev 3` | `/releases/$releaseId/documents/$documentType` with `?revision=3` |
| Manual item | `[4] Manual item Run migration` | `/releases/$releaseId` |
| Code links | `[5] Code links PROJ-123` | `/releases/$releaseId#ticket-PROJ-123` (the expanded ticket row lists commits and PRs with their ADO links) (D-13-22) |
| Tool result | `[6] {tool source title}` | `/releases/$releaseId` when the source names a release, else unlinked |

Document type words use the existing `DOCUMENT_TYPE_LABELS`. Chips are internal links only; the frontend never renders an external href (Amendment 2026-10-16). Internal links navigate with TanStack `Link`; in the drawer, internal chip links to the **same** release keep the drawer open; links to a different route/release navigate normally (drawer state is cleared by the route change, chat is retained for that release).

**"How I answered" panel (D-13-08)**: native `<details>` collapsed by default, `mt-2`, `<summary>` text **"How I answered"** (Small 12/600 `text-gray-600`, `▸` closed / `▾` open via CSS `details[open]`, glyph `aria-hidden`). Expanded (Small, `mt-2 space-y-2 border-t border-gray-100 pt-2`):
- **"Tools used"** — list, one row per tool call: tool label + arguments, e.g. `Deployments · release: 4.2.0 · environment: Production`, args in `font-mono`. If no tool ran: **"No lookups were needed."**
- **"Sources used"** — the same entries as the chip rows, as `[n] label` text without links. If none: **"No sources were used."**
- Values are rendered as text, never as HTML.

**Refusal note (D-13-11)**: when the outcome is NO_ANSWER, the answer area is replaced by a muted note, not a red alert: `rounded border border-gray-200 bg-gray-100 p-4 text-sm text-gray-600`, no chips, no trace panel (the trace panel is still shown if tools ran: assumption 6). Copy: **"I can't answer that from the data I have access to."** with a second Small line **"Try naming a release, ticket or document, or ask about something recorded in this tool."** Role is plain text, not `role="alert"`.

**"Older turns not used" notice (D-13-14)**: when the server reports trimmed history, a Small notice sits at the top of the affected assistant turn: `text-xs text-amber-700`: **"Older turns weren't used for this answer. Start a new chat to include everything."** The "Start a new chat" phrase is not a link; "New chat" button is in the header. The server's trimmed flag is per request, so the notice appears on each turn where it applies.

### Composer and Stop

- `<form>` with `<textarea aria-label="Ask a question" placeholder="Ask a question…">`. Enter sends, Shift+Enter inserts a line break. Maximum 1,500 characters (Amendment 2026-10-16), counter shown only when above 1,300 as Small amber `"{n}/1500"`. Empty or whitespace-only: Send disabled.
- **Send**: `rounded bg-blue-600 px-4 py-2 text-sm font-semibold text-white disabled:opacity-50`, label **"Send"**.
- While a turn is in flight, Send is replaced in the same position by **Stop**: gray-bordered `rounded border border-gray-300 px-4 py-2 text-sm text-gray-700`, label **"Stop"**, `aria-label="Stop generating"`. The textarea stays editable (the user can draft the next question) but submit is disabled until the stream ends. Escape while focus is in the composer or on Stop also stops the stream.
- On submit, the textarea clears, focus stays in the textarea, and the transcript scrolls so the new user turn is in view. After stream end focus returns to the textarea.
- Scroll: the transcript follows new text only while the user is within ~80px of the bottom; if the user scrolled up, following pauses (no jump).

### "New chat" control

Header button (page and drawer): gray-bordered, label **"New chat"**. Disabled when the transcript is empty. Click: aborts any stream, clears the session and returns to the empty state, moves focus to the textarea, announces **"Chat cleared."** in the polite live region. No confirmation dialog: nothing is stored and the loss is the stated behaviour of the control (assumption 3). Scope is retained when starting a new chat via this button.

---

## ASST-08 States

State banners sit in the banner slot (page) or directly under the drawer header, using `role="alert"` for failures and `role="status"` for informative ones. Distinct text, never colour-only.

| Outcome | Where it shows | Style | Copy | Actions |
|---|---|---|---|---|
| OFFLINE | Banner above composer; replaces the in-flight turn's answer with the same text | amber (`rounded border border-amber-600 bg-amber-50 p-4 text-sm text-amber-700`), `role="alert"` | **"The assistant is offline."** + body **"The search service isn't reachable right now. Your question wasn't answered. Try again in a few minutes."** | **"Try again"** (resends the last question) |
| BUSY (429) | In the turn | amber, `role="status"` | **"The assistant is busy with another question."** + body **"Try again in {n} seconds."** where n comes from `Retry-After`; if absent: **"Try again in a moment."** | **"Try again"** button disabled until the countdown ends; the countdown text updates once per second visually but is announced only at start (static `aria-live` text with the initial value; the visible counter is `aria-hidden`) |
| MODEL_MISSING (503) | Banner, persistent until the next success | red, `role="alert"` | **"The assistant's language model isn't installed."** + body **"Ask an administrator to pull the configured chat model on the assistant host."** | none (no retry; composer stays enabled so a fixed server recovers without reload) |
| DEADLINE | In the turn, keeping any partial text | amber, `role="alert"` | **"That took too long and was stopped."** + body **"Try a narrower question, or name a specific release or ticket."** If partial text exists it stays above the banner with the marker **"Stopped. This answer is incomplete."** | **"Try again"** |
| FAILED | In the turn | red, `role="alert"` | **"Something went wrong."** + body **"The assistant couldn't finish this answer. Try again, or rephrase the question."** | **"Try again"** |
| Network drop / HTTP 401 | 401: redirect to `/login` like every other call. Network drop mid-stream: treat as FAILED. | | | |
| CLIENT_GONE | Never displayed (the user caused it by Stop) | | | |

"Try again" resends the same user question as a new turn after removing the failed assistant turn. It replaces, not duplicates, the failed turn.

**Index last updated**: shown on the page under the header and in the drawer under its header, Small `text-gray-500`: **"Search index last updated {relative}"**, e.g. "4 minutes ago", using the most recent `lastIndexedAt` across collections for the page, and the same value for the drawer (collection-level; per-release is not available). If older than 24 hours, the text turns `text-amber-700` and gains **" · Recent changes may be missing."** If the value is null (never indexed): **"Search index hasn't been built yet."** amber. If the freshness lookup itself fails: omit the line (it is supplementary; no error shown).

**Loading of the assistant status** (enabled flag, starters, freshness): on `/assistant`, a centered spinner with **"Loading assistant…"**; failure to load shows the generic red alert **"Couldn't load the assistant — {messageForReason}. Try again."** with a gray-bordered **"Try again"** button (same markup as 08-UI-SPEC).

---

## Copywriting Contract

| Element | Copy |
|---------|------|
| Primary CTA | **"Send"** (composer); **"Ask about this release"** (release-detail entry) |
| Nav label | **"Assistant"** |
| Page heading | **"Assistant"** |
| Empty state heading | **"Ask about your releases"** / **"Ask about {release name}"** |
| Empty state body | **"Answers come from your releases, tickets, deployments, documents and linked code. Every answer shows where it came from."** |
| Composer placeholder | **"Ask a question…"** |
| Scope chip | **"Release: {name}"** |
| Scope picker | **"All releases"** (first option) |
| Scope change notice | **"Started a new chat for Release: {name}."** / **"Started a new chat for all releases."** |
| Status lines | **"Thinking…"**, **"Checking deployments…"**, **"Searching documents…"**, **"Checking documents…"**, **"Checking linked code…"**, **"Looking up release details…"**, fallback **"Looking things up…"** |
| Stopped marker | **"Stopped. This answer is incomplete."** |
| Trace panel | **"How I answered"**, **"Tools used"**, **"Sources used"**, **"No lookups were needed."**, **"No sources were used."** |
| Refusal | **"I can't answer that from the data I have access to."** + **"Try naming a release, ticket or document, or ask about something recorded in this tool."** |
| History trimmed | **"Older turns weren't used for this answer. Start a new chat to include everything."** |
| Index freshness | **"Search index last updated {relative}"**; stale suffix **" · Recent changes may be missing."**; never indexed **"Search index hasn't been built yet."** |
| Error states | See the ASST-08 table: OFFLINE, BUSY, MODEL_MISSING, DEADLINE, FAILED, each with problem and next step. |
| New chat | **"New chat"**; announced **"Chat cleared."** |
| Destructive confirmation | None. "New chat", scope change and drawer close discard only in-memory, unsaved chat text, which is the documented behaviour. No `ConfirmDialog`. |
| Answer disclaimer | None added to every turn (assumption 8). The empty-state body carries the sourcing statement. |

---

## Interaction and Accessibility

- **Live regions.** Streaming text is rendered inside a container `aria-live="off"` while tokens arrive (tokens are never announced individually). A separate, visually hidden `role="status" aria-live="polite" aria-atomic="true"` region announces only discrete events: **"Assistant is working."** on submit, the current status-line text on each tool phase change (debounced, max one per second), **"Answer ready."** when the stream finishes (the final text is then readable by arrow-key navigation, and the user can focus the answer, which has `tabIndex={-1}`), **"Stopped."** on Stop, **"Chat cleared."**, and the scope-change notice. Error banners use `role="alert"` (announced once on mount); BUSY uses `role="status"`.
- **Answer container** has `aria-busy="true"` while streaming and `"false"` after.
- **Keyboard.** All controls are native and in DOM order: header ("New chat"), scope chip `×` / picker, starters (empty state) or transcript links, trace `<summary>`s (native Enter/Space toggling), textarea, Send/Stop. Stop is in the tab order immediately after the textarea (the same slot as Send), so it is reachable with one Tab from the composer, and Escape stops from the composer. Citation chips are real links, reachable by Tab. No focus trap in the drawer (non-modal).
- **Drawer focus handling.** On open (button activation), focus moves to the drawer's textarea (or to the first starter button when the transcript is empty, whichever exists first in the empty state: the starter list; the textarea otherwise). On close (via `×`, Escape or the toggle), focus returns to the "Ask about this release" button that opened it. Escape closes the drawer only when focus is inside the drawer, and does not conflict with Stop: if a stream is running and focus is in the composer, the first Escape stops the stream (and does not close); a second Escape closes. The toggle button's `aria-expanded` mirrors state.
- **Focus after state changes.** After "Try again", focus goes to the composer textarea; after a scope change, to the textarea; after the trace panel toggles, focus stays on the `<summary>`.
- **Contrast.** Text colours used (`gray-500` on white/gray-50 for Small meta text is acceptable for non-essential metadata only; all essential messages use `gray-600` or darker). Amber and red banner text meets 4.5:1 on its tinted background (`amber-700` on `amber-50`, `red-700` on `red-50`).
- **Reduced motion.** The spinner is the only animation; respect `prefers-reduced-motion` with `motion-reduce:animate-none` and show the label text regardless.
- **Plain text safety.** Model output, tool arguments, citation labels and ticket text are all inserted as React text children. No `dangerouslySetInnerHTML`, no Markdown library, no `rehype-raw`. Chip links are internal TanStack Links built from regex-validated ids (uuid, ticket key, document type, positive revision); anything that fails validation renders as an unlinked chip (guards `javascript:` hrefs). No external URLs are rendered.
- **No persistence.** Chat text is held in memory only; the status/starters/freshness queries are TanStack Query data (non-sensitive, and may be cached by Query as usual).
- **Auth.** The stream request uses the existing cookie session with `credentials: 'same-origin'`; a 401 triggers the same redirect-to-login as other API calls. No client key exists in the browser.

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| shadcn official | none | not applicable, no shadcn tool in use |
| third-party | none | not applicable |

---

## Amendments (2026-10-16, after research)

Status stays approved; the affected rows above were edited in place.

- D-13-22: the commit chip row is replaced by "Code links PROJ-123" (internal link to the ticket row) and a Tool result row is added. Manual item and Document rows are internal links. External hrefs and the `↗` glyph are dropped (assumption 5).
- Assumptions: 4 confirmed, 5 dropped, 7 resolved as 1,500 characters with a counter above 1,300.
- Tool-phase status is keyed by real tool names: releases/tickets, deployments, documents, otherwise "Looking things up…".
- D-13-21: up to about 150 characters are held back per round. A discard drops held text silently; text already shown is replaced by the status line.

---

## Requirement-to-UI Traceability

| Requirement / Decision | UI Contract Element |
|---|---|
| ASST-02 | Same-origin cookie stream; no key in the browser (Interaction/Auth) |
| ASST-05 | Assistant page, drawer, scope chip/picker, starters, streaming turn with Stop |
| ASST-06 | Citation chip row, `[n]` markers, "How I answered" panel |
| ASST-07 | Refusal note, older-turns notice, scope-bound chat |
| ASST-08 | State banners OFFLINE/BUSY/MODEL_MISSING/DEADLINE/FAILED, index-last-updated line |
| SAFE-02 | Plain-text rendering rule, no Markdown/HTML, URL scheme check |
| D-13-01, D-13-04, D-13-05 | Nav item and drawer only; hidden entirely when disabled |
| D-13-02, D-13-03, D-13-13 | Scope chip, picker, new chat on scope change, separate page/drawer sessions |
| D-13-06 | Status line only during tool rounds, discarded provisional tokens |
| D-13-07, D-13-08 | Chips and collapsed trace panel |
| D-13-09, D-13-10, D-13-11 | Plain text, Stop and stopped marker, muted refusal |
| D-13-12, D-13-14, D-13-15 | In-memory chat, trimmed-history notice, config-driven starters with defaults |

---

## Assumptions recorded (not settled by CONTEXT or prior UI-SPECs)

1. Tailwind IS in use (correcting the brief). Contract is in Tailwind utilities.
2. Closing the drawer during a stream aborts it (marks "stopped"); click-outside does not close the drawer (differs from `PlaceholderDrawer` on purpose).
3. "New chat", scope change and drawer close need no confirmation, since nothing is saved. Scope stays on "New chat".
4. Citation link targets are built from ids on the server's citation record. CONFIRMED (2026-10-16): the ticket row anchor `#ticket-{key}` is added in 13-07 and the document viewer supports `?revision=`.
5. DROPPED (2026-10-16): the backend supplies no hrefs; chips are internal links only.
6. A refusal still shows "How I answered" if tools ran, and never shows chips.
7. RESOLVED (2026-10-16): composer limit is 1,500 characters with a counter above 1,300, matching the server validation.
8. No per-answer "AI may be wrong" disclaimer; the empty-state copy states sourcing.
9. Freshness uses the newest `lastIndexedAt` across collections; stale threshold is 24 hours.
10. Status-line labels per tool are a client-side fixed table keyed by tool name, with a generic fallback; final tool names come from Phase 12.
11. Starters: 3 per scope, defaults above; the status endpoint returns `enabled`, the two starter lists and freshness (name and shape are the planner's call).
12. Desktop-first internal tool; the only responsive treatment is the drawer bottom sheet below `lg`, as in `PlaceholderDrawer`.

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS
- [ ] Dimension 2 Visuals: PASS
- [ ] Dimension 3 Color: PASS
- [ ] Dimension 4 Typography: PASS
- [ ] Dimension 5 Spacing: PASS
- [ ] Dimension 6 Registry Safety: PASS

**Approval:** pending
