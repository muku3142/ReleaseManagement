# Phase 14: AI Document Assist - Context

**Gathered:** 2026-10-15
**Status:** Ready for /gsd-ai-integration-phase 14, then UI spec and planning

<domain>
## Phase Boundary

From a generated release note, runbook or approval document, the release manager can run Clean up, Summarize or a custom instruction through the local model. The result streams in beside the original. The release manager can edit it and save it as a new revision marked AI-assisted, with the instruction recorded; the original revision is never changed.

Requirements: ASST-09, ASST-10. The phase uses Phase 10's generic transform endpoint (RAG-11), with no retrieval.

</domain>

<decisions>
## Implementation Decisions

### Already locked
- The transform endpoint is Phase 10's `POST /v1/transform`. The caller owns the instruction text, and rag-service fences the document as data (D-10-09). D-10-10 returns 413 beyond `num_ctx`, and D-10-12 gives the same 120 s deadline and the same events as chat.
- Snapshots are append-only (D-05-06). `markdown` holds resolved Markdown, never HTML (D-05-05).
- The instructions live in config (D-13-19, user request). The browser goes through the cookie login and never holds the rag-service key (Phase 13 pattern).
- Residual risk: on the dev model, planted text in a document can steer the transform (golden T03, accepted for dev in 7f54818). The production model must pass T03 before v1.1 ships.

### Revision semantics
- **D-14-01:** A saved AI-assisted result is revision N+1 and **is current like any other revision**. The viewer, PDF/DOCX exports and the assistant index (latest revision only, D-12-11) all use it.
  - DOC-09 readiness and staleness stay tied to the ticket set: an AI revision of a stale document is still stale.
  - The revision carries an `aiAssisted` flag and the instruction used. This needs a migration.
- **D-14-02:** Regenerating from the template after an AI revision **appends a new revision on top** as usual, and the AI edits are not carried over. History shows both. The regenerate action shows a notice: "the latest revision was AI-assisted; regenerating replaces it as current".
- **D-14-03 (superseded by D-14-13):** A **Summarize result is view, copy or download only, never saved** as a revision. This stops a summary from replacing the full document in exports and the index. Only Clean up and custom-instruction results can be saved.
- **D-14-13 (2026-10-18, reverses D-14-03):** A complete **Summarize result can be edited and saved as an AI-assisted revision N+1**, the same way as Clean up. It records ai_mode SUMMARIZE and the configured summarize instruction. Like any AI revision it becomes current (D-14-01), so exports and the assistant index use the summary until the user regenerates or saves another revision. This is the user's accepted trade-off.

### Compare and edit UX
- **D-14-04:** The layout is **side by side** in the document viewer: the original on the left and the result streaming in on the right. On narrow screens they stack.
- **D-14-05:** The result is edited as **raw Markdown in a textarea with a Preview toggle**, rendered by the existing markdown-it viewer. There is no new editor dependency.
- **D-14-06:** AI-assisted revisions are marked by an **"AI-assisted" badge plus the instruction in revision history**, and a **banner in the viewer**. **PDF/DOCX exports are not stamped**, because they go to approvers as the document.

### Modes and prompts
- **D-14-07:** **Prompts are per mode and per document type:** Clean up and Summarize, each for release notes, runbook and approval. They are config keys under `app.assistant.*` (or a sibling block) in both `application.yml` files, overridable by env, following D-13-17/19.
- **D-14-08:** **Only the document text is sent to the model.** There are no ticket summaries, commit messages or Jira fields, which keeps prompts small at 4096 and limits injection surface. The earlier idea of feeding implementation notes and commits stays deferred.
- **D-14-09:** A custom instruction is **free text**. The instruction is saved with the revision (ASST-10). The last 5 used are offered in a dropdown, held **in memory only** until reload, consistent with D-13-12 (no browser storage).

