---
phase: 03-release-assembly
reviewed: 2026-08-05T21:13:44Z
depth: standard
files_reviewed: 49
files_reviewed_list:
  - frontend/package.json
  - frontend/src/api/auth.ts
  - frontend/src/api/releases.ts
  - frontend/src/components/AddToReleaseDialog.tsx
  - frontend/src/components/AppShell.tsx
  - frontend/src/components/ConfirmDialog.tsx
  - frontend/src/router.tsx
  - frontend/src/routes/ReleaseDetailRoute.tsx
  - frontend/src/routes/ReleasesRoute.tsx
  - frontend/src/routes/TicketsRoute.tsx
  - frontend/tests/api/releases.test.ts
  - src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/converter/JsonStringListConverter.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseEntity.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseManualItemEntity.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseTicketItemEntity.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseJpaRepository.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseManualItemJpaRepository.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseSummaryRow.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseTicketItemJpaRepository.java
  - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
  - src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/AddTicketsRequest.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/CreateReleaseRequest.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/ManualItemRequest.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/ManualItemResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseAddResultResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseItemResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseSearchResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseSummaryResponse.java
  - src/main/java/com/evision/releasemgmt/application/ReleaseService.java
  - src/main/java/com/evision/releasemgmt/domain/release/AddTicketsResult.java
  - src/main/java/com/evision/releasemgmt/domain/release/ManualItem.java
  - src/main/java/com/evision/releasemgmt/domain/release/ManualItemCategory.java
  - src/main/java/com/evision/releasemgmt/domain/release/Release.java
  - src/main/java/com/evision/releasemgmt/domain/release/ReleaseDuplicateException.java
  - src/main/java/com/evision/releasemgmt/domain/release/ReleaseFrozenException.java
  - src/main/java/com/evision/releasemgmt/domain/release/ReleaseItem.java
  - src/main/java/com/evision/releasemgmt/domain/release/ReleaseNotFoundException.java
  - src/main/java/com/evision/releasemgmt/domain/release/ReleaseStatus.java
  - src/main/java/com/evision/releasemgmt/domain/release/ReleaseSummary.java
  - src/main/java/com/evision/releasemgmt/ports/ReleaseRepository.java
  - src/main/resources/db/migration/V3__release_assembly.sql
  - src/test/java/com/evision/releasemgmt/adapters/persistence/ReleasePersistenceIT.java
  - src/test/java/com/evision/releasemgmt/adapters/persistence/converter/JsonStringListConverterTest.java
  - src/test/java/com/evision/releasemgmt/adapters/web/ReleaseControllerTest.java
  - src/test/java/com/evision/releasemgmt/application/ReleaseServiceManualItemsTest.java
  - src/test/java/com/evision/releasemgmt/application/ReleaseServiceTicketsTest.java
findings:
  critical: 2
  warning: 15
  info: 0
  total: 17
status: issues_found
---

# Phase 3: Code Review Report

**Reviewed:** 2026-08-05T21:13:44Z
**Depth:** standard
**Files Reviewed:** 49
**Status:** issues_found

## Summary

Release assembly across the hexagonal backend (domain → ports → application → adapters) and the
React SPA. Injection surfaces were traced end to end and are sound; the defects that matter are
concentrated in invariant enforcement and in error paths that are written but never reached.

**What holds up under attack:**

- **JQL sink (AUTH-02 / injection).** `ReleaseService` imports neither `JiraAccessContext` nor
  `JiraTicketPort`; it reaches Jira only through `TicketSearchService`. Ticket keys are validated
  twice — `@Pattern` at the REST boundary (`AddTicketsRequest`, `CreateReleaseRequest`) and again
  at the JQL sink (`ReleaseService.TICKET_KEY`, line 150). `aKeyThatIsNotAValidJiraIssueKeyNeverReachesJql`
  proves the sink-side filter with a real break-out payload. No finding.
- **ILIKE escaping.** `ReleaseRepositoryAdapter.escapeLikeWildcards` (line 158) escapes `\` first,
  then `%` and `_`. That ordering is correct (escaping the escape character first cannot double-escape),
  the set is complete for `LIKE`/`ILIKE` (`SIMILAR TO`/regex metacharacters do not apply), and
  PostgreSQL's default `LIKE` escape character *is* backslash, so the missing explicit `ESCAPE '\'`
  clause is not a defect. The parameter is bound, never concatenated in Java. No finding.
