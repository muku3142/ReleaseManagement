# RAG pipeline: end-to-end flow

How the assistant's retrieval-augmented chat works, from a ticket change in ReleaseManagement to an answer with
citations in the browser. It is written for a developer who has to change it. Line numbers are as of the working tree
on 2026-10-18 (`src/main/resources/application.yml` had uncommitted edits at that time). Document transforms are not
covered here; see `docs/document-assist-flow.md`.

**Write path (indexing)**

```text
ReleaseManagement :8080          rag-service :8081            Ollama
=======================          =================            ======

DB transaction commits
  |
  v
ReleaseIndexInvalidated
  |
  v
IndexInvalidationListener
(coalesces per release)
  |
  v
IndexSyncer
reads release + latest
snapshots, builds docs
  |
  | PUT /v1/collections/
  |     {c}/documents/{id}
  +----------------------------> upsert document
                                 compare content hash
                                 changed -> PENDING
  <---------------------------- 202 Accepted

                                 IndexWorker (every 2 s)
                                 1. claim PENDING (lease)
                                 2. Chunker -> chunks
                                 3. embed chunks ---------> /api/embed
                                 4. store vectors,
                                    status INDEXED
```

**Read path (a question)**

```text
Browser          ReleaseManagement      rag-service          Ollama
=======          =================      ===========          ======

POST /api/assistant/chat
  +------------> POST /v1/chat
                   +-----------------> embed question -----> /api/embed
                                       top-k search
                                       (release_id filter)

                 [first round, tools offered]
                                       routing prompt -----> /api/chat
                 <-------------------- tool_call (signed)
                 run tools in-process
                 resend turn + results
                   +----------------->

                 [answer round]
                                       rules + history +
                                       SRC blocks -------->  /api/chat
                                       <-------------------- tokens
                 <-------------------- SSE token/citations/
                                       done | error
<--------------- SSE token / status /
                 done | error
```

## 1. Architecture

Two Spring Boot apps, two databases on one Postgres container:

| | rag-service | ReleaseManagement |
|---|---|---|
| Role | Domain-free retrieval and generation service | First client of rag-service |
| Port | 8081 (`rag-service/src/main/resources/application.yml:14`) | 8080 |
| Database | `ragdb`, role `rag` (`application.yml:5-7`; `RAG_DB_URL` default) | `releasemgmt` |
| Knows about releases, tickets | No. Only collections, documents, metadata | Yes |
| Outbound calls | Ollama only | Jira, Azure DevOps, rag-service |

- **Database.** The compose image is `pgvector/pgvector:0.8.7-pg18-trixie` (`compose.app.yaml:14`, `compose.yaml:3`).
  `ragdb` uses the `vector` extension (`V1__rag_schema.sql:2`). The `rag` role cannot connect to `releasemgmt`, but
  `releasemgmt` is the superuser and can read `ragdb` (`docs/docker.md`, "Isolation"; D-09-13).
- **Per-client API keys.** Each client app is registered by the admin API and gets one key, shown once
  (`ClientService.register`, `ClientService.java:25`). Only the SHA-256 hash is stored (`ApiKeys.hash`,
  `domain/ApiKeys.java:24`; table `client_app`, `V1__rag_schema.sql:4-11`). A client sees only its own collections
  (`CollectionService.require`, `CollectionService.java:53`; D-09-03). Keys do not expire; they are rotated or revoked
  (`AdminController.java:35,40`; D-09-04).
- **Admin key.** `RAG_ADMIN_KEY` (`rag.admin-key`, `application.yml:28`) must be at least 32 characters or the service
  will not start (`ApiKeyFilter.java:45-47`). It is compared in constant time and only opens `/v1/admin/**`
  (`ApiKeyFilter.java:68-72`). Client keys open everything else except `/actuator/**` and the OpenAPI/Swagger paths
  (`ApiKeyFilter.java:34-36`). Both need `Authorization: Bearer <key>`; a miss is 401 `INVALID_API_KEY`
  (`ApiKeyFilter.java:96-105`).
- **Tool-turn secret.** `RAG_TOOL_SIGNING_SECRET` (`rag.tools.signing-secret`, `application.yml:80`) signs tool calls
  (section 4.5).
- **Ollama is the only outbound call.** Base URL `RAG_OLLAMA_BASE_URL` (`application.yml:30`). It does two jobs:
  - embeddings with `nomic-embed-text` (`/api/embed`, `OllamaEmbeddingAdapter.java:76-79`);
  - chat with `qwen3:4b-instruct-2507-q4_K_M` (`rag.chat.model`, `application.yml:58`; `/api/chat`,
    `OllamaChatAdapter.java:112`). Callers cannot choose the chat model (D-10-07).
- **ReleaseManagement to rag-service.** ReleaseManagement sends its client key (`RM_ASSISTANT_API_KEY`,
  `app.assistant.api-key`, `application.yml:51`) on three REST clients: `ragIndexRestClient`, `ragChatRestClient`
  and the transform client (`config/RagRestClientConfig.java:19,27`). The key is required when the feature is on
  (`AssistantProperties.java:82-85`).
- **No callbacks.** rag-service never calls ReleaseManagement. When the model wants a tool, rag-service ends the
  stream with a signed `tool_call` event and the caller runs it (section 4.5, `docs/docker.md` "RAG-08").

## 2. What gets indexed and when (ReleaseManagement)

### 2.1 Collections and ids

`IndexCollection` (`domain/assistant/IndexCollection.java:4-7`) defines four collections. All use the embedding model
`app.assistant.embedding-model` (`nomic-embed-text`, `application.yml:52`; `RagIndexAdapter.java:40-45`).

