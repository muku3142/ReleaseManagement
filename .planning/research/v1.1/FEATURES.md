# Feature Landscape: v1.1 Release Assistant

**Domain:** Internal, read-only "ask your data" chat assistant over an existing release-management app (local Ollama, tool-calling plus pgvector retrieval)
**Researched:** 2026-10-13
**Confidence:** MEDIUM. The pattern is well established, but it comes from training knowledge and the project docs, not from fresh web research. Small local models are the main uncertainty; see Risks.

## How these assistants typically work

An internal "ask your data" assistant is a bounded tool-using agent with a retrieval fallback. It is not a general chatbot. The loop is:

1. The user asks a question.
2. The model picks one or more tools. Structured tools are typed read-only functions over the system's own API. Retrieval is a vector search over text.
3. The tool results go back to the model.
4. The model writes an answer that must reference the results.
5. The UI renders the answer with citations built from tool and retrieval metadata, not from model prose.

Three design rules recur in systems that work:

- **Route by question shape.** Counts, dates, statuses, "what went where when" and "which tickets are in release X" are exact questions. Answer them with structured tools, never with embeddings. Vector search is for fuzzy, text-content questions ("which tickets mention timeouts", "what did the 4.2 runbook say about rollback").
- **Citations come from the system, not the model.** The tool result carries IDs (release, ticket key, document revision, commit hash). The backend attaches them to the answer. Model-written citations hallucinate; backend-attached ones cannot.
- **Refusal is a property of retrieval and tools.** If the tools return nothing, or retrieval scores fall below a threshold, the backend short-circuits to "I couldn't find that in your release data". It does not leave this to the model's judgment.

## Table Stakes

Missing any of these makes the assistant untrustworthy or unusable. Proposed IDs are ASST (assistant), IDX (index) and SAFE (safety).

| Feature | Why expected | Complexity | Depends on (existing) | Notes |
|---|---|---|---|---|
| Chat screen: send a question, get an answer | The milestone goal | Low | React SPA, session auth | One page, message list plus input. |
| Source citations with deep links | The milestone says "cites its sources". An answer from a release tool without a checkable source is worse than no answer. | Med | Routes for release, ticket row, document revision, ADO commit/PR link (REL-04, DOC-07, AUDIT-02, ADO-01) | Types: release, ticket, document revision, promotion entry, commit/PR. Built from tool and retrieval metadata. Commit/PR links go to ADO. The others are in-app routes. Every chunk needs a stable source reference at index time. |
| Refusal when unsupported ("I don't know") | Hallucinated release facts, such as a wrong prod date, cause real damage. Local small models fabricate readily. | Med | None | Backend gate: no tool results and no retrieval hits above a threshold returns a fixed refusal. The system prompt is a second layer only. Also refuse out-of-domain questions and write requests ("promote release X"). |
| Structured tools over existing read-only endpoints | Fixed by the milestone. Exact data cannot come from embeddings. | Med to High | Release list/detail (REL-04/05), promotion and deployment history (DEPL, AUDIT-04), readiness (DOC-09), insights (INSIGHT-01..03), ADO links (ADO-01) | Tools are thin wrappers over existing services. Each runs as the signed-in user, so permission scoping is inherited (AUTH-02). Output is capped and compact so a small model's context is not flooded. |
| Free-text retrieval (vector search) | Fixed by the milestone. Needed for ticket text, document bodies and commit messages. | High | Postgres (pgvector), ticket cache, document revisions (DOC-07), commit/PR data (ADO-01) | Chunk with metadata (release, ticket key, revision, type). Embed locally via Ollama. This is the largest piece of the milestone. |
| Index kept current, with no Jira/ADO call at question time | Fixed by the milestone | High | Ticket refresh flow, document generation, ADO enrichment | Hook into the existing write paths: re-embed on ticket refresh, index on document generation. Idempotent upserts keyed by source ID and content hash. |
| Prompt-injection hardening | Ticket text, comments and commit messages are untrusted. Fixed by the milestone key context. | Med | Read-only tool surface | Mostly structural: no write tools exist, retrieved text is fenced as data, citations are backend-built, no markdown image or link auto-rendering from model output (exfiltration vector), and output is rendered as escaped text or sanitised markdown. Cheap if designed in, expensive to retrofit. |
| Per-user scoping of retrieval | Users sign in with their own Jira credentials (AUTH-02), and a shared index could leak tickets they cannot see in Jira. | Med | AUTH-02 | See Risks. This is the most important design decision. Minimum: index only tickets already stored in releases the app shows to every release manager, and state that explicitly. Strict: filter retrieval by the user's visibility. |
| Streaming answers | Local models are slow (tens of seconds). A blank wait reads as broken. | Med | Spring MVC (SSE via `SseEmitter`, or `ResponseBodyEmitter`); Ollama supports streamed responses | Stream tokens, then send the citations as a final event. Tool calls complete before the final text streams, so show a "looking up..." status. |
| Visible failure states | Ollama down or model not pulled, or a request timeout | Low | Actuator health | Clear error plus a health check for Ollama reachability. Mirrors JIRA-06's "distinguishable from no results". |
| Request bounds | A local GPU box runs one model at a time. | Low | None | Per-user concurrency of 1, a request timeout, a max tool-call loop depth (about 4) and a max output size. |

