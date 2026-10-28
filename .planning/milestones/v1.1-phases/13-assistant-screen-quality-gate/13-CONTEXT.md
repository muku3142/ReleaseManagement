# Phase 13: Assistant Screen & Quality Gate - Context

**Gathered:** 2026-10-15
**Status:** Ready for planning

<domain>
## Phase Boundary

The release manager asks questions on an Assistant page, or from a release page, and watches a plain-text answer stream in. Each answer shows citation chips and a "how I answered" trace. The browser talks only to ReleaseManagement behind the existing cookie login and never holds the rag-service key.

The phase also builds a 30-50 question quality gate that is run through the API. v1.1 cannot ship until that gate passes on the production host.

Requirements: ASST-02, ASST-05, ASST-06, ASST-07, ASST-08, SAFE-04. The phase also makes the prompts configurable (D-13-17..20, added by the user during discussion).

</domain>

<decisions>
## Implementation Decisions

### Already locked (earlier phases, ROADMAP scope notes)
- Nothing is saved on the server: no saved conversations, no feedback thumbs and no admin re-index screen (v1.1 Out of Scope).
- The browser goes through ReleaseManagement's cookie login and never holds the rag-service client key (ASST-02, D-09-01). CSRF protection relies on SameSite=Strict (locked decision).
- "This release" is an exact scalar `release_id` filter (D-12-01, D-12-11). Tools take a `release` argument that the model has to name, so put the release name into the question text (12-RESEARCH handoff).
- Phase 12's orchestrator already provides what the screen needs:
  - an outcome enum (OFFLINE, BUSY(429), MODEL_MISSING(503), DEADLINE, FAILED);
  - a listener with `token()` and `roundDiscarded()`;
  - a record of which tools ran and which sources were used.

  Phase 13 adds the browser-facing streaming endpoint and the UI on top. It does not rebuild them.
- `app.assistant.enabled` defaults to OFF (D-12-09, D-12-16).
- Answers are plain text (SAFE-02). A stronger production model must pass golden T03 before v1.1 ships (7f54818). The dev model `qwen2.5-coder:7b` prints tool calls as text, so tool flows cannot be proven live on this dev machine.

### Screen placement and scope
- **D-13-01:** There is an **Assistant page in the nav** for open questions. **The release detail page gets a right-hand drawer** for "Ask about this release", so the release stays in view.
- **D-13-02:** Scope is shown as a **removable chip** above the input (for example "Release: 4.2.0 x"). Removing the chip makes the chat unscoped. The Assistant page has a release picker for adding a scope.
- **D-13-03:** **Changing the scope starts a new chat**, so a follow-up never mixes evidence from two releases.
- **D-13-04:** The only entry points are the nav page and the release drawer.
- **D-13-05:** When `app.assistant.enabled` is false, the UI **hides the assistant entirely**: no nav item and no release button. The frontend needs a way to know the flag, for example a small config/status endpoint.

### Answer streaming UX
- **D-13-06:** During tool rounds the user sees **only a status line**, such as "Checking deployments..." or "Searching documents...". Text streams only for the final answer, so provisional tokens from a round that becomes a tool call are never shown.
- **D-13-07:** Citations are **inline `[n]` markers plus a chip row** under the answer, for example "[1] Release notes 4.2.0 rev 3". Each chip links to the cited release, ticket, document revision or commit.
- **D-13-08:** "How I answered" is **collapsed under each answer**. Expanded, it shows the tools that ran with their arguments, and the sources used.
- **D-13-09:** Answers render as **plain text with line breaks kept**. No Markdown, and never `rehype-raw` or similar.
- **D-13-10:** A **Stop button** aborts the fetch. The abort propagates to rag-service (outcome CLIENT_GONE) and frees the one-in-flight slot. The partial answer stays, marked "stopped".
- **D-13-11:** A refusal (NO_ANSWER) is a **styled, muted note without chips**, visually distinct from an error.

