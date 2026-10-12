---
phase: quick-261012-nqd
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/java/com/evision/releasemgmt/application/EnrichmentService.java
  - src/main/java/com/evision/releasemgmt/application/TicketSearchService.java
  - src/main/java/com/evision/releasemgmt/domain/jira/TicketNotFoundException.java
  - src/main/java/com/evision/releasemgmt/adapters/web/TicketController.java
  - src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java
  - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/CodeLinksResponse.java
  - src/test/java/com/evision/releasemgmt/application/EnrichmentServiceTest.java
  - src/test/java/com/evision/releasemgmt/adapters/web/TicketControllerTest.java
  - frontend/src/api/tickets.ts
  - frontend/src/components/TicketBrowser.tsx
  - frontend/tests/components/TicketBrowserCodeLinks.test.tsx
autonomous: true
requirements: [QUICK-261012-nqd]

must_haves:
  truths:
    - "In the ticket browser (browse screen and Add-tickets dialog), an expanded ticket row shows a 'Show linked commits' button"
    - "Nothing is requested from /api/tickets/{key}/code-links until that button is clicked"
    - "After clicking, the row renders CodeLinksDetail with the live result: commits/PRs, 'No linked code.', or 'Couldn't fetch (reason)'"
    - "GET /api/tickets/{key}/code-links writes nothing to any release table and never reads the Jira changelog"
    - "ADO/dev-status failures and timeouts come back as 200 with status FAILED, never as a 5xx"
    - "Unknown key returns 404 TICKET_NOT_FOUND; a key not matching ^[A-Z][A-Z0-9]*-[0-9]+$ returns 400 before any Jira call"
  artifacts:
    - path: "src/main/java/com/evision/releasemgmt/application/EnrichmentService.java"
      provides: "public CodeLinks lookup(TicketDetail ticket)"
      contains: "public CodeLinks lookup("
    - path: "src/main/java/com/evision/releasemgmt/adapters/web/TicketController.java"
      provides: "GET /{key}/code-links"
      contains: "code-links"
    - path: "src/main/java/com/evision/releasemgmt/adapters/web/dto/CodeLinksResponse.java"
      provides: "static from(CodeLinks) shared by ReleaseController and TicketController"
      contains: "static CodeLinksResponse from("
    - path: "frontend/tests/components/TicketBrowserCodeLinks.test.tsx"
      provides: "button -> fetch -> CodeLinksDetail test"
  key_links:
    - from: "TicketController.codeLinks"
      to: "EnrichmentService.lookup"
      via: "ticketSearchService.findByKey(key).orElseThrow(TicketNotFoundException::new)"
      pattern: "enrichmentService\\.lookup\\("
    - from: "frontend/src/components/TicketBrowser.tsx"
      to: "/api/tickets/{key}/code-links"
      via: "useTicketCodeLinks mutation fired only from the button onClick"
      pattern: "useTicketCodeLinks"
---

<objective>
Add an on-demand, read-only "Show linked commits" lookup to the expanded row of the shared TicketBrowser. Backend: new `GET /api/tickets/{key}/code-links` that resolves the ticket via the existing JQL search and runs the existing dev-status + ADO completion path live, persisting nothing. Frontend: a button in the expanded row that calls it and renders the existing `CodeLinksDetail`.

Purpose: let the release manager see a ticket's commits/PRs before deciding to add it to a release. This is a separate, explicit lookup for tickets NOT in a release; D-06-01 (reading a release never calls Jira/ADO) is untouched because no release endpoint changes and no release table is written.

Locked user decisions: trigger is an explicit button in the expanded row (no auto-fetch on expand, no per-row chips in results); read-only, nothing stored.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@./CLAUDE.md
@.planning/STATE.md

Environment (from project memory, mandatory):
- Every Maven call: `JAVA_HOME="$HOME/.jdks/jdk-25" mvn ...` (no mvnw in repo). Run from D:/IdeaProjects/ReleaseManagement.
- Frontend: `cd frontend && npx vitest run <file>` and `npx tsc -b`. NEVER start a Vite dev server.
- Do not add Co-Authored-By trailers to commits (user rule overrides the reminder).