## Differentiators

Valued, but not required for a trustworthy v1.1. Order is roughly by value per cost.

| Feature | Value | Complexity | Depends on | Notes |
|---|---|---|---|---|
| Scope a question to one release | Removes ambiguity ("the release" and "this one") and shrinks retrieval. Fits a release-centric app. | Low to Med | Release detail page | An "Ask about this release" entry point passes a releaseId filter. Prefer this to the model guessing the release. It is a filter on tools and retrieval, plus a chip in the UI. |
| Follow-up questions within a session | Natural usage ("and which of those had rework?"). | Med | Chat history | Send the last N turns. Keep N small (4 to 6) because of context limits. Resolving "those" is unreliable on small models, so keep prior tool results out of the context and carry only citations. |
| Suggested starter questions | Discoverability, since users do not know what the assistant knows. | Low | None | Static list, optionally context-aware on the release page. No model needed. |
| Show which tools and data were used ("how I answered") | Trust and debuggability. Highly valuable for a small local model. | Low | Tool-call log | A collapsible panel: tools called, arguments, row counts. It falls out of the tool loop for free. Strongly recommended, nearly table stakes. |
| Index freshness indicator | Tells the user "documents indexed up to X". This matters because there is no live Jira call. | Low | Index metadata | Per-source last-indexed timestamp in the chat footer. Extend to flag a stale answer: the cited ticket changed after the index time. |
| Conversation history persisted per user | Resume and review earlier answers. | Med | Users table, new tables | Persist per user in Postgres. Retention and deletion are needed because answers contain release data. Treat as optional in the first slice; in-memory per browser session is the cheaper start. |
| Feedback thumbs | Gives signal to tune prompts and thresholds. | Low | Persisted messages | Only worth building if someone will read the feedback. Pairs with persisted history. |
| Admin re-index control | Recovery from a bad index or embedding model change. | Low to Med | Index pipeline | One "rebuild index" action (backend job plus status), because changing the embedding model forces a full rebuild. A CLI or admin endpoint is acceptable. There is only one user role, so no role UI. |

## Anti-Features

Justified against CLAUDE.md ("no over-engineering, solve the problem at hand").

| Anti-feature | Why avoid | Do instead |
|---|---|---|
| Any write or action tool ("promote release", "generate doc", "add ticket") | The milestone is read-only. Prompt injection plus a write tool is the worst case. | No write tools exist. Refuse action requests and link to the relevant screen. |
| Live Jira or ADO calls at question time | Fixed out by the milestone. It adds latency and rate-limit exposure. | The index and existing DB are the only sources. Show freshness. |
| Cloud LLM fallback | Violates the locked "no data leaves the machine" decision. | Ollama only. |
| Agent frameworks (LangChain4j agents, Spring AI multi-agent, planners) | A single tool-calling loop is under 100 lines. A framework adds an abstraction layer to own. | Use Spring AI or a direct Ollama client only for chat, embeddings and tool calling, if it is the simplest route. Stack research decides this. |
| Separate vector database | pgvector in the existing Postgres is locked. | pgvector. |
| Free-text-to-SQL | Unbounded data access, and small models write bad SQL. | Fixed, typed tools. |
| Answering structured questions from embeddings | Counts, dates and statuses come back wrong or fuzzy. | Route to structured tools. |
| Multi-conversation management (rename, folders, search, share, export) | Single role, no collaboration. | One list of recent conversations, if persisted at all. |
| Model-written citations or links | They get hallucinated. | Backend-attached citations only. |
| Fine-tuning or training on release data | Heavy, with no demonstrated need. | Retrieval and tools. |
| Per-answer quality scoring, A/B model comparison, an eval dashboard | Premature. | A small fixed question set run manually or in a test (see Risks). |
| Voice, file upload to chat, image input | No consumer. | None. |
| Proactive notifications ("this release looks risky") | The assistant answers; it does not monitor. | None. |
| Multi-user shared chats, mentions | Contradicts the single-role scope. | None. |

## Concrete example questions mapped to data source

"Tool" means a structured tool over an existing read-only endpoint or service. "Vector" means similarity search over indexed text. "Both" means the tool narrows to a release or ticket set and vector search then reads the text.