### Session lifetime
- **D-13-12:** A chat lives **in memory until reload**. It survives navigation inside the app. A reload, logout or "New chat" clears it. Nothing goes to the server or to browser storage (no sessionStorage or localStorage for chat text).
- **D-13-13:** The **page chat and the drawer chat are separate**. Opening the drawer for a different release starts fresh, following D-13-03.
- **D-13-14:** Follow-ups send **all turns of the session**, and rag-service trims them to its history budget (D-10-08). Once trimming kicks in, the UI shows "older turns not used".
- **D-13-15:** Starter questions are a **fixed list per scope**: one set for the unscoped chat and one for a release, with the release name filled in. The lists come from config (D-13-17).
- ASST-08 states: offline, busy and model-missing get distinct UI states mapped from the Phase 12 outcome enum, and the screen shows when the index was last updated. rag-service exposes `lastIndexedAt` per collection.

### Quality gate and model (SAFE-04)
- **D-13-16: Split gate.** Phase 13 builds the 30-50 question set and a runnable gate (one command that writes a report file), runs it on dev, and records the dev numbers. **Running the gate on the production host is an explicit v1.1 ship blocker**, recorded in STATE and ROADMAP. Phase 13 can close without it, but v1.1 cannot ship. The production run also covers T03.
- **Authoring:** Claude drafts the questions and the user approves the labels at a checkpoint, as in Phase 10's golden set. Categories:
  - exact facts via tools;
  - free text via retrieval;
  - release-scoped questions;
  - follow-ups;
  - not-in-the-data;
  - injection;
  - scope leak (a scoped question must not cite another release).
- **Data:** a **fixed, reproducible seed dataset** of releases, tickets, documents and code links, loaded into a scratch instance, so results compare across models and hosts. The gate does not use live dev data.
- **Pass bar:** **every safety case passes 3/3**, and **at least 80% of quality runs pass**. The report records the model, the host, and the active prompt hash (D-13-18).
- Because the dev model cannot do tool calls, tool-dependent cases are expected to fail on dev. The dev run records them as such; this is not a reason to weaken the cases.

### Prompts as configuration (added by the user during discussion)
- **D-13-17:** ReleaseManagement config keys under `app.assistant.*` in the main `application.yml` (and the test `application.yml`, which shadows it), each overridable by environment variable:
  - the starter question lists, unscoped and per release;
  - the tool-use instruction and scoped-question text (the release-name injection);

  Phase 12 executes unchanged; Phase 13 moves its tool-use text into config.
- **D-13-18:** The rag-service system prompts become configurable as **rule text only** under `rag.prompts.*` in rag-service's `application.yml`, with today's `Prompts.java` text as the defaults. This covers the grounding, citation, refusal and transform wording.
  - **The data fences that wrap retrieved and transformed text stay in code**, so a config edit cannot turn document text into instructions (SAFE-02).
  - The active prompt hash, alongside or replacing `PROMPT_VERSION`, goes into the generation log metadata and the gate report.
- **D-13-19:** The Phase 14 document-assist instructions (Clean up, Summarize and the per-document-type prompts) are also config, as `app.assistant.*` or a sibling block. **This is recorded for Phase 14**, which owns them; Phase 13 does not build them.
- **D-13-20:** The project uses `application.yml`, not `.properties`. "application.properties" in the user's request means the yml files.

### Claude's Discretion
- How the browser-facing stream is carried: SSE over POST with fetch plus `AbortController`, Spring MVC async/`SseEmitter`, auth on async dispatch, and no proxy buffering. These are ROADMAP research flags.
- How the frontend learns `enabled`, index freshness and the status-line labels per tool.
- The drawer component (there is no drawer pattern today; `ConfirmDialog` and `PlaceholderDrawer` are the nearest), the chip styling and the starter question wording.
- The gate harness shape: the main-app API with a seeded scratch instance, the report format, and how it reuses Phase 10's golden harness ideas.
- The `rag.prompts.*` key names, and validation at startup (for example, blank rule text fails fast).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Scope and requirements
- `.planning/ROADMAP.md` § Phase 13: goal, success criteria, research flags, scope notes
- `.planning/REQUIREMENTS.md`: ASST-02, ASST-05..08, SAFE-02, SAFE-04
- `.planning/research/v1.1/`: milestone research (model, hardware, injection)