### Size and fact safety
- **D-14-10:** A document that is too long gets **a clear message, and there is no chunking.** An estimate disables the buttons before sending, and a 413 from rag-service shows the same message ("too long for the AI model on this server, about N of M characters"). A bigger production model raises the limit through config.
- **D-14-11:** **Fact check: a ticket-key and version-string diff that warns.** Before Save, any ticket keys or version strings that are missing from or new in the result are listed above the Save button. The user can still save after seeing the warning.
- **D-14-12:** Runbook and approval results get a **"verify steps before use" banner** above the result and on the saved AI revision in the viewer. It does not appear in exports.
- Discard stores nothing. When the service is offline, the user sees a clear message and document generation is unaffected (SC3), using Phase 13's outcome states.

### Claude's Discretion
- Exact prompt wording per mode and type, and key names.
- Revision schema: flag and instruction columns, and the migration number.
- How the stream reaches the browser: reuse Phase 13's streaming endpoint pattern.
- How "about N characters" is estimated client-side vs server-side.
- The fixed document set for SC4 (a small fixture of real-shaped documents checked by the ticket-key diff).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

- `.planning/ROADMAP.md` § Phase 14: goal, success criteria, research flags (prompt per mode, long docs vs num_ctx, revision history/exports/readiness, DOC-09 currency)
- `.planning/REQUIREMENTS.md`: ASST-09, ASST-10, DOC-07, DOC-09
- `.planning/phases/10-rag-chat-citations/10-CONTEXT.md`: D-10-09, D-10-10, D-10-12 (transform contract)
- `.planning/phases/10-rag-chat-citations/10-AI-SPEC.md`: transform prompt, fences, golden T01-T03
- `.planning/phases/10-rag-chat-citations/10-09-SUMMARY.md`: dev model transform numbers and T03 residual risk
- `.planning/phases/13-assistant-screen-quality-gate/13-CONTEXT.md`: D-13-12 (no browser storage), D-13-17..20 (prompts as config), streaming and outcome states
- `.planning/phases/12-releasemanagement-as-first-client/12-CONTEXT.md`: D-12-11 (index uses the latest revision)
- `src/main/java/com/evision/releasemgmt/domain/document/DocumentSnapshot.java`: the append-only snapshot and content-type rule
- `rag-service/src/main/java/com/evision/ragservice/application/TransformService.java`: transform limits and token budget

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `DocumentGenerationService`: appends revisions. An AI save is a sibling path that writes a snapshot with copied template fields and the new flag.
- `DocumentReadinessService`: the single staleness comparison (DOC-09), unchanged by D-14-01.
- `DocumentExportService` with the PDF/DOCX adapters: already export the latest revision; no stamping (D-14-06).
- `frontend/src/routes/DocumentViewerRoute.tsx` and `components/RenderedDocument.tsx` (markdown-it): host for the side-by-side view and the preview.
- The Phase 12 RAG client adapter and the Phase 13 browser streaming endpoint: the transform call and SSE relay should follow the same shape.

### Established Patterns
- Snapshots copy template fields at write time (D-05-05). An AI revision copies the source revision's template fields and chrome.
- Config goes in both main and test `application.yml` (the test one shadows main).

### Integration Points
- Revision history UI (`DocumentsSection.tsx`) for the badge, the viewer for the banners, and the release page's regenerate action for the D-14-02 notice.

</code_context>

<specifics>
## Specific Ideas

- Too-long message: "This document is too long for the AI model on this server (about N of M characters)."
- Regenerate notice: "The latest revision was AI-assisted; regenerating replaces it as current."

</specifics>

<deferred>
## Deferred Ideas

- Feeding ticket summaries, commit messages or a Jira "implementation notes" field into the prompts (D-14-08). "Implementation notes" is still undefined.
- Chunked transforms for long documents (D-14-10).
- Export stamping of AI-assisted revisions (D-14-06).

</deferred>

---

*Phase: 14-ai-document-assist*
*Context gathered: 2026-10-15*
