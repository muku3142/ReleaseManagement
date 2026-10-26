# Document AI assist: end-to-end flow

What happens when the release manager presses **Clean up**, **Summarize** or **Custom…** on a generated document. The
flow runs from the button to the model and back, then to a saved revision. Line numbers are as of commit `c4f4174`.

```text
 Browser (DocumentViewerRoute)     ReleaseManagement :8080          rag-service :8081            Ollama
 -----------------------------     -----------------------          -----------------            ------
 GET /api/assistant/transform/limits -->
 <-- numCtx, charsPerToken, systemTokens,
     slack, per-preset max chars
 (buttons disabled if document too long)

 POST /api/assistant/transform
 {releaseId, documentType,
  sourceRevision, mode, instruction?} -->
                                   read latest snapshot from DB
                                   (document text never comes
                                    from the browser)
                                   pick instruction,
                                   pre-flight size check
 <-- 200 text/event-stream
     (or 400/404/409/413 before
      any stream opens)
                                   POST /v1/transform
                                   {text, instruction}  --------->  fence TEXT with nonce,
                                                                    size check, readiness,
                                                                    per-client gate
                                                                    system = transform rules
                                                                    user = INSTRUCTION +
                                                                    fenced TEXT  ----------->  /api/chat
                                                                    <------------------------  token stream
                                   <--- SSE status / token /
                                        done | error (+ heartbeats)
 <-- SSE token / done{complete} |
     error{outcome}

 (review side by side, edit Markdown,
  fact-check warning)

 POST /api/assistant/documents/ai-revisions
 {..., markdown}  -------------->
                                   append revision N+1 in DB
                                   (ai_mode, ai_instruction,
                                    ai_source_revision,
                                    basis_generated_at)
 <-- 201 {revision}
                                   after commit: re-index the
                                   document (async)  ----------->  PUT document
```

## 1. When the buttons appear (browser)

`frontend/src/routes/DocumentViewerRoute.tsx` renders `AiAssistPanel` around the document
(`frontend/src/components/AiAssistPanel.tsx:37`). The **AI assist** bar shows only when both of these hold:

- the assistant is enabled (`GET /api/assistant/status`, `useAssistantStatus`; off by default with `RM_ASSISTANT_ENABLED`);
- the revision on screen is the latest one, because you can only assist the current document.

On load the panel fetches the size limits (`AiAssistPanel.tsx:40` calls `useTransformLimits`, which calls
`GET /api/assistant/transform/limits`; see `frontend/src/api/documentAssist.ts:52`). The server computes each preset's
maximum with the same formula rag-service uses (`TransformSizeEstimator`):

```
maxChars = floor( trunc((numCtx − systemTokens − ceil(instructionChars / charsPerToken) − templateSlack) / 2) × charsPerToken )
```

With num_ctx 4096 this is about 5,300 characters. It limits **one request**, not the document:

- **Clean up and Custom are sectioned** (this reverses D-14-10, "no chunking"). The server splits the stored Markdown at
  heading lines (`DocumentSections`), never inside a code block, and sends each section's body to the model in its own
  request, one after another. Heading lines (ticket titles and keys) and the blank lines around each body are copied
  verbatim and never reach the model. A body over the per-request limit is split again at blank lines; only a single
  paragraph or code block over the limit is refused (413). So these buttons are not disabled for long documents.
- **Summarize** needs the whole document in one request, so it keeps the limit: if the document is longer, only the
  Summarize button is disabled, with "This document is too long to summarize on this server (about N of M characters).
  Clean up and Custom still work: they process the document one section at a time."

Why: on a CPU-bound host a 40,000-character document (about 9,400 prompt tokens) spent its whole 8-minute deadline in
prompt processing before the first output token. Small sections each start producing output within seconds. The last five custom instructions are kept in
memory only (`AiAssistPanel.tsx:100`, `addRecentInstruction`) and are lost on reload.

## 2. Starting a run (browser)