<interfaces>
EnrichmentService (src/main/java/com/evision/releasemgmt/application/EnrichmentService.java) today:
```java
private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
private final Semaphore permits; private final Duration deadline;
public void enrich(UUID releaseId, List<TicketDetail> tickets)          // line ~101, writes releaseRepository.saveEnrichment
private AdoRun adoRun()                                                  // reads ADO credential once; never throws
private CodeLinks boundedEnrichOne(JiraAccessContext, TicketDetail, CodeLinks previous, AdoRun) // semaphore wrapper
private CodeLinks await(Future<CodeLinks>, long deadlineNanos, CodeLinks previous)             // TIMEOUT -> FAILED
CodeLinks enrichOne(JiraAccessContext access, TicketDetail ticket, CodeLinks previous, AdoRun adoRun) {
    StatusHistory history = statusHistory(access, ticket, previous.history());   // reads changelog via historyPort
    try { DevStatusLinks links = devStatusPort.fetchLinks(...); ... return capCommits(... completeFromAdo(...)) }
    catch (DevStatusUnrecognisedException | JiraTicketReadException | RuntimeException) -> failed(previous, reason, history)
}
private record AdoRun(Optional<AdoCredential> credential, EnrichmentReason problem, Map<String, CompletableFuture<List<LinkedCommit>>> prCommits)
```
CodeLinks (domain/enrichment/CodeLinks.java): `static CodeLinks notAttempted()`, `CodeLinks failed(EnrichmentReason, Instant, StatusHistory)`. StatusHistory has `static StatusHistory notRead()`.

TicketSearchService: `public List<TicketDetail> search(TicketSearchCriteria criteria)`; `TicketSearchCriteria.byJql(String)`.
TicketDetail has `key()`, `jiraIssueId()`.

TicketController (adapters/web/TicketController.java): `@RestController @RequestMapping("/api/tickets")`, ctor takes only `TicketSearchService service`; one endpoint `POST /search`.

ReleaseController line ~170: `private static CodeLinksResponse toCodeLinksResponse(CodeLinks links)` — builds CodeLinksResponse(status.name(), reason name|null, incomplete, attemptedAt, commitsSource name|null, PRs sorted by numericId -> PullRequestLinkResponse(id,title,status,url,sourceBranch,targetBranch,countsTowardCommits), commits -> CommitLinkResponse(sha,shortSha,message,author,authoredAt,url), branches).

Ticket key pattern used elsewhere: `^[A-Z][A-Z0-9]*-[0-9]+$` (AddTicketsRequest, ReleaseService.TICKET_KEY).
GlobalExceptionHandler extends ResponseEntityExceptionHandler; 404 pattern: `@ExceptionHandler(ReleaseNotFoundException.class) ... problem(HttpStatus.NOT_FOUND, "Release not found", "<detail>", "RELEASE_NOT_FOUND")`. Spring 7 built-in method validation turns a failed `@Pattern` on a `@PathVariable` into HandlerMethodValidationException, which the superclass maps to 400.

