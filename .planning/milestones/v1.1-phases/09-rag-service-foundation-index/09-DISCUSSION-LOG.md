# Phase 9 Discussion Log (2026-10-13)

Human reference only; downstream agents read 09-CONTEXT.md.

| Area | Question | Options | Chosen |
|---|---|---|---|
| API keys & tenancy | How apps get keys | Admin endpoint / config-env list / CLI | Admin endpoint (D-09-01) |
| API keys & tenancy | How collections are created | Explicit create / auto on first push | Explicit create (D-09-02) |
| API keys & tenancy | Cross-app access | Never in v1.1 / explicit read grants | Never (D-09-03) |
| API keys & tenancy | Key expiry | No expiry, rotate on demand / fixed expiry | No expiry (D-09-04) |
| Documents & chunking | Who chunks | Service / caller may pre-chunk | Service (D-09-05) |
| Documents & chunking | Formats | Plain text + Markdown / also HTML-PDF-DOCX | Plain + Markdown (D-09-06) |
| Documents & chunking | Filters | Exact + IN / exact only | Exact + IN (D-09-07) |
| Documents & chunking | Size limit | 1 MB, 413 above / you decide | 1 MB (D-09-08) |
| Ingestion queue | Queue location | Postgres table / in-memory + sweep | Postgres table (D-09-09) |
| Ingestion queue | Ollama down | Accept + back off / reject 503 | Accept + back off (D-09-10) |
| Ingestion queue | Ordering | Last write wins / caller version | Last write wins (D-09-11) |
| Ingestion queue | When FAILED | Bad content only / after N retries | Bad content only (D-09-12) |
| Deployment & DB | Database location | Same container, own DB + user / separate container | Same container (D-09-13) |
| Deployment & DB | ragdb creation | Init script + bootstrap / service creates | Init script + bootstrap (D-09-14) |
| Deployment & DB | Default start | Yes always / opt-in profile | Yes (D-09-15) |
| Deployment & DB | Reach Ollama | Host Ollama via config / Ollama in compose | Host via config (D-09-16) |

Side request during the discussion: the user asked for Postgres to be published, so `127.0.0.1:5432` was added to compose.app.yaml (commit 7fc980b; D-09-17).