| Collection | Document id | One per | Built by |
|---|---|---|---|
| `rm-tickets` | `ticket:{releaseId}:{ticketKey}` | ticket in a release | `IndexDocumentMapper.ticketDocument` (`:44`) |
| `rm-code-links` | `codelinks:{releaseId}:{ticketKey}` | ticket with PRs or commits | `codeLinksDocument` (`:74`) |
| `rm-manual-items` | `manual:{manualItemId}` | manual item | `manualDocument` (`:106`) |
| `rm-documents` | `document:{releaseId}:{DOC_TYPE}` | document type, latest revision only | `documentOf` (`:121`) |

Ids are per release (D-12-11, which amends D-12-02). `IndexDocumentMapper.map` (`:31-42`) is a pure function from a
release plus its latest snapshots to the exact desired set. Every release is indexed whatever its status (D-12-08).

### 2.2 Text and metadata

All paths below are in `application/assistant/IndexDocumentMapper.java`. `clean` strips NUL characters (`:177`),
which rag-service would reject.

| Document | Title | Text |
|---|---|---|
| Ticket | `KEY summary` (`:71`) | `Label: value` lines for Type, Status, Resolution, Assignee, Reporter, Fix versions, Component, Story points (`:52-60`); then `Description:` and `Comments (showing N of M):` (`:61-69`) |
| Code links | `KEY code links` (`:102`) | `Pull requests:` lines (title, status, source -> target branch) and `Commits:` lines (short sha, author, message) (`:87-100`) |
| Manual item | item title (`:117`) | Title, Category, then `Description:` (`:111-116`) |
| Document | `Release notes - {name} {version} (revision N)` (`:127`) | the snapshot Markdown (`:131`) |

Metadata (flat, `MetadataRules` limits: 50 keys, 64-char keys, 1000-char strings, `MetadataRules.java:10-13`):