Frontend:
- frontend/src/api/tickets.ts: `import { useMutation } from '@tanstack/react-query'; import { apiFetch } from './client';` exports Ticket, useTicketSearch (a useMutation).
- frontend/src/api/releases.ts: `export type CodeLinks = {...}` (line ~57) — import the type, do NOT modify releases.ts.
- frontend/src/components/CodeLinks.tsx: `export function CodeLinksDetail({ links }: { links: CodeLinks })` — renders "Code links" heading, "Couldn't fetch (reason)." for FAILED, "No linked code." for empty, links only via safeAdoHref.
- frontend/src/components/TicketBrowser.tsx: expanded row at line ~372 `{expandedKey === ticket.key && (<tr><td colSpan={9}>...<div className="max-h-80 overflow-y-auto ...">` with Description then Comments sections. Error rendering pattern: `x.isError && x.error instanceof ApiError ? x.error.problem : undefined` then `{messageForReason(problem)}` (imports ApiError from '../api/client', messageForReason from '../api/auth').
- Test conventions: frontend/tests/components/AddTicketsToReleaseDialog.test.tsx (createRoot + act, QueryClientProvider, vi-mocked fetch, TICKETS fixture with key CNV-1).
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Read-only EnrichmentService.lookup + TicketSearchService.findByKey</name>
  <files>src/main/java/com/evision/releasemgmt/application/EnrichmentService.java, src/main/java/com/evision/releasemgmt/application/TicketSearchService.java, src/test/java/com/evision/releasemgmt/application/EnrichmentServiceTest.java</files>
  <read_first>
    - src/main/java/com/evision/releasemgmt/application/EnrichmentService.java (whole file, ~380 lines)
    - src/test/java/com/evision/releasemgmt/application/EnrichmentServiceTest.java lines 56-110 and 271-290 (setUp, service factory, devStatus/previousRelease helpers)
    - src/main/java/com/evision/releasemgmt/application/TicketSearchService.java
  </read_first>
  <behavior>
    - lookup on a ticket whose dev-status returns PRs/commits -> status LINKED, commits present; `verifyNoInteractions(repository, historyPort)` (nothing stored, no changelog read)
    - lookup on empty dev-status -> NO_LINK, still no repository/historyPort interaction
    - dev-status throws JiraTicketReadException(JIRA_UNREACHABLE) -> returns FAILED with reason JIRA_UNREACHABLE, does not throw
    - ticket with jiraIssueId null -> returns CodeLinks.notAttempted() without calling devStatusPort
  </behavior>
  <action>
    In EnrichmentService:
    1. Split `enrichOne` so status history can be skipped: extract the existing try/catch body into `private CodeLinks discover(JiraAccessContext access, TicketDetail ticket, CodeLinks previous, AdoRun adoRun, StatusHistory history)` (identical logic, uses the `history` argument everywhere `history` is used today). `enrichOne(access, ticket, previous, adoRun)` keeps its signature and becomes `return discover(access, ticket, previous, adoRun, statusHistory(access, ticket, previous.history()));` so existing tests and `enrich` are unchanged.
    2. Generalise the semaphore wrapper: replace `boundedEnrichOne(access, ticket, previous, adoRun)` with `private CodeLinks bounded(CodeLinks previous, Supplier<CodeLinks> work)` (same acquire / interrupted -> timedOut(previous) / finally release). `enrich` submits `() -> bounded(previous, () -> enrichOne(access, ticket, previous, adoRun))`.
    3. Add the public method, with a one-line comment that it is the on-demand ticket-browser lookup and stores nothing (status history is skipped because there is nowhere to keep it):
    ```java
    public CodeLinks lookup(TicketDetail ticket) {
        if (ticket.jiraIssueId() == null) return CodeLinks.notAttempted();
        JiraAccessContext access = accessContextService.currentContext();
        AdoRun adoRun = adoRun();
        CodeLinks none = CodeLinks.notAttempted();
        Future<CodeLinks> future = executor.submit(() -> bounded(none,
                () -> discover(access, ticket, none, adoRun, StatusHistory.notRead())));
        return await(future, System.nanoTime() + deadline.toNanos(), none);
    }
    ```
    This honours the same permits, deadline (TIMEOUT -> FAILED), maxCommitsPerTicket cap and ADO degradation as the release path, and never touches releaseRepository or historyPort.

    In TicketSearchService add:
    ```java
    public Optional<TicketDetail> findByKey(String key) {
        return search(TicketSearchCriteria.byJql("key = " + key)).stream().findFirst();
    }
    ```
    The key is already validated against `^[A-Z][A-Z0-9]*-[0-9]+$` by the controller (Task 2) before reaching here, so it cannot inject JQL; reuses the existing /search/jql port and field resolution — no new Jira call shape.

    Add the four behaviour tests above to EnrichmentServiceTest (reuse the existing `devStatus(...)` helper and mocks; build the TicketDetail the same way existing tests do).
  </action>
  <verify>
    <automated>cd D:/IdeaProjects/ReleaseManagement && JAVA_HOME="$HOME/.jdks/jdk-25" mvn -q test -Dtest=EnrichmentServiceTest,TicketSearchServiceTest</automated>
  </verify>
  <done>`public CodeLinks lookup(TicketDetail` and `public Optional<TicketDetail> findByKey(` exist; all EnrichmentServiceTest tests (old and new) pass; new tests assert `verifyNoInteractions(repository, historyPort)`.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: GET /api/tickets/{key}/code-links with shared CodeLinksResponse.from</name>
  <files>src/main/java/com/evision/releasemgmt/adapters/web/TicketController.java, src/main/java/com/evision/releasemgmt/adapters/web/dto/CodeLinksResponse.java, src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java, src/main/java/com/evision/releasemgmt/domain/jira/TicketNotFoundException.java, src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java, src/test/java/com/evision/releasemgmt/adapters/web/TicketControllerTest.java</files>
  <read_first>
    - src/main/java/com/evision/releasemgmt/adapters/web/TicketController.java
    - src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java lines 1-40 (imports) and 160-185 (toCodeLinksResponse)
    - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java lines 110-135 (ReleaseNotFoundException handler and problem(...) helper)
    - src/main/java/com/evision/releasemgmt/domain/release/ReleaseNotFoundException.java (shape to copy)
    - src/test/java/com/evision/releasemgmt/adapters/web/TicketControllerTest.java lines 1-80 and 195-210 (setup, authenticated(), unauthenticated test)
  </read_first>
  <behavior>
    - GET /api/tickets/CNV-1/code-links (authenticated), service returns LINKED with one commit -> 200, `$.status`="LINKED", `$.commits[0].shortSha` present
    - NO_LINK result -> 200, `$.status`="NO_LINK"
    - findByKey returns Optional.empty() -> 404 with code TICKET_NOT_FOUND, enrichmentService never called
    - GET /api/tickets/cnv-1/code-links -> 400, `verifyNoInteractions(ticketSearchService, enrichmentService)`
    - unauthenticated GET -> 401
  </behavior>
  <action>
    1. Move the mapping: add `public static CodeLinksResponse from(CodeLinks links)` to the CodeLinksResponse record containing exactly the body of `ReleaseController.toCodeLinksResponse`; delete the private method from ReleaseController and call `CodeLinksResponse.from(item.codeLinks())` in `toItemResponse`. Remove imports ReleaseController no longer uses (e.g. Comparator, LinkedPullRequest, PullRequestLinkResponse, CommitLinkResponse) only if now unused. This keeps the JSON identical to the release page so the frontend `CodeLinks` type matches.
    2. Create `domain/jira/TicketNotFoundException extends RuntimeException` (no-arg, same shape as ReleaseNotFoundException). In GlobalExceptionHandler add a handler next to ReleaseNotFoundException: `problem(HttpStatus.NOT_FOUND, "Ticket not found", "Jira has no ticket with this key, or you cannot see it.", "TICKET_NOT_FOUND")`.
    3. TicketController: add `EnrichmentService enrichmentService` to the constructor and:
    ```java
    // Live, read-only lookup for the ticket browser; stores nothing (D-06-01 governs release reads, not this).
    @GetMapping("/{key}/code-links")
    public CodeLinksResponse codeLinks(@PathVariable @Pattern(regexp = "^[A-Z][A-Z0-9]*-[0-9]+$") String key) {
        TicketDetail ticket = service.findByKey(key).orElseThrow(TicketNotFoundException::new);
        return CodeLinksResponse.from(enrichmentService.lookup(ticket));
    }
    ```
    Use `jakarta.validation.constraints.Pattern`. GET needs no CSRF token; it is authenticated by the existing /api/** security rule (do not touch SecurityConfig). If the 400 case returns 500 instead (method validation not triggered), add `@Validated` on the class rather than changing the handler.
    4. TicketControllerTest: add `@MockitoBean EnrichmentService enrichmentService;` and the five tests above (`get(...)` with `.with(authenticated())`, no csrf needed).
    5. Run ArchitectureBoundaryTest too — the DTO depending on domain.enrichment mirrors what ReleaseController already did.
  </action>
  <verify>
    <automated>cd D:/IdeaProjects/ReleaseManagement && JAVA_HOME="$HOME/.jdks/jdk-25" mvn -q test -Dtest=TicketControllerTest,ReleaseControllerTest,ArchitectureBoundaryTest,EnrichmentServiceTest</automated>
  </verify>
  <done>All four test classes green; `grep -n "toCodeLinksResponse" src/main/java -r` returns nothing; `grep -n "code-links" src/main/java/com/evision/releasemgmt/adapters/web/TicketController.java` matches the GetMapping; no file under adapters/persistence or ReleaseRepository changed.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 3: "Show linked commits" button in the expanded TicketBrowser row</name>
  <files>frontend/src/api/tickets.ts, frontend/src/components/TicketBrowser.tsx, frontend/tests/components/TicketBrowserCodeLinks.test.tsx</files>
  <read_first>
    - frontend/src/components/TicketBrowser.tsx lines 1-30, 75-85, 235-250, 355-410
    - frontend/src/api/tickets.ts (whole file)
    - frontend/src/components/CodeLinks.tsx lines 98-150 (CodeLinksDetail)
    - frontend/tests/components/AddTicketsToReleaseDialog.test.tsx (first ~120 lines: render harness, fetch mock, how a search is run and a row expanded)
  </read_first>
  <behavior>
    - Run a search, expand CNV-1: a button "Show linked commits" is visible and fetch has NOT been called with a URL containing "/code-links"
    - Click it: fetch called once with "/api/tickets/CNV-1/code-links" (GET); LINKED response renders the "Code links" heading and the commit's shortSha
    - FAILED response with reason JIRA_UNREACHABLE (no commits) renders text starting "Couldn't fetch ("
    - Collapsed rows never show the button
  </behavior>
  <action>
    1. frontend/src/api/tickets.ts: add
    ```ts
    import type { CodeLinks } from './releases';
    // A live Jira/ADO lookup on explicit request, so a mutation: nothing fires on render or expand.
    export function useTicketCodeLinks() {
      return useMutation({
        mutationFn: (ticketKey: string) =>
          apiFetch<CodeLinks>(`/api/tickets/${encodeURIComponent(ticketKey)}/code-links`),
      });
    }
    ```
    Do not modify api/releases.ts.
    2. TicketBrowser.tsx: add a small component in the same file:
    ```tsx
    function TicketCodeLinks({ ticketKey }: { ticketKey: string }) {
      const lookup = useTicketCodeLinks();
      const problem = lookup.isError && lookup.error instanceof ApiError ? lookup.error.problem : undefined;
      ...
    }
    ```
    Render: if `lookup.data` -> `<CodeLinksDetail links={lookup.data} />`; else a `type="button"` "Show linked commits" (label "Looking up…" and disabled while `lookup.isPending`) that calls `lookup.mutate(ticketKey)`; if `lookup.isError` show `messageForReason(problem)` in the same red text style used for searchProblem (~line 243) and keep the button so the user can retry. Place `<TicketCodeLinks ticketKey={ticket.key} />` inside the expanded row's scroll div after the Comments section. Because it unmounts on collapse, re-expanding shows the button again (no automatic re-fetch). Style the button like the existing secondary buttons in the file. This applies to AddTicketsToReleaseDialog automatically since it renders TicketBrowser.
    3. New test frontend/tests/components/TicketBrowserCodeLinks.test.tsx using the AddTicketsToReleaseDialog.test.tsx harness (or render TicketBrowser directly with `actionLabel` and `onAction={vi.fn()}`), covering the four behaviours. Fixture CodeLinks objects must include every field of the `CodeLinks` type; commit url `https://dev.azure.com/org/proj/_git/repo/commit/abc`.
  </action>
  <verify>
    <automated>cd D:/IdeaProjects/ReleaseManagement/frontend && npx vitest run tests/components/TicketBrowserCodeLinks.test.tsx tests/components/AddTicketsToReleaseDialog.test.tsx tests/components/TicketsRoute.test.tsx tests/components/CodeLinks.test.tsx && npx tsc -b</automated>
  </verify>
  <done>New test file passes; existing TicketBrowser-consuming tests still pass; `tsc -b` clean; `grep -n "useTicketCodeLinks" frontend/src/components/TicketBrowser.tsx` matches; no useEffect or useQuery triggers the code-links fetch.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser -> GET /api/tickets/{key}/code-links | untrusted path variable, authenticated user |
| app -> Jira /search/jql and dev-status, app -> ADO | outbound with the user's Jira credential and the org ADO PAT |
| ADO/dev-status response -> rendered links | untrusted URLs shown in the UI |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-nqd-01 | Tampering | TicketController.codeLinks key -> JQL `key = X` | mitigate | `@Pattern("^[A-Z][A-Z0-9]*-[0-9]+$")` on the path variable; 400 test asserts no service interaction |
| T-nqd-02 | Spoofing | new GET route | mitigate | existing /api/** authenticated rule; 401 test |
| T-nqd-03 | Denial of Service | live lookup fan-out | mitigate | lookup runs through the same semaphore, deadline and commit cap as release enrichment |
| T-nqd-04 | Information Disclosure | ADO PAT | mitigate | reuses adoRun(); token never in response/logs; Jira access uses the caller's own credential so they only see what Jira lets them see |
| T-nqd-05 | Tampering (XSS/open link) | CodeLinksDetail | mitigate | reuse existing component: links only via safeAdoHref (https://dev.azure.com/) |
| T-nqd-06 | Tampering | release data | mitigate | lookup never calls releaseRepository; service test asserts verifyNoInteractions(repository, historyPort) |
</threat_model>

<verification>
- `JAVA_HOME="$HOME/.jdks/jdk-25" mvn -q test` full backend suite green (includes ArchitectureBoundaryTest).
- `cd frontend && npx vitest run && npx tsc -b` green.
- `git diff --stat` touches only files in files_modified.
</verification>

<success_criteria>
- Expanded ticket row in the browser and Add-tickets dialog offers "Show linked commits"; clicking shows live commits/PRs or the same No link / Couldn't fetch states as the release page.
- No fetch before the click; nothing persisted; no changelog read.
- 404 unknown key, 400 malformed key, 401 unauthenticated.
</success_criteria>

<output>
After completion, create `.planning/quick/261012-nqd-ticket-browser-show-linked-commits/261012-nqd-SUMMARY.md`
</output>