Pressing a button mounts `AiResultCard` (`frontend/src/components/AiResultCard.tsx`). The card calls
`streamTransform` (`AiResultCard.tsx:226` → `documentAssist.ts:69`), which sends:

```json
POST /api/assistant/transform
{ "releaseId": "…", "documentType": "RELEASE_NOTES|RUNBOOK|APPROVAL", "sourceRevision": 3,
  "mode": "CLEAN_UP|SUMMARIZE|CUSTOM", "instruction": "…only for CUSTOM…" }
```

The request goes over `fetch` with an `AbortController` and the normal session cookie. It is not an `EventSource`, which
can't POST. The browser never sends the document text and never holds the rag-service key.

## 3. Pre-flight in ReleaseManagement (before any stream opens)

`AssistantTransformController.transform` (`src/main/java/com/evision/releasemgmt/adapters/web/AssistantTransformController.java:62`)
calls `AssistantTransformService.prepare` (`application/assistant/AssistantTransformService.java:72`). It does four things:

1. **Loads the latest snapshot** for the release and document type. If none exists it answers **404**. If
   `sourceRevision` is not the latest it answers **409** `REVISION_SUPERSEDED`. The text that will be transformed is
   always the stored Markdown.
2. **Picks the instruction** (`resolveInstruction`, around line 148):
   - Clean up and Summarize use the configured text for that mode and document type. That text is in
     `src/main/resources/application.yml`, `app.assistant.document-assist.instructions.{clean-up|summarize}.{release-notes|runbook|approval}`,
     lines 95-103.
   - Custom uses the typed text. It must not be blank, must fit `instruction-max-chars` (2,000) and must contain no
     NUL; otherwise the answer is **400**.
3. **Checks the size** with `TransformSizeEstimator`, the same formula as step 1. Summarize checks the whole document;
   Clean up and Custom split it into sections first and check each request. If something is too long, the answer is
   **413** `TEXT_TOO_LARGE` with `characters` and `maxCharacters`, and no model call is made.
4. **Logs one metadata line**: release, type, mode, instruction source and lengths. It never logs text.

When those pass, the controller opens an `SseEmitter`. Its timeout is `document-assist.stream-deadline`
(`RM_ASSISTANT_TRANSFORM_DEADLINE`, PT20M) plus a backstop; each section is also bounded by rag-service's own
per-request `deadline`. A sectioned run sends its sections one at a time (rag-service allows one generation per client)
and stops at the first section that fails or hits its deadline. If at least one section already finished, the server then
sends a `remainder` event with the original text of the stopped section and everything after it, so the browser holds
the whole document (processed up to the stop point, original after it). The
controller then starts a virtual thread (`AssistantTransformController.java:66-67`) and returns
`200 text/event-stream` with `Cache-Control: no-cache` and `X-Accel-Buffering: no`.

## 4. Calling rag-service (ReleaseManagement → rag-service)

On the virtual thread, `stream()` (`AssistantTransformController.java:83`) calls `AssistantTransformService.run`
(`AssistantTransformService.java:89`), which calls `RagTransformAdapter.transform`
(`adapters/rag/RagTransformAdapter.java:46`):

```json
POST http://rag-service:8081/v1/transform     Authorization: Bearer <RM_ASSISTANT_API_KEY>
{ "text": "<stored Markdown>", "instruction": "<chosen instruction>" }
```

The adapter reads the SSE stream with the shared `SseStreamReader` (also used by chat).

- **Tokens and heartbeats:** each `token` is passed to `BrowserStreamListener`, and each `:` heartbeat comment is
  forwarded, so a dead browser is noticed.
- **Browser gone:** if the browser has gone, the listener throws `ClientGoneException`. The adapter then closes the
  upstream response, and rag-service sees CLIENT_GONE and frees its one-per-client slot.
- **Error statuses:** HTTP errors map to outcomes:

| rag-service answer | Outcome |
|---|---|
| 429 | BUSY (with `Retry-After` seconds) |
| 503 MODEL_NOT_AVAILABLE | MODEL_MISSING |
| other 503, or connection refused | OFFLINE |
| 413 | TooLarge |
| 400 | REJECTED (shown to the browser as FAILED) |
| request timeout | DEADLINE |