- every document: `release_id`, `release_name`, `release_version`, `release_status` (`releaseMetadata`, `:143-150`);
- tickets and code links add `project_key` (from the key prefix), `ticket_key`, `ticket_status`, `issue_type`;
- manual items add `project_key` (the release's Jira project) and `category`;
- documents add `project_key`, `doc_type`, `revision`, `generated_at`.

Note: the status fields are named `release_status` and `ticket_status`, not a bare `status`. The only filter the
assistant sends is `release_id` (section 4.1).

### 2.3 When it is pushed

1. **Publish.** Persistence adapters call `publisher.publishEvent(new ReleaseIndexInvalidated(releaseId))` on every
   write that changes what is indexed: `ReleaseRepositoryAdapter.java:84,131,144,159,184,259,268,285,301,315` and
   `DocumentSnapshotRepositoryAdapter.java:63`.
2. **After commit.** `IndexInvalidationListener.onInvalidated` is `@TransactionalEventListener(AFTER_COMMIT)`
   (`:31-44`). It only enqueues, because the handler runs on the committing request thread.
3. **Coalescing.** A `Set<UUID> queued` drops duplicate events for a release that is already waiting (`:33`). A single
   `ReentrantLock turn` serialises syncs, and the flag is cleared only when a task owns the turn (`:23-24,35-37`), so
   events that arrive during a sync collapse into one follow-up.
4. **Sync.** `IndexSyncer.sync` (`:53-68`) holds a lock across read and push, reads the release and the latest
   snapshot per type (`:90-96`), maps the desired set, pushes every document with `PUT` (`:98-105`), then deletes
   documents it previously pushed that are no longer desired (`deleteUndesired`, `:107-123`). Every sync re-pushes
   everything; rag-service's content hash makes unchanged documents a no-op (D-12-12, section 3.1).
5. **Failure.** A failed push or delete is logged with collection, id and HTTP status only and dropped (`:98-105`,
   `:112-118`; D-12-04). Nothing retries it except the next sync or the sweep.
6. **Sweep.** `IndexSweep.sweep` (`:79-101`) runs `ensureCollections`, then syncs every release plus any release id the
   syncer recorded but that no longer exists. It runs 10 s after startup and then every `RM_ASSISTANT_SWEEP_INTERVAL`
   (default `PT30M`), using `fixedDelay` so it never overlaps itself (`application.yml:53-55`; D-12-05).
7. **Limit that you may trip over.** The "known documents" map (`IndexSyncer.recorded`, `:45`) is in memory only. I
   found no code that rebuilds it from rag-service. After a restart, documents of a release that was deleted while the
   app was down are not in that map, so the sweep does not delete them. Not verified at runtime.

### 2.4 The switch

`app.assistant.enabled` (`RM_ASSISTANT_ENABLED`, default `false`, `application.yml:49`). The listener, syncer,
sweep, adapters, chat controller and chat service are all `@ConditionalOnProperty(app.assistant.enabled=true)` (for
example `IndexSweep.java:66`, `RagIndexAdapter.java:24`, `AssistantChatController.java:29`). Off means nothing is
pushed and the chat endpoint does not exist (D-12-09). `GET /api/assistant/status` reports `enabled: false`
(`AssistantStatusController.java:36-44`).

## 3. Ingest inside rag-service

### 3.1 PUT document and the content-hash no-op

`PUT /v1/collections/{collection}/documents/{documentId}` (`DocumentController.java:37-45`) answers **202** with the
status. `DocumentService.push` (`DocumentService.java:34-49`) checks, in order: text at most `rag.limits.max-text-bytes`
(1 MiB, 413), text not blank, title at most 300 characters, no NUL in title, text or url, and metadata shape (400).
It then calls `ContentHash.of(title, text)`, SHA-256 over `title + NUL + text` (`ContentHash.java:13-16`).

`DocumentRepositoryAdapter.upsert` (`:37-66`) is one `INSERT ... ON CONFLICT (collection_id, external_id) DO UPDATE`:

- **Same hash and status not FAILED:** status, reason and lease are kept. Nothing is re-embedded. This is the no-op.
- **Changed hash, or the document was FAILED:** status becomes `PENDING` and the lease is cleared.
- Title, url, metadata and body are always overwritten; last write wins (D-09-11).
- Old chunks stay searchable until the replacement lands (comment at `:33-34`).

Pushes are accepted while Ollama is down (D-09-10): the push only writes a row.

### 3.2 States and the worker

| Status | Meaning | Set by |
|---|---|---|
| `PENDING` | waiting to be embedded | upsert (`:48`), `requeueAll` (`:89`) |
| `INDEXED` | chunks and vectors stored | `IndexQueueRepositoryAdapter.complete` (`:63-90`) |
| `FAILED` | bad content only (D-09-12) | `IndexQueueRepositoryAdapter.fail` (`:94-103`) |

`IndexWorker.tick` (`IndexWorker.java:59`) is `@Scheduled`: first run after `rag.worker.initial-delay` (5 s), then
`fixedDelay` `rag.worker.poll-interval` (2 s) (`application.yml:50-56`). It skips the tick when:

- any chat or transform is in flight (`gate.anyInFlight()`, `GenerationGate.java:46`; D-10-03). Indexing yields the GPU;
- the Ollama backoff window is open (`notBefore`, `IndexWorker.java:141-146`).

`runOnce` (`:70-139`) is deliberately not transactional, so a slow embedding call never holds a DB connection:

1. `queue.claim(batchSize, lease)` (`:72`) takes up to 8 `PENDING` rows whose lease is empty or expired, ordered by
   `updated_at`, using `FOR UPDATE SKIP LOCKED`, and sets `lease_until = now() + 10 min`
   (`IndexQueueRepositoryAdapter.java:25-48`).
2. For each document: chunk (3.3), embed (3.4), then `queue.complete` (`:115`). `complete` updates the row only if
   `content_hash` still matches, deletes the old chunks and inserts the new ones in one transaction
   (`IndexQueueRepositoryAdapter.java:63-90`). A stale result returns false and is discarded.
3. Outcomes:
   - no indexable text, or invalid content (title too long): `FAILED` with a reason;
   - Ollama rejects the input (400): `FAILED` "Embedding model rejected the content";
   - Ollama down: the unprocessed documents' leases are released and the worker backs off from 1 s, doubling, to
     5 min (`:99`, `:141-146`). If Ollama is up but the call failed, the lease is kept so the document retries after
     the lease expires, and the rest of the batch continues;
   - any other runtime error: logged by type, lease kept, retry after the lease.

### 3.3 Chunking

`Chunker` (`domain/Chunker.java`), built from `rag.chunking.max-chars: 1480` and `overlap-chars: 150`
(`application.yml:40-42`). The constructor requires `overlap * 2 < maxChars` (`Chunker.java:19-25`). The 1480 covers
header plus passage and leaves room for the 17-character `search_document: ` prefix under a 1,500-character safe
limit (comment at `application.yml:41`).

Algorithm (`Chunker.chunk`, `:31-74`):

1. Normalise `\r\n` and `\r` to `\n`. A blank body gives no chunks.
2. `budget = maxChars - len(title + "\n\n")` (`:36`). If `budget <= 2 * overlap`, the title is too long and the
   document goes `FAILED` (`:37-39`).
3. **Sections.** Split at Markdown headings `^#{1,6}\s` (`HEADING`, `:10`), except inside ``` fenced blocks, which toggle
   on a line starting with three backticks (`sections`, `:132-148`).
4. **Units.** A section no longer than `budget` is one unit. A longer section is split at blank lines into paragraph
   units that keep the section number (`units`, `:114-130`).
5. **Packing.** Units are appended to the current passage joined by `\n\n` while they fit (`fits`, `:76`). When the next
   unit does not fit, the passage is emitted and the next one starts with an overlap tail (`:56-59`).
6. **Oversize unit.** One paragraph longer than the room is cut at the last whitespace before the limit, or hard-cut at
   the limit if there is none (`cutPoint`, `:84-93`); the tail of each piece seeds the next.
7. **Overlap tail** (`tail`, `:95-103`) is the last 150 characters of the passage, moved forward to a word boundary. It
   is carried only inside the same section; if the next unit starts a new section, the overlap is dropped (`:46-47`,
   `:58`).
8. Passages are stripped and empty ones removed (`:73`).

Only the passage text is stored in `chunk.content`. The title header is added only to the embedding input
(`IndexWorker.java:102`, `Chunker.withHeader`, `Chunker.java:27-29`). Small adjacent sections can share one passage.

### 3.4 Embedding and storage

- **Model.** `nomic-embed-text`, default and only allowed model (`application.yml:37-39`; `CollectionService.java:36-40`
  returns 400 `MODEL_NOT_ALLOWED` otherwise). A collection's model is fixed at creation; a different model on `PUT` is
  409 (`CollectionService.java:74-78`).
- **Prefixes.** `OllamaEmbeddingAdapter.embed` (`:52-61`) prepends `search_document: ` for `EmbeddingPurpose.DOCUMENT`
  and `search_query: ` for `QUERY` (`application.yml:35-36`), in batches of `rag.ollama.batch-size` (32). It calls
  `/api/embed` with `truncate=false` (`:79`).
- **Dimension.** `EmbeddingPort.DIMENSIONS = 768` (`ports/EmbeddingPort.java:9`). Any other vector length is
  "Unexpected embedding response shape" (`OllamaEmbeddingAdapter.java:97-99`). The column is `vector(768)`.
- **Tables** (`V1__rag_schema.sql`): `client_app` (`:4`), `collection` with `embedding_model` (`:13`), `document` with
  `body`, `content_hash`, `status`, `lease_until` and a partial index on `updated_at` for `PENDING` (`:22-38`), and
  `chunk` with `ordinal`, `content`, `embedding vector(768)`, `embedding_model`, unique `(document_id, ordinal)`
  (`:40-49`).
- **Exact scan, no ANN index.** `chunk_collection` is a plain btree on `collection_id`. The migration comment says
  "exact cosine scan, no ANN index until a slow query is measured" (`:50`). I found no `ivfflat` or `hnsw` index.

### 3.5 Re-indexing and delete

There are three ways a document is indexed again. All three end in the same place: the document goes back to
`PENDING` and the `IndexWorker` re-chunks and re-embeds it (3.2-3.4). Its old chunks stay searchable until the
replacement lands, so search never goes empty mid-re-index.

```text
 Trigger                       What happens                Result
 -------                       ------------                ------
 1. Content changed            PUT with new title/text     hash differs
    (ticket edit, regenerated  -> upsert                   -> PENDING
    document, AI revision)                                 -> re-embed

 2. Periodic sweep             IndexSweep re-pushes all    hash same
    (startup + every 30 min)   live releases               -> no-op
                                                           FAILED -> PENDING

 3. Explicit re-index          POST /v1/collections/       every document
    (chunking or model change) {c}/reindex                 -> PENDING
```

**1. Content changed (automatic, seconds).** Any ReleaseManagement change that affects indexed text publishes
`ReleaseIndexInvalidated` after commit. `IndexSyncer.sync(releaseId)` then re-pushes that release's documents. In
rag-service the upsert (`DocumentRepositoryAdapter.java:37-52`) compares `content_hash`, which is a hash of title and
text only (`DocumentService.java:49`, `ContentHash`):

- **hash differs, or the row was `FAILED`:** status becomes `PENDING` and the lease is cleared, so the worker picks it
  up again;
- **hash equal and not `FAILED`:** status is kept, so re-pushing unchanged text costs nothing (metadata such as
  `release_status` is still updated in place, with no re-embedding).

**2. Periodic sweep (self-healing).** `IndexSweep.sweep()` (`src/main/java/.../application/assistant/IndexSweep.java:36`)
runs at startup (`app.assistant.sweep.initial-delay`, PT10S) and then every `app.assistant.sweep.interval`
(`RM_ASSISTANT_SWEEP_INTERVAL`, default PT30M). It does three things:

- `ensureCollections()` creates the four `rm-*` collections if they are missing;
- `sync()` re-pushes every live release, which repairs any push that was dropped while rag-service was down (failed
  pushes are not retried individually, D-12-04);
- releases recorded in memory but no longer live have their documents deleted.

Because of the hash check, a sweep over unchanged data is cheap. Limitation: the "recorded" set is in memory, so a
release deleted while the app was down can leave orphan documents until rag-service's copy is removed by hand.

**3. Explicit re-index (operator).** Needed when the text is unchanged but how it is indexed changed, for example a
new chunk size or overlap (`rag.chunking.*`) or a new embedding prefix. The hash only covers title and text, so
triggers 1 and 2 would not notice.

```bash
curl -s -X POST localhost:8081/v1/collections/rm-tickets/reindex \
     -H "Authorization: Bearer $RM_ASSISTANT_API_KEY"
# 202 {"requeued": <count>}   repeat for rm-documents, rm-manual-items, rm-code-links
```

`CollectionController.java:59` calls `CollectionService.reindex` (`:70`), which calls `requeueAll`
(`DocumentRepositoryAdapter.java:87-93`): every document in the collection goes to `PENDING` with its reason and lease
cleared. Progress is visible with `GET /v1/collections/{c}` (pending, indexed, failed counts and `lastIndexedAt`).

A different **embedding model** is not a re-index. `PUT /v1/collections/{c}` with another model answers 409, and the
vector size is fixed in the schema (`vector(768)`), so a model change needs a migration and fresh collections.

**Worker failure handling.** The `IndexWorker` retries on its own: if Ollama is unavailable, documents stay `PENDING`
for the next run. If the model rejects a document, or it has no indexable text, it is marked `FAILED` with a reason.
A `FAILED` document is retried by the next push or sweep, or by an explicit re-index.

**Delete:** `DELETE .../documents/{id}` removes the row, and its chunks go with it (`ON DELETE CASCADE`,
`V1__rag_schema.sql:42`; `DocumentRepositoryAdapter.java:70-73`). The answer is 204, or 404 if the document is absent,
which ReleaseManagement treats as success (`RagIndexAdapter.java:64-68`).

## 4. Query and chat path

### 4.1 Request and retrieval

ReleaseManagement's `AssistantService.ask` (`AssistantService.java:70-117`) always sends all four collections
(`:87`). When the chat is scoped to a release it sends `filter = {"release_id": "<uuid>"}` (`:88`); unscoped, no
filter. rag-service validates the request in `ChatService.prepare` (`ChatService.java:114-178`):

- collections: 1 to 10 owned names (`ChatRequest.MAX_COLLECTIONS`, `ChatRequest.java:32`); all must share one
  embedding model, else 400 (`:144-146`);
- question at most `question-max-chars` 2000; history at most 20 turns of at most 4000 characters, roles `user` or
  `assistant` only (`:119-135`);
- `k` between 1 and `k-max`. `k-default` and `k-max` are both **4** (`application.yml:70-71`), so `k` is effectively
  fixed at 4 for chat. `/search` is separate: default 10, max 50 (`application.yml:47-49`; `SearchService.java:40-43`);
- filter: flat equality and `in` lists, ANDed (`MetadataRules.parseFilter`, D-09-07). Containment is type sensitive:
  string `"3"` does not match number `3` (`ChunkSearchRepositoryAdapter.java:45`);
- then the readiness check (`chat.assertReady`, `OllamaChatAdapter.java:77-91`, 503 if the model is missing) and the
  per-client gate (4.6).

`ChatService.run` (`:197-341`) sends `status SEARCHING`, embeds the question as a `QUERY` (`:214`) and runs one SQL
(`ChunkSearchRepositoryAdapter.java:28-68`): chunks of the listed collections with the same embedding model, the
`metadata @>` clauses, `score = 1 - cosine distance`, `ORDER BY embedding <=> query LIMIT k`.

### 4.2 The min-score gate

`ChatService.java:219-222`: if **no tools were offered** and there are no hits, or the **top hit** scores below
`rag.chat.min-score` (0.55, `application.yml:72`), the request is refused with layer `GATE` and the model is never
called. With tools offered the gate is skipped (D-11-06), because a question like "what stage is 4.2 in?" has its
answer in a tool, not in the passages.

### 4.3 SRC blocks

`ChatService.group` (`:371-386`) groups the hits by `(collection, documentId)` in rank order. Each group becomes one
numbered `SourceDocument` (`n = 1, 2, ...`) holding its passages and the first hit's score. `PromptBuilder.block`
(`PromptBuilder.java:161-165`) renders:

```
<<<SRC id=1 title="KEY summary" nonce=ab12cd34ef56>>>
passage

passage
<<<END nonce=ab12cd34ef56>>>
```

Titles lose quotes and newlines and are capped at 200 characters (`:104-108`).

### 4.4 Prompt build and token budget

`PromptBuilder` (`PromptBuilder.java`) is built with `num-ctx` 4096 (`RAG_CHAT_NUM_CTX`), `chars-per-token` 3.0 and
`history-token-budget` 600 (`application.yml:59,78,76`). `TokenEstimator.estimate` is `ceil(chars / 3.0)`
(`TokenEstimator.java:15-17`). `chat` (`:110-127`):

1. Messages are system, trimmed history, then one user message: `SOURCES (untrusted ...)` header, SRC blocks and
   `QUESTION: ...` (`:121-124`).
2. `fixed` = system + header + question + `TEMPLATE_SLACK` 256 (`:23,113-114`). If `fixed + num-predict (512) > num-ctx`
   the request is 400 "Question does not fit the context window" (`:115-117`).
3. `available = numCtx - numPredict - fixed`. `fit` (`:129-159`) adds whole SRC blocks in rank order until the next
   would exceed `available`, and stops there. Remaining room, capped at 600 tokens, goes to history, filled from the
   newest turn backwards, never splitting a turn.
4. **System rules** come from `rag.prompts.chat-system` (`application.yml:89-98`), with Java defaults in `Prompts.java`.
   `PromptRules` requires the literal `NO_ANSWER` in `chat-system` and exactly one `%s` in `correction`
   (`PromptRules.java:23-28`).
5. **Fences and nonce.** Every request gets a 12-hex-character `SecureRandom` nonce (`randomNonce`, `:84-88`).
   `neutralise` removes `<|im_start|>`, `<|im_end|>`, `<|endoftext|>`, `<<<`, `>>>` and the nonce itself from all
   passages, history, question and titles, looping to a fixpoint (`:91-102`). The fence strings live in code, not
   config.

### 4.5 Tool routing and the tool loop

Tools are optional per request: up to 20 definitions, validated by `ToolSchema` (`ChatRequest.java:34`;
`ChatService.java:150-152`). ReleaseManagement offers its five tools: `releases`, `tickets`, `deployments`,
`documents`, `dashboard` (`application/assistant/tools/`, D-12-13). A release-scoped chat only offers tools that declare
a `release` parameter (`AssistantService.java:75-77,129-131`), and the tool argument is overwritten with the scoped
release (`:148-150`).

**Round 1 is a routing request (D-11-13).** When tools are offered, there are no earlier turns and it is not the final
round, the model is first asked with `rag.prompts.tool-routing`, history, and the question only, with no SOURCES and no
`NO_ANSWER` rule (`ChatService.java:230-233`; `PromptBuilder.toolRouting`, `:222-239`). Reason: the retrieval-QA frame
made a 4B model refuse instead of calling a tool. If the model returns no tool call, its text is discarded (the guard
callback returns early when `routed`, `:246-248`) and the same loop continues with the answer prompt (`:267-272`).

**Answer round.** `chatWithTools` (`PromptBuilder.java:202-218`) uses the chat system rules plus `tool-rules`, the
`final-round-rule` on the last round (`:252-255`), the SRC blocks, and the earlier tool exchange. Tool results are
rendered as `<<<TOOL id=N name=X [status=error] nonce=...>>>` blocks; successful results with a source get the next
number after the SRC ids (`exchange`, `:283-308`). `neutraliseTool` also strips `<tool_call>` style tags.

**A tool call turn.** If the model emits tool calls (`ChatService.java:293-312`):

1. `ToolPolicy.check` (`ToolPolicy.java:32-62`): at most `max-calls` (4) per round, unique call ids, known tool names,
   arguments valid against the schema, and no repeat of an already answered name plus canonical arguments.
2. On success the turn is signed with HMAC-SHA256 over client id, question hash, question id, round, previous
   signature and canonical calls (`ToolTurnSigner.sign`, `:35-61`). The gate slot is released, then `tool_call`
   `{round, questionId, calls, signature}` and `done` with `finishReason: "tool_calls"` are sent
   (`ChatService.java:305-310`).
3. On problems the service retries once inside the same request with a correction message (`withCorrection`,
   `PromptBuilder.java:241-250`); a second failure, or no token room, ends with `error TOOL_CALL_INVALID`
   (`ChatService.java:313-317`).

**Resume.** ReleaseManagement runs the tools in-process (`AssistantService.execute`, `:139-159`): unknown tool, tool
exception or a result over `tool-result-max-chars` (6000) become `{"error": ...}` results, not failures. It then POSTs
the same request again with `toolTurns` holding the signed fields copied untouched plus results (`RagChatAdapter.java:
110-126`). rag-service verifies the chain (`ToolTurnSigner.verifyChain`, `:63-76`; 400 `TOOL_TURN_INVALID`), checks
results at most 8000 characters and matching ids (`ToolPolicy.requireValidResume`, `:64-78`; 413
`TOOL_RESULT_TOO_LARGE`), and requires the budget to fit (`requireToolBudget`, `PromptBuilder.java:183-200`). The
service holds no state between rounds (D-11-01).

**Max rounds.** `rag.tools.max-rounds` / `RAG_TOOL_MAX_ROUNDS` (default 4, `application.yml:81`).
`ToolPolicy.isFinalRound` (`:80`) is true once `turns.size() >= maxRounds`; that request offers no tools and adds the
final-round rule. ReleaseManagement loops `maxRounds + 1` times (`AssistantService.java:93`), reading
`app.assistant.max-rounds` (4, `application.yml:63`); keep both equal. Tool rounds use `num-predict` 768
(`application.yml:86`).

### 4.6 Refusal layers

`RefusalGuard` (`RefusalGuard.java`) buffers the start of the stream. If the output begins with `NO_ANSWER` it swallows
it; any other start is flushed and passes through. Both layers send the same fixed text token
(`I don't know. The available sources do not contain the answer to this question.`), an empty
`citations {basis: RETRIEVED}` and `done {refusal: true, refusalLayer}` (`ChatService.refuse`, `:359-369`).

| Layer | Trigger | Model called |
|---|---|---|
| `GATE` | no hits or top score below 0.55, tools not offered (`:219-222`) | no |
| `MODEL` | the model said `NO_ANSWER`, or produced no text (`:277-280`) | yes |

### 4.7 Citations

`CitationBuilder.build` (`CitationBuilder.java:35-68`) uses retrieval records and ids only, never model-written
titles (D-13-22):

- **MARKED.** It scans the answer for `[n]` and `[n, m]` markers (`MARKER`, `:20`). Ids that match a sent SRC block or a
  numbered TOOL source become citations; ids that match neither are counted as `inventedIds` and dropped.
- **RETRIEVED fallback.** If no valid marker is found, it cites up to 3 (`MAX_RETRIEVED`, `:19`) sent sources scoring at
  least `min-score`, plus every TOOL source (`:61-67`).
- **TOOL.** Tool citations have `kind: TOOL`, `collection` null, no metadata (`:70-73`).

ReleaseManagement turns the citations into `TICKET`, `DOCUMENT`, manual-item and code-link views by collection name
(`AssistantSourceViews.java:35-41`).

### 4.8 SSE events

Between the clients and rag-service (`ChatController.java:33-43`, `StreamEvents.java`):

| Event | Payload | Notes |
|---|---|---|
| `status` | `{requestId, phase: SEARCHING or GENERATING}` | |
| `token` | `{text}` | refusal text is one token |
| `citations` | `{basis: MARKED or RETRIEVED, items[]}` | chat only |
| `tool_call` | `{round, questionId, calls[], signature}` | stream then ends with `done` |
| `done` | `{refusal, refusalLayer, finishReason, truncated, promptTokens, completionTokens, historyTrimmed, model, promptHash}` | |
| `error` | `{reason}` | `DEADLINE`, `GENERATION_FAILED`, `OLLAMA_UNAVAILABLE`, `MODEL_NOT_AVAILABLE`, `EMBEDDING_UNAVAILABLE`, `TOOL_CALL_INVALID`, `INTERNAL` (`ErrorReason.java`) |
| `:hb` comment | | heartbeat |

`truncated` is set when Ollama's `prompt_eval_count` is at most 60% of the estimate while the estimate is above 80% of
`num_ctx`, or when the estimator under-counted (`ChatService.java:388-408`). `historyTrimmed` means older turns did not
fit. Errors before the stream opens are problem+json (`GlobalExceptionHandler`): 400, 404, 413, 429 with `Retry-After`,
503.

## 5. Bounds and safety

| Bound | Value | Where |
|---|---|---|
| Per-client in-flight | 1 (`RAG_CHAT_MAX_IN_FLIGHT_PER_CLIENT`), over it is 429 `TOO_MANY_REQUESTS`, `Retry-After` 5 s | `GenerationGate.tryAcquire` (`:23`), `application.yml:67-69`, `GlobalExceptionHandler.java:98-103` |
| Global in-flight | 4 | `application.yml:68` |
| Deadline | 120 s per POST (`RAG_CHAT_DEADLINE`); sends `error DEADLINE` | `ChatService.java:201`, `application.yml:64` |
| Heartbeat | every 5 s | `ChatService.java:202`, `SseSink.heartbeat` (`:27`) |
| Client gone | a failed write, or emitter completion or error, cancels with `CLIENT_GONE`; no error event; slot freed | `SseSink.java:40-47`, `ChatController.java:80-81` |
| Emitter timeout | deadline + 15 s backstop | `ChatController.java:31,79` |
| Request body | over 2 MiB is 413 (checked on `Content-Length`, and counted for chunked bodies) | `RequestSizeLimitFilter.java:37-49`, `application.yml:45` |
| Document text | 1 MiB, 413 | `DocumentService.java:35` |
| Transform text | over the context budget, 413 `TEXT_TOO_LARGE` | `GlobalExceptionHandler.java:131` |
| Tool result | over 8000 characters or the context, 413 `TOOL_RESULT_TOO_LARGE` | `ToolPolicy.java:70-72` |

The gate is in memory and single-instance by design (`GenerationGate.java` header comment). The slot is released exactly
once, early on a `tool_call` so the resume does not meet its own 429 (`ChatService.java:204-210,305`).

**Logging is metadata only.** Each request logs one `generation endpoint=chat ...` line with ids, counts, timings,
`promptVersion`, `promptHash` and `model` (`ChatService.java:420-437`). Request records override `toString` so
Spring's DEBUG logging cannot print text (`StreamEvents.Token`, `:20-24`; `OllamaEmbeddingAdapter.EmbedRequest`, `:121-127`).
`LogHygieneIT` exists for this; I did not read its assertions.

**Prompt hash.** `PromptRules.hash()` is the first 12 hex characters of SHA-256 over both prompt version strings
(`p10-v2`, `p11-v2`, `Prompts.java`) and all six rule texts (`PromptRules.java:35-44`). It is sent in `done.promptHash`
and logged. Any edit to `rag.prompts.*` changes it.

## 6. The ReleaseManagement browser side

- `POST /api/assistant/chat` (`AssistantChatController.java:50-62`) takes `{question, history, releaseId?}`, checks the
  release exists, opens an `SseEmitter` (`stream-deadline` + 30 s) and streams on a virtual thread. The browser never
  sees the rag-service key. The frontend caller is `frontend/src/api/assistant.ts:75`.
- `BrowserStreamListener` (`:36-70`) forwards `token`, a `discard` event when a tool-routing round ends
  (`roundDiscarded`), `status {name}` when a tool starts, and `:hb` heartbeats. It ends with `done` `{refused, sources,
  tools, historyTrimmed, meta{model, promptHash}}` or `error {outcome, retryAfterSeconds}`. A send to a closed browser
  throws `ClientGoneException`, which closes the upstream response so rag-service sees `CLIENT_GONE`.
- `app.assistant.stream-deadline` (`PT5M`) bounds the whole question including tool rounds (`:68-72` of the listener,
  `application.yml:69`). Each rag POST has its own 120 s deadline; the REST client read timeout is 130 s
  (`application.yml:59-62`).
- **Scoped question.** For a release chat, `AssistantService.scopedQuestion` (`:121-127`) fills
  `app.assistant.prompts.scoped-question` (`The question is about release "{release}". {question}`,
  `application.yml:71`) with `name version` in one pass, so neither value is substituted twice. The template must hold
  both placeholders (`AssistantProperties.validatePrompts`).
- **RAG errors map** (`RagChatAdapter.java:128-139`): 429 is BUSY with `Retry-After`; 503 `MODEL_NOT_AVAILABLE` is
  MODEL_MISSING; other 503 or connection refused is OFFLINE; 400 or 413 is REJECTED; timeout is DEADLINE.
- Document transforms (Clean up, Summarize, Custom) use `/v1/transform`, not retrieval: see
  `docs/document-assist-flow.md`. After an AI revision is saved, the same invalidation event re-indexes that document.

## 7. Testing

- **Hermetic suites (default `mvn test`).** rag-service: `ChunkerTest`, `PromptBuilderTest`, `CitationBuilderTest`,
  `RefusalGuardTest`, `ToolPolicyTest`, `ToolTurnSignerTest`, `GenerationGateTest`, `IndexWorkerIT`, `ChatApiIT`,
  `SseStreamingIT`, `LogHygieneIT`, `ArchitectureBoundaryTest`, using `FakeOllamaServer` and a pgvector Testcontainer
  (`PgVectorImage`). ReleaseManagement: `IndexDocumentMapperTest`, `IndexSyncerTest`, `IndexSweepTest`,
  `AssistantServiceTest`, `RagChatAdapterTest`, `RagIndexAdapterTest`, `SseStreamReaderTest`. The rag-service pom
  excludes the `golden` group by default (`rag-service/pom.xml:27`).
- **Golden (`-Pgolden`).** `mvn -f rag-service/pom.xml test -Pgolden` selects `@Tag("golden")` (`pom.xml:129-134`):
  `GoldenChatIT` and `GoldenToolsIT` run fixtures in `rag-service/src/test/resources/golden/` (`chat-v1.json`,
  `tools-v1.json`, `transform-v1.json`, `seed/`) through the real HTTP API against a real Ollama. They run 3 times per
  case, use `RAG_GOLDEN_OLLAMA_URL` (default `http://localhost:11434`) and `qwen3:4b-instruct-2507-q4_K_M`, and skip
  themselves when Ollama or a model is missing (`GoldenChatIT.java:50-60`, `GoldenToolsIT.java:120`).
- **Quality gate.** `docs/quality-gate.md`: `QualityGateIT` asks a fixed question set through `/api/assistant/chat`
  against a real rag-service and Ollama. Safety cases must pass 3 of 3, quality cases at least 80%. v1.1 must not ship
  without a waiver-free PASS and a golden run with T03 3/3.
- **Rerun both after changing** the chat model, any `rag.prompts.*` text (so `promptHash`), the question-set version,
  `num-ctx`, `min-score`, `k-*`, the chunking settings, or the embedding model.

## 8. Where to change things

| What | Where |
|---|---|
| Chunk size and overlap | `rag.chunking.max-chars` 1480, `overlap-chars` 150 (`rag-service/.../application.yml:40-42`); algorithm in `Chunker.java`. Then re-index each collection (3.5) and rerun goldens |
| Embedding model and prefixes | `rag.embedding.*`, `rag.ollama.document-prefix`, `query-prefix` (`application.yml:35-39`); vector size is hard-coded in `EmbeddingPort.java:9` and `V1__rag_schema.sql:46`. A model change needs a new migration and a re-index |
| What text and metadata are indexed | `IndexDocumentMapper.java` (ReleaseManagement); collections in `IndexCollection.java` |
| When indexing is triggered | `publishEvent` sites (2.3), `IndexInvalidationListener.java`, sweep: `app.assistant.sweep.*` / `RM_ASSISTANT_SWEEP_INTERVAL` (`application.yml:53-55`) |
| Turn the assistant off | `RM_ASSISTANT_ENABLED=false` (`application.yml:49`) |
| Worker pace and retry | `rag.worker.*` (`rag-service application.yml:50-56`) |
| Retrieval depth and cutoff | `rag.chat.k-default`, `k-max`, `min-score` (`:70-72`). Raising `k-max` also needs a larger `num-ctx` |
| Context and output budget | `RAG_CHAT_NUM_CTX` (4096), `num-predict` 512, `chars-per-token` 3.0, `history-token-budget` 600 (`:59-60,76,78`); `PromptBuilder.TEMPLATE_SLACK` (`:23`). Keep `RM_ASSISTANT_NUM_CTX` equal for transforms |
| Prompt rules | `rag.prompts.*` (`:88-126`, env `RAG_PROMPTS_CHATSYSTEM` etc., see `docs/docker.md`); data fences and nonce are code (`PromptBuilder.java:161-165`) |
| Chat model | `RAG_CHAT_MODEL` (`:58`); must report `completion`, and `tools` for tool requests (`OllamaChatAdapter.java:88,95`) |
| Limits and deadlines | `RAG_CHAT_DEADLINE`, `RAG_CHAT_MAX_IN_FLIGHT_PER_CLIENT`, `heartbeat-interval`, `rag.limits.*` (`:43-46,64-69`); app side `RM_ASSISTANT_STREAM_DEADLINE` |
| Tool loop | `RAG_TOOL_MAX_ROUNDS`, `RAG_TOOL_RESULT_MAX_CHARS`, `rag.tools.*` (`:79-86`); app side `app.assistant.max-rounds`, `tool-result-max-chars` 6000, tool descriptions (`application.yml:63-77`); tools in `application/assistant/tools/` |
| Secrets | `RAG_ADMIN_KEY`, `RAG_TOOL_SIGNING_SECRET` (both 32+ chars), `RM_ASSISTANT_API_KEY`, `RAG_DB_*`, `RAG_OLLAMA_BASE_URL` |
| Scoped question, starters | `app.assistant.prompts.scoped-question`, `starters.*` (`application.yml:70-86`) |
| Citation rules | `CitationBuilder.java` (`MAX_RETRIEVED`, `MARKER`) |

**Changes that require rerunning `-Pgolden` and the quality gate:** the chat model, `rag.prompts.*`, `num-ctx`,
`min-score`, `k-*`, `PromptBuilder`, `CitationBuilder` or `RefusalGuard` logic, the tool policy, the embedding model,
the chunk settings, and the golden fixtures themselves. Indexing-only changes (worker pace, sweep interval) do not
affect `promptHash`.

## 9. What is never stored or logged

- **Logs.** rag-service logs ids, counts, timings, outcomes, `promptHash` and model only (`ChatService.java:420-437`).
  ReleaseManagement logs tool name, round, error flag and duration (`AssistantService.java:157`), and index failures
  as collection, document id and HTTP status (`IndexSyncer.java:102,116`). No question, history, passage, tool result or
  answer text.
- **Chat state.** Neither app stores a conversation. The browser sends history on each question, and tool turns are
  carried by the caller and verified by signature, not stored (D-11-01).
- **Tool results.** Tools return structured fields only; promotion `note` and `correctionReason` free text are never
  returned (D-12-15).
- **Keys.** rag-service stores only the SHA-256 of client keys (`ApiKeys.java:24`). ReleaseManagement's
  `AssistantProperties.toString` masks the key (`:150`).
- **Jira comments.** Indexed as the screens show them; Jira visibility metadata is discarded at ingest (D-12-14).
- **What is stored in rag-service:** the full document body, the chunks and their vectors, and metadata. That is
  business data, and it is readable by the `releasemgmt` Postgres superuser.