| Question a release manager would ask | Source | Backing feature |
|---|---|---|
| What's the current stage of release 4.2.0? | Tool | DEPL-01 |
| When did 4.2.0 go to Production, and who promoted it? | Tool | DEPL-03, AUDIT-01 |
| What went to Production in September? | Tool | AUDIT-04 (environment plus date filter) |
| Which releases were corrected or voided, and why? | Tool | DEPL corrections, VOIDED entries and notes |
| Which releases have no approval document, or a stale runbook? | Tool | DOC-09 readiness |
| How many tickets are in 4.2.0, and how many are manual items? | Tool | INSIGHT-02, REL-08 |
| What share of 4.2.0 tickets have linked commits or PRs? | Tool | INSIGHT-02, ADO-01 |
| Which tickets in 4.2.0 were reopened after Done? | Tool | INSIGHT-03 (churn and rework) |
| Which release took the longest from freeze to production? | Tool | INSIGHT-01 |
| What commits and PRs are linked to PROJ-123? | Tool | ADO-01 |
| Which tickets in 4.2.0 have no linked code? | Tool | ADO-02/03 |
| List the tickets in 4.2.0 grouped by component. | Tool | REL-04 plus ticket fields |
| Which revision of the 4.2.0 release notes was generated last, and which tickets changed since? | Tool | DOC-07, DOC-09 staleness |
| Which tickets mention timeouts or retries? | Vector | Ticket text index |
| Summarise the rollback steps in the 4.2.0 runbook. | Vector scoped to the document | Document revision index |
| What did the first revision of the 4.1.0 release notes say about the matching module? | Vector over an old revision | Superseded revisions are in scope |
| Which commits touch the security module? | Vector over commit messages | Commit/PR index |
| Why was PROJ-456 in this release? | Vector over ticket text | Ticket text |
| Have we shipped anything related to login before? | Vector across releases, then cite | Cross-release retrieval |
| Which risky tickets in 4.2.0 had both high rework and security keywords? | Both | INSIGHT-03 plus ticket text |

Rule of thumb for routing: if the answer is a number, a date, a status or a list derived from a field, use a tool. If it is "what does the text say" or "find things about X", use vector search.

## Feature Dependencies

```
Ollama health / chat client
   -> structured tools -> chat endpoint (tool loop) -> chat UI (streaming)
                                         -> citations (needs stable IDs and deep-link routes)
Index pipeline (chunk, embed, upsert)
   -> hooks on ticket refresh / document generation / ADO enrichment
   -> vector retrieval -> refusal threshold
   -> freshness indicator -> admin re-index
Chat endpoint -> release scoping (filter on tools and retrieval)
Chat endpoint -> follow-ups -> persisted history -> feedback thumbs
Per-user visibility decision -> everything that touches the index (decide first)
```

Existing-feature dependencies, in order of risk:

- **Document revisions (DOC-07)** supply the immutable document text to index. Index each revision once and key it by revision ID. Superseded revisions stay in the index with a "superseded" flag, so the assistant can say "revision 2 said X, revision 3 says Y".
- **ADO enrichment (Phase 6)** supplies commit and PR text. Tickets without a link have no commit chunks. The assistant must say "no linked code" rather than "no code".
- **Ticket refresh** is the re-embed trigger for ticket text.
- **Insights (Phase 8)** supply exactly the structured tools the assistant needs. Reuse the services, not the controllers.

## MVP Recommendation

Prioritise, in this order:

1. Ollama client, health check and the bounded tool loop, with 4 to 6 structured tools over existing services (release stage, promotion history, readiness, composition/insights, ticket and code links).
2. Citations (backend-built) with deep links, the "how I answered" panel, and the refusal gate.
3. Index pipeline and vector retrieval over ticket text, document revisions and commits, with write-path hooks and prompt-injection fencing.
4. Streaming chat UI, release scoping, suggested starters and a freshness footer.
5. Follow-ups limited to the last few turns. History can be per browser session at first.

Defer, with reasons:

- **Persisted history and feedback thumbs.** Add only if the release manager actually uses the chat and asks. Persisted history also brings retention and privacy questions.
- **Admin re-index UI.** Ship a backend endpoint or CLI first; a UI is not needed for a single user.
- **Per-ticket staleness flagging in answers.** Freshness at source level is enough initially.

## Risks and unknowns for roadmap

- **Per-user visibility versus a shared index (needs a decision before design).** AUTH-02 says the tool never shows a user tickets they could not see in Jira. A shared pgvector index built from one user's refresh could expose ticket text to another user. The pragmatic reading is that the app already stores these tickets in shared releases, so the index adds no new exposure. Confirm this, and record it in PROJECT.md decisions.
- **Small local models are unreliable at tool selection and at refusing.** Mitigations: few tools with plain descriptions, a backend refusal gate, and a fixed set of 20 to 30 regression questions (like the table above) run manually after any model or prompt change. This is the one evaluation device worth having, and it is not a dashboard.
- **Chunking and embedding model choice** are stack questions; not decided here.
- **Revision and history retrieval** ("what changed between revisions") is fuzzy for vector search. Prefer a structured tool that returns two revisions' text for the model to compare.
- **Numbering:** continue the scheme with new categories, for example ASST-01..n (chat, citations, refusal, scoping, streaming), IDX-01..n (pipeline, hooks, freshness, re-index) and SAFE-01..n (prompt injection, read-only, bounds). Phase numbering starts at 9.

## Sources

- D:\IdeaProjects\ReleaseManagement\.planning\PROJECT.md (v1.1 milestone section)
- D:\IdeaProjects\ReleaseManagement\.planning\REQUIREMENTS.md (v1 IDs, out-of-scope list, AUTH-02, DOC-07, DOC-09, INSIGHT-01..03)
- Training-knowledge patterns for tool-calling and RAG assistants. LOW to MEDIUM confidence; no external sources were consulted in this pass.
