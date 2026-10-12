---
phase: 06-azure-devops-enrichment
plan: 03
subsystem: persistence
tags: [jira, flyway, jpa, enrichment, storage]
requires: [06-01, 06-02]
provides:
  - TicketDetail carries jiraIssueId, updatedAt, statusCategory from /search/jql
  - V9 schema (enrichment columns + pull request, commit, status change child tables)
  - ReleaseItem.codeLinks populated on every release load via three batched queries
  - ReleaseRepository.saveEnrichment (locked, fingerprint-stamped single write path)
affects: [06-05, 06-06, 06-07, 08]
key-files:
  created:
    - src/main/resources/db/migration/V9__ado_enrichment.sql
    - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ (ReleaseItemPullRequestEntity, ReleaseItemCommitEntity, ReleaseItemStatusChangeEntity)
    - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ (three child JPA repositories)
    - src/test/java/com/evision/releasemgmt/adapters/persistence/ReleaseEnrichmentPersistenceIT.java
  modified:
    - TicketDetail, JiraRestTicketAdapter, ReleaseItem, ReleaseService, ReleaseRepository, ReleaseRepositoryAdapter, ReleaseTicketItemEntity
    - eight test files updated for the new constructors
key-decisions:
  - "Child delete queries use flushAutomatically but not clearAutomatically, so entities held in the saveEnrichment transaction stay managed"
requirements-completed: [ADO-01, ADO-03, INSIGHT-03]
duration: ~35min
completed: 2026-10-12
---

# Phase 6 Plan 03: Enrichment storage and write path Summary

Every Jira-backed release item can now store its code links and status history: search captures the numeric issue id, tenant-offset `updated` time and status category, V9 adds the schema (existing rows read NOT_ATTEMPTED), release load returns `CodeLinks` through three batched child queries, and `saveEnrichment` is the one write path, taking the release row lock and stamping `items_changed_at` only when documents would render differently.

## Tasks

| Task | Commit | Result |
|------|--------|--------|
| 1. TicketDetail id/updated/category | d04ee3b | adapter adds `updated` to requested fields; +0300 fixture honoured; malformed time yields null; 2 new tests |
| 2. V9, entities, read path | e662945 | Flyway applies V9, Hibernate validate passes; 3 new persistence tests |
| 3. saveEnrichment | 81da698 | 10 IT tests: round trip, no-op, NOT_ATTEMPTED/FAILED to NO_LINK, LINKED to FAILED keeps rows and stamps, frozen refusal, unknown key, cascade |

Full suite (`mvn -P '!frontend' clean test`): 692 tests, 0 failures (was 678 after wave 1).

## Deviations from Plan

- TDD red commit for Task 3 was not made separately; tests and implementation landed in one commit, verified green. No behaviour impact.
- Task 1 and 2 were committed in the planned order, with `toDomain` passing `null, null, null` in between as the plan prescribed.
- Child JPQL deletes use `@Modifying(flushAutomatically = true)` only. `clearAutomatically` would detach the release and item entities loaded earlier in the same `saveEnrichment` transaction.

## Known Stubs

None.

## Notes for later plans

- `release_item_commit.authored_at` is NOT NULL (per plan). A commit with a null author date would fail the write; the ADO mapper in 06-05/06-08 must always supply one or drop the commit.
- Files in this worktree are CRLF in the index normalisation sense; new files were written with LF and git warns on add only.

## Threat Flags

None beyond the plan's threat model (T-06-11 lock + frozen IT, T-06-12 JPA-bound writes only, T-06-13 batched IN loads, T-06-14 stamping rule tested).

## Self-Check: PASSED

Commits d04ee3b, e662945, 81da698 exist; V9 migration, three entities, three repositories and the new IT file are present; full backend suite green.
