# Milestones

## v1.1 Release Assistant (Shipped: 2026-10-20)

**Phases:** 9-14 · **Plans:** 70 · **Timeline:** 2026-10-13 → 2026-10-20

**Delivered:** a reusable, self-hosted RAG service and a sourced, read-only assistant inside ReleaseManagement.

**Key accomplishments:**
1. Standalone `rag-service/` on pgvector with per-app API keys, async embedding queue, search, re-index and an OpenAPI contract
2. Streamed (SSE) chat with server-built `[n]` citations, honest refusal and metadata-only logging
3. HMAC-signed tool-call protocol in which the calling app runs its own tools; routing split took native tool calls from 0 to 30
4. ReleaseManagement as first client: after-commit async push, startup and 30-minute sweep, five read-only tools under ArchUnit rules
5. Assistant screen with release-scoped entry and a "how I answered" panel
6. AI document assist (clean up, summarize, custom instruction) saved as AI-assisted revisions

**Known gaps at close:** closed on user approval without the automated evidence: `AssistantLiveCheck`, the
dev quality-gate report, the first live doc-assist report and the production-host gate runs. SAFE-04 has
no measured numbers. Known deferred items at close: 6 (see STATE.md Deferred Items).

**Archives:** [roadmap](milestones/v1.1-ROADMAP.md) · [requirements](milestones/v1.1-REQUIREMENTS.md) · phases in `milestones/v1.1-phases/`

---

## v1.0 MVP (Shipped: 2026-10-20)

**Phases:** 1-8 incl. 5.1-5.4 · **Plans:** 89 · **Timeline:** 2026-07-27 → 2026-10-20

**Delivered:** Jira-sourced release assembly, template-driven release paperwork, ADO code enrichment and a tracked promotion ladder, without manual bookkeeping in Jira.

**Key accomplishments:**
1. Sign-in with the user's own Jira credentials; Jira Cloud browsing on `/search/jql` with cursor paging and sprint filtering
2. Release assembly with freeze, manual items and per-release ticket selection
3. User-authored logic-less templates (jmustache + flattened view-model) with live preview, chrome fields and per-user defaults
4. Immutable document snapshots exported to PDF and DOCX, with per-release template choice
5. Azure DevOps commits and PRs via Jira dev-status, degrading gracefully, with an org-scoped tracked-expiry PAT
6. Editable environment ladder with append-only promotion history, corrections and VOIDED outcomes, plus the insights dashboard

**Known gaps at close:** human checks 01-08, 04-11, 05.1-04, 05.2, 05.4-07, 06-10, 07-07 and 08-08 were
closed on the user's word rather than observed. JIRA-03 never run against a >100-ticket real result set.

**Archives:** [roadmap](milestones/v1.0-ROADMAP.md) · [requirements](milestones/v1.0-REQUIREMENTS.md) · phases in `milestones/v1.0-phases/`