### Upstream phase contracts
- `.planning/phases/12-releasemanagement-as-first-client/12-CONTEXT.md`: D-12-01..16 (collections, ids, `app.assistant.*`, tools)
- `.planning/phases/12-releasemanagement-as-first-client/12-RESEARCH.md`: orchestrator listener, outcome enum, Phase 13 handoff notes, chip linking by `release_id`/`ticket_key`/`doc_type`+`revision`
- `.planning/phases/11-tool-call-protocol/11-CONTEXT.md`: tool loop protocol, provisional tokens (D-11-01..12)
- `.planning/phases/10-rag-chat-citations/10-CONTEXT.md`: D-10-01..12 (429, deadline, history trimming, refusal)
- `.planning/phases/10-rag-chat-citations/10-AI-SPEC.md`: citation format, NO_ANSWER, golden fixture approach
- `.planning/phases/10-rag-chat-citations/10-09-SUMMARY.md`: dev model numbers (qwen2.5-coder:7b), accepted T03 risk
- `.planning/debug/resolved/transform-deadline-qwen3.md`: why qwen3:4b was rejected on dev

### Code
- `rag-service/src/main/java/com/evision/ragservice/application/Prompts.java`: prompt text and fences (D-13-18)
- `rag-service/src/test/resources/golden/`: Phase 10 golden fixture and seed format
- `rag-service/openapi.json`: rag-service `/v1` contract
- `docs/docker.md` § rag-service: operations, accepted risks (chat)

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `frontend/src/components/AppShell.tsx`: nav where the Assistant item goes, hidden when disabled.
- `frontend/src/routes/ReleaseDetailRoute.tsx`: host for the "Ask about this release" button and drawer.
- `frontend/src/components/PlaceholderDrawer.tsx`, `ConfirmDialog.tsx`: closest existing overlay patterns for the drawer.
- `frontend/src/api/`: existing API client layer (TanStack Query). Streaming needs a fetch/ReadableStream reader; none exists yet.
- Phase 12 orchestrator and `RagChatPort` (main app): tool loop, outcome mapping, tools/sources record.

### Established Patterns
- Frontend: React + TanStack Router/Query, Tailwind v4 utilities (no shadcn or component library; see 13-UI-SPEC), `markdown-it` present but not used for answers (D-13-09).
- Backend: hexagonal ports/adapters, config in both main and test `application.yml` (the test one shadows main; see the env landmines memory).
- rag-service is a standalone pom and a domain-free service; caller-owned instructions are fenced as data (D-10-09).

### Integration Points
- A new authenticated main-app endpoint streams the orchestrator's events to the browser.
- rag-service `GET /v1/collections/{c}` returns `lastIndexedAt` for the "index last updated" display.
- rag-service `application.yml` gets a `rag.prompts.*` block, and the main app gets `app.assistant.*` prompt and starter keys.

</code_context>

<specifics>
## Specific Ideas

- Example starters: unscoped "What shipped to Production last week?"; scoped "Is {release} ready for Production?"
- Example chip label: "[1] Release notes 4.2.0 rev 3".
- Example status lines: "Checking deployments...", "Searching documents...".

</specifics>

<deferred>
## Deferred Ideas

- Document-assist prompts as config (D-13-19): Phase 14.
- More assistant entry points (dashboard, ticket rows): later.
- Pulling a tool-capable model for dev (for example `qwen3:4b-instruct-2507`): not chosen. The gate splits instead (D-13-16).

</deferred>

---

*Phase: 13-assistant-screen-quality-gate*
*Context gathered: 2026-10-15*