- **No XSS surface.** No `dangerouslySetInnerHTML`, no `innerHTML`, no `eval`, no debug artifacts,
  no hardcoded secrets across any reviewed file.

**What does not hold up:** the FROZEN one-way door is enforced only in memory, in a different
transaction from the write it guards, with a multi-second Jira HTTP call inside the window (CR-01).
And the SPA's most important write path — adding tickets to an existing release — has no error
branch at all, so every `RELEASE_FROZEN`, `JIRA_UNREACHABLE` and `VALIDATION_FAILED` response is
swallowed silently (CR-02). Below those, a recurring theme: several paths report success while
quietly discarding information (WR-03, WR-04, WR-05).

---

## Critical Issues

### CR-01: FROZEN guard is a read-then-write race spanning an external HTTP call — BLOCKER

**File:** `src/main/java/com/evision/releasemgmt/application/ReleaseService.java:68-89`, `96-105`, `107-119`
**Also:** `src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java:76-79`, `113-116`

**Issue:** `requireOpen()` is evaluated against a `Release` snapshot loaded in one transaction; the
mutation runs in a *different* transaction opened later by `@Transactional` on the adapter. Nothing
at the persistence layer re-checks status: `addItems` inserts unconditionally, `delete` calls
`deleteById` unconditionally, `freeze` overwrites `frozen_at`/`frozen_by` unconditionally. The
`release` table has no status guard beyond `CHECK (status IN ('OPEN','FROZEN'))` and the entities
carry no `@Version`.

This is not a microsecond window. In `addTickets` (lines 69-87) and `refresh` (lines 97-104), the
guard is checked, then a paginated Jira `/search/jql` call runs — seconds for a large release — and
only then does the write happen. Two browser tabs (or a retried request) are enough:

```
tab A: addTickets  -> get() sees OPEN -> [Jira fetch, 4s] ------------> addItems() INSERT
tab B:                        freeze() -> UPDATE status=FROZEN ----^
```

Result: a FROZEN release whose ticket set changed after freeze. The phase's own doc calls freeze
"a one-way door" and D-03-11 calls a frozen release "an audit record"; both are violated, silently,
with no record that it happened. `delete` has the same shape — a frozen audit record can be deleted
by a delete that started while the release was still open.

**Fix:** enforce the invariant where the write happens, inside the same transaction. Load the entity
and re-check in the adapter (the release row is already fetched in `freeze`/`replaceItemSnapshots`;
`addItems` and `delete` need one extra `findById`):

```java
@Override
@Transactional
public Release addItems(UUID releaseId, List<ReleaseItem> items) {
    requireOpen(releaseId);
    insertItems(releaseId, items);
    return load(releaseId);
}

@Override
@Transactional
public void delete(UUID releaseId) {
    requireOpen(releaseId);
    jpa.deleteById(releaseId);
}

private void requireOpen(UUID releaseId) {
    ReleaseEntity entity = jpa.findById(releaseId).orElseThrow(ReleaseNotFoundException::new);
    if (entity.getStatus() == ReleaseStatus.FROZEN) {
        throw new ReleaseFrozenException();
    }
}
```

Apply the same check in `replaceItemSnapshots` (which already loads the row at line 95) and in
`freeze` (line 104), so double-freeze is a `ReleaseFrozenException` rather than an overwrite of the
original actor and timestamp.

### CR-02: Adding tickets to an existing release swallows every failure — BLOCKER

**File:** `frontend/src/components/AddToReleaseDialog.tsx:146-157`

**Issue:** `addTickets` is created at line 27 and invoked at line 149. Its `isError`/`error` is
never read anywhere in the component — only `createRelease`'s error is surfaced (lines 83-90,
202-208). The mutation's only handler is `onSuccess: goToRelease`.

So on the phase's primary action ("Add N tickets to release X"), every failure produces exactly
nothing: the button click does not navigate, no message appears, the dialog sits open unchanged.
This covers real, expected responses:

- `409 RELEASE_FROZEN` — the target was frozen in another tab since the list was cached (`useReleases('')`
  is fetched once when `TicketsRoute` mounts and only invalidated by this SPA's own mutations).
- `503 JIRA_UNREACHABLE` / `429 JIRA_RATE_LIMITED` / `502 JIRA_ERROR` — the add path calls Jira.
- `400 VALIDATION_FAILED` — selecting more than 500 tickets trips `@Size(max = 500)` on
  `AddTicketsRequest`.
- `404 RELEASE_NOT_FOUND` — the target was deleted elsewhere.

`messageForReason` already carries contracted copy for all of these (`frontend/src/api/auth.ts:63-79`);
none of it is wired up. Note also that the same gap hides create-path `ticketKeys` errors: `nameError`
and `versionError` only read `fieldErrors.name`/`.version`, so a `fieldErrors["ticketKeys[500]"]`
entry renders nothing either.

**Fix:** surface the add error the same way the list error is surfaced, and cover unmapped field
errors by falling back to `messageForReason`:

```tsx
const addProblem =
  addTickets.isError && addTickets.error instanceof ApiError ? addTickets.error.problem : undefined;
```

```tsx
{addProblem && (
  <p role="alert" data-reason={addProblem.reason} className="mt-2 text-sm text-red-600">
    {messageForReason(addProblem)}
  </p>
)}
```

and render `{createProblem && !nameError && !versionError && createProblem.reason !== 'RELEASE_DUPLICATE' && (...)}`
for the create form so a `ticketKeys` validation failure is not lost.

---

## Warnings

### WR-01: `ReleaseService.create` is dead code — WARNING

**File:** `src/main/java/com/evision/releasemgmt/application/ReleaseService.java:43-45`
**Issue:** `create(String, String, String, String)` has no caller in `src/main` or `src/test`. The
only creation path is `createWithTickets`, which `ReleaseController.create` (line 51) calls directly.
D-03-02 explicitly states there is no create-then-add path, so this method is a second door onto an
invariant that is supposed to have one. `CLAUDE.md` bans dead code outright.
**Fix:** delete lines 43-45.

### WR-02: `findByStatusOrderByCreatedAtDesc` is dead code with a comment that misstates the design — WARNING

**File:** `src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseJpaRepository.java:40-41`
**Issue:** No caller anywhere. Worse, the comment claims it exists because "Only OPEN releases are
valid targets for the Add-to-release picker (D-03-02)" — but the picker filters client-side via
`openReleases()` (`frontend/src/api/releases.ts:141-143`) over the unfiltered `GET /api/releases`
response. A future reader will believe there is server-side filtering that does not exist.
**Fix:** delete lines 40-41.

### WR-03: Requested ticket keys that Jira does not return vanish from both counters — WARNING

**File:** `src/main/java/com/evision/releasemgmt/application/ReleaseService.java:84-88`, `149-161`
**Issue:** `added` is `items.size()` — what Jira returned — while `alreadyPresent` counts only keys
already on the release. A key that Jira does not resolve (deleted issue, project the credential
cannot see, key moved to a new project) falls into neither bucket. `fetchTickets` also drops
regex-rejected keys at line 150 with no signal.

The UI then reports the shortfall as an unqualified success. `addResultMessage`
(`frontend/src/routes/ReleaseDetailRoute.tsx:22-26`) renders `added=1, alreadyPresent=0` from a
3-ticket selection as `"1 ticket(s) added to Sprint 42 2026.08."` — and `added=0, alreadyPresent=0`
(nothing resolved at all) as `"0 ticket(s) added to …"` after navigating to a release that did not
change. D-03-13's stated goal is "counts let the UI say so honestly"; the not-found bucket is not
counted, so it cannot be said. `aKeyJiraDoesNotReturnIsSimplyNotAdded` (ReleaseServiceTicketsTest.java:132)
pins the current behaviour without asserting anything about the missing key.
**Fix:** add a third counter and render it:

```java
public record AddTicketsResult(Release release, int added, int alreadyPresent, int notFound) { }
```

```java
int notFound = toFetch.size() - items.size();
return new AddTicketsResult(updated, items.size(), alreadyPresent, notFound);
```

### WR-04: `refresh` stamps `lastRefreshedAt` even when nothing was refreshed — WARNING

**File:** `src/main/java/com/evision/releasemgmt/application/ReleaseService.java:96-105`
**Also:** `src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java:90-99`
**Issue:** `replaceItemSnapshots` overwrites only the items Jira returned (`ifPresent`, line 92-93),
then unconditionally sets `last_refreshed_at = now` (line 96). If Jira returns a subset — or an
empty list, which `refreshFetchesInBatchesOfOneHundred` (ReleaseServiceTicketsTest.java:245) shows is
a state the code accepts without complaint — the stale rows keep their old field values while the UI
reports `"Last refreshed: <now>"` (`ReleaseDetailRoute.tsx:181-183`). A release manager reading
frozen-in-time ticket statuses under a fresh timestamp is exactly the "manual release bookkeeping"
error this system exists to prevent.
**Fix:** stamp only what was actually refreshed, or report the shortfall. Minimum: return the
refreshed count from the port and have the service refuse to stamp when `refreshed.size() != keys.size()`,
surfacing a partial-refresh reason instead.

### WR-05: Framework-generated ProblemDetails carry no `reason`, breaking the D-04 error contract — WARNING

**File:** `src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java:113-126`
**Issue:** Only `handleMethodArgumentNotValid` is overridden. Every other `ResponseEntityExceptionHandler`
path — `HttpMessageNotReadableException` (malformed JSON, unknown enum value),
`HttpRequestMethodNotSupportedException`, `HttpMediaTypeNotSupportedException`,
`NoResourceFoundException` — returns a ProblemDetail built by `createProblemDetail`, which never sets
the `reason` property. The SPA's entire error-copy table is keyed on `reason`
(`frontend/src/api/auth.ts:81-87`), and `rawFetch` degrades a missing one to `'UNKNOWN'`
(`frontend/src/api/client.ts`), so all of these render the generic
`"Something went wrong. Try again, and check the server logs if it persists."`

This is directly reachable in this phase: `POST /manual-items` with `"category":"SOMETHING_ELSE"`
takes exactly this path. The test that covers it
(`ReleaseControllerTest.java:479-489`) asserts only `is4xxClientError()` — deliberately weak enough
that it cannot detect the missing property, unlike its sibling at line 465 which does assert
`$.reason`.
**Fix:** override `handleExceptionInternal` once so every framework-produced ProblemDetail gets a
`reason`, and tighten the test to assert it:

```java
@Override
protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
        HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
    if (response != null && response.getBody() instanceof ProblemDetail pd && pd.getProperties() == null) {
        pd.setProperty("reason", "BAD_REQUEST");
    }
    return response;
}
```

### WR-06: Unknown manual-item id reports "Release not found"; unknown id on delete reports success — WARNING

**File:** `src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java:134-149`
**Issue:** `updateManualItem` throws `ReleaseNotFoundException` when `findByReleaseIdAndId` misses
(line 136), which the handler maps to `404` with detail *"This release doesn't exist or has been
deleted."* — false, and it will send a user to look for the wrong problem. Meanwhile
`removeManualItem` (line 147) uses `ifPresent(...)` and returns `200 OK` for the same nonexistent id.
Two adjacent methods, opposite contracts, both wrong in different directions.
**Fix:** pick one. Either both no-op (matching `removeItem`'s documented behaviour at line 83), or
introduce a distinct `ManualItemNotFoundException` mapped to a `MANUAL_ITEM_NOT_FOUND` reason and use
it in both.

### WR-07: Unique-constraint violation on `addItems` escapes as a 500 — WARNING

**File:** `src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java:76-79`
**Issue:** `create` catches `DataIntegrityViolationException` (lines 48-52), `addItems` does not. The
`release_ticket_item_key_unique (release_id, ticket_key)` constraint (V3 line 35) can fire from
`addItems` because `addTickets` de-duplicates against the *requested* keys while inserting the keys
Jira *returned* — and those differ when an issue has been moved between projects (a request for
`OLD-1` resolves and comes back as `NEW-5`; if `NEW-5` is already on the release, the insert
violates the constraint). Two concurrent adds of the same key do the same. The user gets
`500 INTERNAL_ERROR` plus a stack trace in the log for a condition the system understands perfectly.
**Fix:** de-duplicate against the returned keys rather than the requested ones, and catch the
constraint at the adapter:

```java
List<String> presentKeys = repository.findTicketKeys(releaseId);
List<ReleaseItem> items = fetchTickets(toFetch).stream()
        .filter(ticket -> !presentKeys.contains(ticket.key()))
        .map(ticket -> new ReleaseItem(ticket, now))
        .toList();
```

### WR-08: NOT NULL snapshot columns can be fed nulls from the Jira adapter — WARNING

**File:** `src/main/resources/db/migration/V3__release_assembly.sql:24-26`
**Also:** `src/main/java/com/evision/releasemgmt/adapters/jira/JiraRestTicketAdapter.java` (`toTicketDetail`)
**Issue:** `summary`, `issue_type` and `status` are `NOT NULL`, but the adapter builds them with
`.asText(null)` — which yields `null` whenever the field is absent from the response or the resolved
field id does not match (a field renamed or hidden by a Jira permission scheme, exactly the drift the
stack notes warn to expect). The result is a `DataIntegrityViolationException` at flush inside
`insertItems`, surfacing as `500 INTERNAL_ERROR` from an entirely foreseeable upstream condition.
`description` and `comments` are safe (`AdfFlattener.flatten` never returns null); these three are not.
**Fix:** default at the adapter boundary — `.asText("")` for `summary`, and a literal fallback such
as `"Unknown"` for `issueType`/`status` — so a partial Jira response degrades to visible placeholder
text rather than a 500.

### WR-09: `browsedProjectKey` ignores which mode produced the results — WARNING

**File:** `frontend/src/routes/TicketsRoute.tsx:88-90`
**Issue:** The comment states *"Null when the search ran from raw JQL, where there is no single
browsed project"* — the code does not implement it. `browsedProjectKey` is derived from the live
`projectId` select, regardless of `loadedMode`. The component already tracks `loadedMode` for exactly
this class of problem ("Deliberately not the live `jqlOpen` flag", line 26-28) and then does not use
it here. Select project CNV, switch to JQL, load tickets from project PAY, create a release: the
release is stamped `jiraProjectKey: "CNV"` and displays "Project: CNV" forever
(`ReleaseDetailRoute.tsx:172`), contradicting D-03-05.
**Fix:**

```tsx
const browsedProjectKey =
  loadedMode === 'jql' ? null : (projects.data?.find((p) => p.id === projectId)?.key ?? null);
```

### WR-10: `AddToReleaseDialog` keeps stale form state and stale errors across open/close — WARNING

**File:** `frontend/src/components/AddToReleaseDialog.tsx:29-32`
**Issue:** `filter`, `formOpenState`, `name` and `version` are component state, and the dialog is
permanently mounted by `TicketsRoute` (line 406). Nothing resets them on close. Cancel a create after
a `RELEASE_DUPLICATE`, reopen with a different ticket selection, and the form is still expanded,
still prefilled with the previous name/version, and `createRelease.isError` is still true — so the
duplicate error message from the *previous* attempt is rendered against the *new* selection
(line 204-208). Submitting it re-sends the same duplicate name/version.
**Fix:** reset on the open transition in the existing effect:

```tsx
useEffect(() => {
  const dialog = dialogRef.current;
  if (!dialog) return;
  if (props.open && !dialog.open) {
    setName('');
    setVersion('');
    setFilter('');
    setFormOpenState(false);
    createRelease.reset();
    addTickets.reset();
    dialog.showModal();
  }
  if (!props.open && dialog.open) dialog.close();
}, [props.open]);
```

### WR-11: "Add" button is not disabled while the add is in flight — WARNING

**File:** `frontend/src/components/AddToReleaseDialog.tsx:146-157`
**Issue:** The submit button on the create form is correctly guarded by `createRelease.isPending`
(line 212); the per-release "Add" buttons have no `disabled` at all. Because the request goes through
a Jira fetch it can take seconds with no visual change, which invites a second click — two `POST
/items` requests for the same selection. With WR-07 unfixed, the second can land as a 500.
**Fix:** `disabled={addTickets.isPending}` on the button at line 146, and a pending indicator
matching the one used for `releases.isPending` at lines 102-110.

### WR-12: A failed freeze or delete leaves the confirmation modal open with the error hidden behind it — WARNING

**File:** `frontend/src/routes/ReleaseDetailRoute.tsx:237-259`
**Issue:** `onConfirm` clears `confirming` only in `onSuccess` (lines 244, 256). On failure the
`<dialog>` stays open via `showModal()`, which makes the rest of the document inert — and
`actionProblem` (lines 224-228) renders *in that inert page content*, behind the backdrop. The user
sees an unchanged modal with a re-enabled button and no explanation, on the two irreversible actions
the dialog exists to make deliberate. This is the most likely place `RELEASE_FROZEN` from CR-01's
race actually shows up, and it is exactly where it cannot be read.
**Fix:** render the error inside `ConfirmDialog` — add an optional `problem` prop and display it
above the button row — or close the dialog on settle (`onSettled: () => setConfirming(null)`) so the
page-level alert becomes visible.

### WR-13: "+ Add manual item" after an Edit reuses the edited item's values — WARNING

**File:** `frontend/src/routes/ReleaseDetailRoute.tsx:460-471`
**Issue:** The toggle button clears `editingId` but not `title`, `description` or `category`, which
`startEditingManualItem` (lines 133-139) populated from the row being edited. Sequence: click Edit on
"Run migration" → click "+ Add manual item" (closes) → click again (opens). The form now reads as a
*create* form (button says "Add item", `editingId` is null) prefilled with "Run migration". Submitting
creates a duplicate manual item — and since `release_manual_item` has no uniqueness constraint (V3
lines 38-46) it persists silently, appearing twice in a title-sorted list.
**Fix:** reuse the reset already written at lines 125-131:

```tsx
onClick={() => {
  if (formOpen) resetManualItemForm();
  else { setEditingId(null); setTitle(''); setDescription(''); setCategory('OTHER'); setFormOpen(true); }
}}
```

### WR-14: Deleting a release refetches it before navigating away — WARNING

**File:** `frontend/src/api/releases.ts:215-224`
**Issue:** `removeQueries` on a query that still has an active observer (the `useRelease(releaseId)`
mounted by `ReleaseDetailRoute`) causes that observer to re-create and refetch the query. The hook's
`onSuccess` runs before the per-call `onSuccess` that navigates, so a `GET /api/releases/{id}` fires
against the release that was just deleted, returns 404, and — with `retry: false` — can paint the
`RELEASE_NOT_FOUND` error state for a frame before the route changes.
**Fix:** invalidate the list and let the navigation unmount the detail query, then drop the cache
entry afterwards:

```ts
onSuccess: (_data, releaseId) => {
  queryClient.invalidateQueries({ queryKey: ['releases'] });
  queryClient.removeQueries({ queryKey: releaseQueryKey(releaseId), type: 'inactive' });
},
```

### WR-15: `ReleasePersistenceIT` asserts exact result sets against an accumulating shared database — WARNING

**File:** `src/test/java/com/evision/releasemgmt/adapters/persistence/ReleasePersistenceIT.java:29-33`, `182-208`
**Issue:** One static container, no `@Transactional`, no `@Sql` cleanup, no per-test truncation — every
test method leaves its rows behind for the next. Several assertions are exact-set:
`search("sprint")` … `containsExactly(sprint.id())` (line 188), `search("2026")` …
`containsExactly(sprint.id())` (line 190), and `search("%")`/`search("_")` … `isEmpty()` (lines 203-204).
They pass today only because no other test in the class happens to create a matching name or version.
The next test added with "Sprint" in its name, or a rerun in the same container, breaks them — and
the failure will look like a wildcard-escaping regression (the security-relevant assertion) rather
than a fixture collision.
**Fix:** add cleanup between methods so each test owns its data:

```java
@AfterEach
void clearReleases() {
    jdbcTemplate.execute("TRUNCATE release CASCADE");
}
```

### WR-16: `npm run lint` runs no linter — WARNING

**File:** `frontend/package.json:11`
**Issue:** `"lint": "tsc -b"` is byte-identical to the first half of `"build"`. There is no ESLint
config or dependency in the project, so `npm run lint` returns green without checking a single lint
rule — a CI step named "lint" that provably cannot fail on a lint violation is worse than no step,
because it reads as coverage that does not exist. Several findings in this review
(`addTickets.isError` never read in CR-02, unused `Principal` shadowing, `no-floating-promises`
patterns) are in the class a linter catches automatically.
**Fix:** either add ESLint with `typescript-eslint` and point `lint` at it, or rename the script to
`typecheck` so it stops claiming to be something it is not.

---

_Reviewed: 2026-08-05T21:13:44Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