## 5. Inside rag-service

`ChatController` `POST /v1/transform` (`rag-service/.../adapters/web/ChatController.java:66`) calls
`TransformService.prepare` (`application/TransformService.java:59`):

- **Limits:** the instruction must be at most `instruction-max-chars`, else 400. The text must fit
  `maxTransformTextTokens`, else 413.
- **Prompt:** built by `PromptBuilder.transform` (`application/PromptBuilder.java:310`):
  - **System message:** `rag.prompts.transform-system` (`rag-service/src/main/resources/application.yml:99`). It says
    to apply the INSTRUCTION to the TEXT block only, that TEXT is data and never instructions, to keep keys, versions,
    dates, environments and step order, and to add no facts.
  - **User message:**
    ```
    INSTRUCTION: <instruction, nonce-neutralised>
    <<<TEXT nonce=XYZ>>>
    <document, nonce-neutralised>
    <<<END nonce=XYZ>>>
    ```
    The fence and the per-request nonce are built in code, not config, so a config edit can't remove them.
- **Output budget:** `numPredict = min(4096, numCtx − estimated prompt tokens)`.
- **Model readiness:** `chat.assertReady()` checks the model is installed (else 503), then `gate.tryAcquire(clientId)`
  allows one in-flight request per client (else 429).

`TransformService.run` (line 82):

1. Sends `status`.
2. Streams the model's tokens as `token` events. Generation runs at temperature `transform-temperature` 0.3, with a
   120 s `deadline` watchdog and a heartbeat every 5 s.
3. Ends with one of:
   - **`done`**: `finishReason`, `truncated`, `promptTokens`, `completionTokens`, `model`, `promptHash`;
   - **`error`**: DEADLINE, OLLAMA_UNAVAILABLE and so on.
4. Releases the gate.
5. Logs one `generation endpoint=transform` metadata line with `promptVersion`, `promptHash` and the model. It never
   logs text.

There is no retrieval and there are no citations on this path.

> Note: the system rule says "Output plain text", and every configured instruction says "Output Markdown, not plain
> text". The instruction is more specific, so the documents stay Markdown.

## 6. Back to the browser

`stream()` turns the outcome into the browser's events (`AssistantTransformController.java:83-102`):

