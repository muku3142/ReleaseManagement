---
phase: 09-rag-service-foundation-index
plan: 11
subsystem: rag-service
tags: [rollout, live-stack, pgvector, ollama, verification]
requirements: [RAG-01, RAG-02, RAG-03, RAG-04, RAG-05, RAG-10]
status: complete
---

# Phase 9 Plan 11: Live rollout and end-to-end verification Summary

Task 1 (live rollout plus end-to-end and outage drills against the real host Ollama) passed on every step with no code changes. Task 2 (human verification) was approved by the user on 2026-10-13.

## Task 1 evidence

| Step | Outcome |
|------|---------|
| 1 Provenance | postgres-1 and app-1 both have `com.docker.compose.project.config_files` = `D:\IdeaProjects\ReleaseManagement\compose.app.yaml`. The stop condition did not trigger. Before: postgres image `postgres:18`. |
| 2 Secrets | `RAG_ADMIN_KEY` and `RAG_DB_PASSWORD` were missing from `.env`, so both were appended (the file is gitignored and ended with a newline). No values were printed. |
| 3 Backup | `C:\Users\ucs\releasemanagement-backups\releasemgmt-pre-pgvector-202610131407.dump`, 327,748 bytes, pg_dump exit 0. Baseline flyway_schema_history count: 10. |
| 4 Image swap | `up -d postgres` recreated the container. It reached `healthy`. Image `pgvector/pgvector:0.8.7-pg18-trixie`. flyway_schema_history count 10, equal to baseline. |
| 5 One-off | `compose exec ... postgres bash /docker-entrypoint-initdb.d/10-ragdb.sh` exited 0. `ragdb` has extension `vector` and role `rag` exists. This confirms RESEARCH assumption A1. Note: running it non-interactively needed `exec -T`. The documented form works in a terminal. |
| 6 Service | `up --build -d rag-service` reached `healthy`. `releasemanagement-app-1` stayed up 3 hours, healthy. v1 `http://localhost:8080/actuator/health` (RM_PORT=8080) returned 200. rag readiness is UP. |
| 7a | Create client returned 201. No auth returned 401. Wrong key returned 401. |
| 7b | `PUT /v1/collections/smoke` returned 201 with `nomic-embed-text`. |
| 7c | Three documents pushed (runbook R1, backup R1, holiday R2), each 202. |
| 7d | indexed=3 after 5 s. lastIndexedAt 2026-10-13T11:09:01Z. |
| 7e | Query "how do we back up the database": rank 1 backup (score 0.768), rank 2 holiday (0.522), rank 3 runbook (0.472). Every hit has documentId, title, url and metadata. Filter release_id=R2 returned only holiday. |
| 7f | Unchanged re-push returned 202 with status INDEXED. `indexedAt` unchanged. |
| 7g | `reindex` returned 202 with requeued=3. indexed returned to 3 and lastIndexedAt advanced. |
| 7h | Delete returned 204. The repeat search returned holiday and runbook only, with backup absent. |
| 7i | The second client's GET `/v1/collections/smoke` returned 404. |
| 7j | `/v3/api-docs` returned 200 and contains `/v1/collections/{collection}/search`. |

### Outage drill (success criterion 4)

- Ollama killed (both processes) and confirmed down via the failing `/api/version` call.
- Push during the outage returned 202. The document was PENDING and `/v1/status` returned `{"ollama":"DOWN","pending":1,"failed":0}`.
- After 20 s the document was still PENDING with a null reason, not FAILED.
- Ollama restarted through the tray app and answered `/api/version` 9 s later.
- The document was INDEXED by the first poll (about 12 s or less after restart, including the poll-start delay), with no caller action. `/v1/status` returned `{"ollama":"UP","pending":0,"failed":0}`.
- Ollama is running again.

### Log hygiene (step 9)

Searched the rag-service container log (44 lines) for distinctive phrases from the three document texts, the outage document, the query and the titles: 0 matches for each.

### Cleanup (step 10)

Both smoke clients were revoked (204 each). The `smoke` collection is left in place because there is no delete endpoint. It holds documents runbook, holiday and outage (backup was deleted).

### Automated verify

Printed `OK`.

## Deviations from Plan

None. No code changes and no fix commits. Minor note: the docs one-off command works unchanged in an interactive terminal. Add `-T` for non-TTY use. Not changed.

## Task 2: human verification

Approved by the user on 2026-10-13. The user replied with the resume signal "approved" only. No per-step observations were supplied, so the outcomes below are **asserted, not observed**:

- Step 1, v1 app unaffected at `http://localhost:8080`: approved, no observation given. Automated evidence: `/actuator/health` returned 200 and the flyway history count was unchanged (Task 1, steps 4 and 6).
- Step 2, swagger-ui at `http://localhost:8081/swagger-ui/index.html`: approved, no observation given. Automated evidence: `/v3/api-docs` returned 200 and contains the search path (7j).
- Step 3, Task 1 results reviewed: approved.
- (a) `RAG_ADMIN_KEY` required by compose up: accepted.
- (b) One-way isolation (`rag` cannot read `releasemgmt`; the superuser can read `ragdb`): accepted.

Residual: nobody has walked through a v1 release or document flow by hand on the pgvector image. That flow is covered only by the 946-test main suite (09-01) and the health check.
