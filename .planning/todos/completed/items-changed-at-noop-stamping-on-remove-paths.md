---
type: bug
created: 2026-08-11
source: gsd-code-fixer, while fixing WR-01 from 05-REVIEW.md
resolves_phase: 05
severity: low
files:
  - src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java
---

# `removeItem` / `removeManualItem` may stamp `items_changed_at` on a no-op

WR-01 in `05-REVIEW.md` fixed `addItems`, which stamped `items_changed_at` even when every
requested ticket was already present — falsely marking generated documents stale. The fix was
scoped to `addItems` only.

The reviewer noted, and the fixer confirmed while applying it, that `removeItem` and
`removeManualItem` in the same adapter follow the same shape and may stamp unconditionally even
when the targeted item was not present. That was outside the explicit fix scope and was
deliberately left untouched rather than silently widened.

## Why it matters

`items_changed_at` is the sole input to `DocumentReadinessService`'s staleness comparison
(DOC-09). A spurious stamp reports up-to-date paperwork as stale, which is the same user-visible
defect WR-01 described — just reached by a different path. Impact is low: it requires removing an
item that is already absent.

## What to do

Verify whether either remove path can stamp without an actual row change, and if so apply the same
guard `addItems` now uses (stamp only when the mutation actually affected rows). Add a regression
test alongside `addItemsThatAreAllAlreadyPresentDoesNotStampItemsChangedAt` in
`ReleasePersistenceIT`.

Related: [[adf-flattener-heading-and-codeblock-newlines]] was the other defect found by inspection
rather than by a failing test during this phase.