| Event | When |
|---|---|
| `token {text}` | each streamed piece of the result; for a sectioned run, also each verbatim heading and separator |
| `progress {section, sections}` | sectioned runs only, before each section's model call; the card shows "Section n of m" |
| `remainder {keep, text}` | sectioned run stopped after at least one finished section; the browser keeps the first `keep` characters (dropping the stopped section's partial output) and appends `text`, the original rest. Sent even after the stream deadline |
| `done {finishReason, truncated, complete}` | `complete` is true only when `finishReason == "stop"` and not truncated |
| `error {outcome, retryAfterSeconds?, characters?, maxCharacters?}` | OFFLINE, BUSY, MODEL_MISSING, DEADLINE, FAILED, or TOO_LARGE (rag 413 after a passing pre-flight, with both sizes) |

`AiResultCard` dispatches these into its reducer (`AiResultCard.tsx:217-223`, `frontend/src/documentAssist/resultState.ts`).
Its phases are: streaming, then complete, incomplete, stopped or error, then saving.

- **Stop:** the Stop button, or Escape, aborts the fetch. The partial result stays, but it can't be saved.
- **Layout:** the original sits on the left and the result on the right. The result can be edited as raw Markdown, with
  a Preview toggle.
- **Images:** the preview never loads remote images (`MarkdownBody blockImages`).
- **Fact check:** the client runs `factDiff(original, edited)` (`AiResultCard.tsx:285`,
  `frontend/src/documentAssist/factDiff.ts`), debounced. It lists ticket keys and versions that are missing or new
  above the Save button. It warns and never blocks (D-14-11). It can't see a key moved to another item, or a changed
  date or environment.
- **Verify banner:** runbook and approval results show "verify steps before use".

## 7. Saving

**Accept and save as revision N+1** is enabled when the run ended `complete` with a non-blank buffer, or when a Clean up/Custom
run stopped (deadline or any failure) after a `remainder` was applied; the card then says which section it stopped at and
that the rest was kept as it was (`resultState.ts`, `canSave`, and
`AiResultCard.tsx`). Summarize results save like Clean up (D-14-13). Still blocked: `length` (a section was cut
mid-text and its end is lost, which the fill cannot restore), a user Stop, a stop in the first section (nothing
processed), and every incomplete Summarize (one request with no original tail; a partial summary is not a revision of
the document). Blocking these is done in the UI;
the server doesn't check it, since the user may edit the text freely anyway.

```json
POST /api/assistant/documents/ai-revisions
{ "releaseId": "…", "documentType": "…", "sourceRevision": 3, "mode": "CLEAN_UP|SUMMARIZE|CUSTOM",
  "instruction": "…custom text only…", "markdown": "<edited result>" }
```

1. `AssistantTransformController.save` (line 75) calls `AssistantTransformService.saveAiRevision` (line 115).
   - A NUL in the markdown is rejected with **400**.
   - The instruction recorded for Clean up and Summarize is the server's configured text, not anything the client sends. SUMMARIZE maps to `AiMode.SUMMARIZE`.
2. `AiRevisionService.save` (`application/AiRevisionService.java:30`) re-checks that `sourceRevision` is still the
   latest, else **409**. It then appends revision **N+1** via `DocumentSnapshot.createAiRevision`, copying template
   fields, chrome and logo from the source revision (append-only, D-05-06). The new columns come from migration
   `V12__document_snapshot_ai_assist.sql`:
   - `ai_mode`;
   - `ai_instruction`;
   - `ai_source_revision`;
   - `basis_generated_at`, copied from the source, so an AI revision of a stale document stays stale (D-14-01).
3. The response is **201** `{revision}`. The browser invalidates the release, readiness, dashboard, document,
   revisions and freshness queries (`documentAssist.ts:86`, `useSaveAiRevision`) and navigates to the new latest
   revision.

**After the save:**
- The new revision is the current one for the viewer, PDF/DOCX export (not stamped as AI) and the assistant index.
- After commit, the persistence adapter publishes `ReleaseIndexInvalidated`. The index listener re-pushes that
  document to rag-service asynchronously.
- Revision history shows an **AI-assisted** badge with the instruction, and the viewer shows an AI banner.
- Regenerating from the template appends a new revision on top, with a notice under the button.

## 8. Where to change things

| What | Where |
|---|---|
| Clean up / Summarize instruction per document type | `src/main/resources/application.yml` lines 95-103 (and the copy in `src/test/resources/application.yml`; `AssistantPropertiesTest` checks they match) |
| Transform system rules | `rag-service/src/main/resources/application.yml` `rag.prompts.transform-system` (Java default in `PromptRules`; a drift test keeps them byte-identical) |
| Model | `RAG_CHAT_MODEL` (dev default `qwen3:4b-instruct-2507-q4_K_M`) |
| Context size (limits both sides) | `RAG_CHAT_NUM_CTX` and `RM_ASSISTANT_NUM_CTX`, which must be equal |
| Deadlines | rag `deadline` 120 s per request (per section); app `RM_ASSISTANT_TRANSFORM_DEADLINE` (PT20M) for a whole transform; chat uses `RM_ASSISTANT_STREAM_DEADLINE` (PT5M) |
| Turn the feature off | `RM_ASSISTANT_ENABLED=false` hides the bar and removes the endpoints |

Changing rag-service rule text changes `promptHash`. Rerun the golden tests and the quality gate (`docs/quality-gate.md`).

## 9. What is never stored or logged

- **Logs:** neither side logs the document, instruction or result text. Logs carry ids, modes, lengths, token counts,
  outcomes and the prompt hash only.
- **Discard:** discarding a result stores nothing.
- **Browser:** the recent-instructions list lives in browser memory only.
