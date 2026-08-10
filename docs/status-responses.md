# Status Responses and the Backend↔Frontend Error Contract

How this system reports outcomes over HTTP, and the rules that keep the backend and the SPA in
agreement about what went wrong.

Verified against the codebase at Phase 4 wave 5 (backend 427 tests, frontend 75 tests, all green).

---

## 1. The contract in one sentence

**The HTTP status says what kind of failure it is; a `reason` string says exactly which failure it
is; and the frontend selects user-facing text from `reason` alone — never from the status, and never
by matching on message text.**

Everything below follows from that.

---

## 2. Why `reason` exists at all

HTTP status codes are too coarse to drive a user interface. This system returns `409 Conflict` for
five genuinely different situations:

| Situation | What the user must do |
|---|---|
| Jira site not configured | Go to Settings and set the site |
| Release is frozen | Create a new release; this one is immutable |
| Duplicate release name+version | Pick a different name or version |
| Duplicate template name for a type | Pick a different name |
| Token belongs to a different Jira account | Sign out, sign back in as that account |

A client that branched on `409` could only ever say "conflict". So every error response carries an
extra top-level property, `reason`, holding a stable machine-readable token. The status tells a
generic HTTP client how to behave (retry, redirect, give up); `reason` tells *this* client what to
say and do.

`reason` is the API contract. The prose on either side is presentation and may be reworded freely.

---

## 3. Response shape

### 3.1 Errors — RFC 9457 `ProblemDetail`

Every error response is a Spring `ProblemDetail` with one custom property added:

```json
{
  "type":   "about:blank",
  "title":  "Release frozen",
  "status": 409,
  "detail": "This release is frozen and can no longer be changed.",
  "reason": "RELEASE_FROZEN"
}
```

`VALIDATION_FAILED` responses carry one further property, a per-field map:

```json
{
  "status": 400,
  "title":  "Request validation failed",
  "detail": "One or more fields failed validation.",
  "reason": "VALIDATION_FAILED",
  "fieldErrors": { "email": "must be a well-formed email address" }
}
```

The TypeScript mirror of this shape lives in `frontend/src/api/client.ts`:

```ts
export type ProblemDetail = {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  reason: string;                          // required — the contract
  fieldErrors?: Record<string, string>;
};
```

Note `status` and `reason` are non-optional on the client; `title` and `detail` are optional,
because the client must never depend on them.

### 3.2 Success

Success responses are plain DTOs with no envelope. There is no `{ success: true, data: ... }`
wrapper anywhere — the status code carries that information.

---

## 4. Every status this system emits

### 4.1 Success statuses

| Status | Used by | Notes |
|---|---|---|
| `200 OK` | All `GET`s, and mutations that return the updated resource | Default when a handler returns a body |
| `201 Created` | `POST /api/releases`, `POST /api/releases/{id}/manual-items`, `POST /api/templates`, `POST /api/templates/{id}/duplicate` | Declared with `@ResponseStatus(HttpStatus.CREATED)` |
| `204 No Content` | `POST /api/auth/logout`, `PUT /api/auth/credential`, `DELETE /api/releases/{id}`, `DELETE /api/templates/{id}` | Declared with `@ResponseStatus(HttpStatus.NO_CONTENT)` |

`GET /api/templates/{id}/logo` is the one endpoint returning a non-JSON body — it builds a
`ResponseEntity` explicitly so it can set image content-type headers.

**Client handling of 204 is a real edge case.** `rawFetch` short-circuits before parsing:

```ts
if (response.status === 204) return undefined as T;
```

Without that line, `response.json()` on an empty body throws a `SyntaxError` that would surface as a
mysterious parse failure rather than a successful logout.

### 4.2 Error statuses and their reasons

Complete map as of Phase 4 wave 5. Source column shows where the token is defined.

| Status | `reason` | Raised when | Source |
|---|---|---|---|
| `400` | `VALIDATION_FAILED` | Bean Validation failure, or a malformed path parameter | `GlobalExceptionHandler` literal |
| `401` | `INVALID_CREDENTIALS` | Jira rejected the email/token pair | `JiraAuthFailureReason` |
| `401` | `SESSION_EXPIRED` | Refresh token past its inactivity window or the 8h absolute cap | `SessionRefreshException.Reason` |
| `401` | `INVALID_SESSION` | Session token failed validation | `SessionRefreshException.Reason` |
| `401` | `UNAUTHENTICATED` | No/!valid session at the security filter | `ProblemDetailAuthenticationEntryPoint` |
| `403` | `JIRA_FORBIDDEN` | Jira authenticated the account but denied access to the site | `JiraAuthFailureReason` |
| `403` | `FORBIDDEN` | Access denied at the security filter (includes CSRF failure) | `ProblemDetailAuthenticationEntryPoint` |
| `404` | `RELEASE_NOT_FOUND` | Unknown release id | `GlobalExceptionHandler` literal |
| `404` | `MANUAL_ITEM_NOT_FOUND` | Unknown manual item id on that release | `GlobalExceptionHandler` literal |
| `404` | `TEMPLATE_NOT_FOUND` | Unknown template id | `GlobalExceptionHandler` literal |
| `404` | `DOCUMENT_NOT_FOUND` | Unknown document/revision for a (release, document type) pair | `GlobalExceptionHandler` literal |
| `409` | `SITE_NOT_CONFIGURED` | No Jira site configured yet | both literal and enum |
| `409` | `ACCOUNT_MISMATCH` | Replacement token authenticates as a different Jira account | `JiraAuthFailureReason` |
| `409` | `RELEASE_FROZEN` | Mutation attempted on a frozen release | `GlobalExceptionHandler` literal |
| `409` | `RELEASE_DUPLICATE` | `UNIQUE (name, version)` violated | `GlobalExceptionHandler` literal |
| `409` | `TEMPLATE_DUPLICATE` | Template name already used for that document type | `GlobalExceptionHandler` literal |
| `409` | `NO_ACTIVE_TEMPLATE` | Generate requested for a document type with zero active templates (D-05-10) | `GlobalExceptionHandler` literal |
| `413` | `LOGO_TOO_LARGE` | Upload exceeds `app.logo.max-bytes` (2 MiB) | `LogoRejectionReason` + multipart override |
| `415` | `LOGO_INVALID_TYPE` | Content sniffing says it isn't PNG/JPEG — catches SVG named `.png` | `LogoRejectionReason` |
| `422` | `RESULT_SET_TOO_LARGE` | Jira query matched more tickets than the paging cap allows | `JiraAuthFailureReason` |
| `422` | `REFRESH_INCOMPLETE` | Jira did not return every ticket on the release, so nothing was refreshed | `GlobalExceptionHandler` literal |
| `422` | `TEMPLATE_MALFORMED` | `TemplateScanner` rejected the Mustache — unclosed section, partial, lambda, custom delimiter | `GlobalExceptionHandler` literal |
| `422` | `RENDER_BOUND_EXCEEDED` | `RenderService` tripped the execution timeout or the collection-size cap | `GlobalExceptionHandler` literal |
| `422` | `LOGO_DIMENSIONS_EXCEEDED` | Over `app.logo.max-width`/`max-height` (2000×2000) | `LogoRejectionReason` |
| `429` | `JIRA_RATE_LIMITED` | Jira is rate-limiting | `JiraAuthFailureReason` |
| `500` | `INTERNAL_ERROR` | Anything unhandled — stack trace logged server-side only | `GlobalExceptionHandler` catch-all |
| `502` | `JIRA_ERROR` | Jira returned an unexpected error | `JiraAuthFailureReason` |
| `503` | `JIRA_UNREACHABLE` | Jira could not be reached | `JiraAuthFailureReason` |
| *(varies)* | `BAD_REQUEST`, `METHOD_NOT_ALLOWED`, … | Framework-generated problems — backfilled with the status name | `handleExceptionInternal` |

**27 explicit reasons**, plus the backfilled status-name family.

Status choices worth noting:

- **`415` for wrong logo type, `413` for oversize, `422` for oversize dimensions.** Not all `400`.
  A wrong media type genuinely is 415; a too-big payload genuinely is 413; dimensions are a
  semantic problem with a well-formed payload, which is what 422 means.
- **`502`/`503` for Jira problems, not `500`.** Jira is an upstream dependency. A 500 would imply
  this system is broken; 502/503 correctly say the dependency is.
- **`422` for `TEMPLATE_MALFORMED`, not `400`.** The request is syntactically valid JSON containing
  a syntactically invalid *template*. That is exactly the 400-vs-422 distinction.

---

## 5. Where reason codes are defined

There is **no single registry**. Codes come from four places, with different safety properties:

| Source | Kind | Compile-time safety |
|---|---|---|
| `GlobalExceptionHandler` | bare string literals | **none** — a typo silently degrades to the client's generic fallback |
| `domain/auth/JiraAuthFailureReason` | enum (8 values) | exhaustive `switch`, no `default` → adding a value breaks the build until mapped |
| `SessionRefreshException.Reason` | enum (2 values) | same |
| `domain/template/LogoRejectionReason` | enum (3 values) | same |
| `ProblemDetailAuthenticationEntryPoint` | string literals | none |

The enums are the safer pattern. The literals are load-bearing but unprotected — **this is the
weakest point in the contract**, and it is worth knowing before adding new codes.

---

## 6. How a failure travels

```
  ReleaseService
      throw new ReleaseFrozenException()          ← domain vocabulary, knows nothing about HTTP
          │
          │ propagates out of the controller (controllers contain ZERO catch blocks)
          ▼
  GlobalExceptionHandler  @RestControllerAdvice
      @ExceptionHandler(ReleaseFrozenException.class)
      → ProblemDetail: 409 + reason "RELEASE_FROZEN"
          │
          ▼
  HTTP
          │
          ▼
  rawFetch()            !response.ok → throw new ApiError(problem)
          │
          ▼
  apiFetch()            session-reason interception (see §8)
          │
          ▼
  messageForReason()    reason → user-facing string
```

Each layer speaks its own language, and exactly one place translates between them.

### 6.1 Backend: `GlobalExceptionHandler`

`@RestControllerAdvice`, so Spring routes every exception escaping any `@RestController` here.
**No controller in this codebase contains a `catch` block** — verified. Controllers and services
throw domain exceptions and never construct error responses.

Three mechanisms give it complete coverage:

1. `@ExceptionHandler(X.class)` methods — most specific match wins.
2. `extends ResponseEntityExceptionHandler` — inherits Spring's framework-error handling
   (malformed JSON, wrong method, unsupported media type), with two overrides customising it.
3. `@ExceptionHandler(Exception.class)` — the catch-all. Logs the stack trace server-side, returns
   a bare `INTERNAL_ERROR`. **No stack trace ever reaches the browser.**

All responses are built through one private helper, which is what guarantees `reason` is always
set:

```java
private ProblemDetail problem(HttpStatus status, String title, String detail, String reason) {
    ProblemDetail pd = ProblemDetail.forStatus(status);
    pd.setTitle(title);
    pd.setDetail(detail);
    pd.setProperty("reason", reason);
    return pd;
}
```

### 6.2 The gap that `handleExceptionInternal` closes

Framework-generated problems — malformed JSON, an unknown enum value, a wrong HTTP method — are
built by Spring itself and arrive **without** a `reason`. The client would degrade them to
`'UNKNOWN'` and show its generic fallback.

So the handler backfills:

```java
if (pd.getProperties() == null || !pd.getProperties().containsKey("reason")) {
    pd.setProperty("reason", HttpStatus.valueOf(statusCode.value()).name());   // e.g. "BAD_REQUEST"
}
```

Every response now carries a reason without hand-writing a handler per framework case.

### 6.3 The path that bypasses the handler entirely

**Authentication and authorization failures never reach `@RestControllerAdvice`**, because Spring
Security's filter chain runs *before* any controller is resolved. There is no controller for the
advice to advise.

`adapters/security/ProblemDetailAuthenticationEntryPoint` implements both `AuthenticationEntryPoint`
and `AccessDeniedHandler` and serialises `ProblemDetail` JSON by hand — same shape, `UNAUTHENTICATED`
and `FORBIDDEN`. The SPA cannot tell the two paths apart, which is the point.

**If you add a reason code, check whether it can occur in the filter chain.** If so, it belongs
here, not in `GlobalExceptionHandler`.

---

## 7. Frontend: turning a response into an error

`frontend/src/api/client.ts` is the single HTTP boundary. Every request in the SPA goes through it.

```ts
if (response.status === 204) return undefined as T;
if (!response.ok) {
  const problem = await response.json().catch(() => ({ status: response.status, reason: 'UNKNOWN' }));
  throw new ApiError({ ...problem, status: response.status, reason: problem.reason ?? 'UNKNOWN' });
}
```

Two defensive details:

- `.catch(() => ...)` handles a non-JSON error body — a proxy 502 HTML page, a gateway timeout.
  Without it the SPA would throw an unhelpful `SyntaxError` instead of a usable `ApiError`.
- `reason ?? 'UNKNOWN'` guarantees `ApiError.problem.reason` is always a string, so downstream
  lookups never see `undefined`.

`ApiError` is the only error type the SPA throws for HTTP failures, and it carries the whole
`ProblemDetail` rather than flattening it to a message.

---

## 8. Reason-driven behaviour, not just text

Two reason families trigger *behaviour* in `apiFetch`, not just copy.

### 8.1 Silent refresh-and-retry

```ts
const SESSION_REASONS = new Set(['UNAUTHENTICATED', 'SESSION_EXPIRED', 'INVALID_SESSION']);
```

On any of those, `apiFetch` calls `POST /api/auth/refresh`, and on success **replays the original
request once**. The `retried` parameter prevents infinite recursion, and `path !== REFRESH_PATH`
prevents the refresh call refreshing itself.

`refreshInFlight` makes it **single-flight**: if five queries 401 simultaneously, one refresh runs
and all five await it. Without that, a page with several parallel queries would fire several
concurrent refreshes and race.

This is why the short access-token TTL (10 minutes, D-07) is invisible to the user.

### 8.2 Credential-invalid signalling

```ts
if (error.problem.reason === 'INVALID_CREDENTIALS') {
  markJiraCredentialInvalid();
  throw error;
}
```

A rejected Jira credential is not a per-request failure — it's an account-level state. This flips a
module-level flag that `CredentialAlert` renders as a persistent banner, so the user is told to fix
their token once rather than seeing the same error on every screen.

**Both behaviours are keyed on `reason`, not status.** A `401` alone cannot distinguish
"refresh and retry" from "your Jira token is dead".

---

## 9. Frontend: choosing the message

`frontend/src/api/auth.ts` holds the single copy table, with the rule stated in a comment above it:

> The only place failure text is chosen — keyed on the machine-readable `reason` property and never
> on message matching. Later phases must extend this table rather than adding ad-hoc error text.

```ts
export function messageForReason(problem: ProblemDetail): string {
  if (problem.reason === 'VALIDATION_FAILED') {
    const fieldMessages = problem.fieldErrors ? Object.values(problem.fieldErrors) : [];
    return fieldMessages.length > 0 ? fieldMessages.join(' ') : 'Check the values you entered.';
  }
  return REASON_MESSAGES[problem.reason] ?? 'Something went wrong. Try again, and check the server logs if it persists.';
}
```

`VALIDATION_FAILED` is special-cased because the useful text is per-field and server-supplied; every
other reason maps to a static string.

The fallback matters: an unmapped reason produces a generic-but-sane message rather than `undefined`
or a crash. That makes a missing table entry a **quiet degradation**, not an outage — which is
convenient in production and dangerous in review, since nothing fails loudly.

### 9.1 Why the copy exists on both sides

The backend's `detail` and the frontend's `REASON_MESSAGES` overlap but are **not** the same string,
deliberately:

- `detail` serves API consumers, logs and non-browser clients.
- `REASON_MESSAGES` is UI copy, owned by the design contract.

Neither reads the other. If the SPA keyed off `detail`, any backend rewording would silently break
the UI. `reason` is the contract; prose on both sides is free to change.

---

## 10. Rules for adding a new status response

1. **Throw a domain exception**, don't build a response. Services and controllers stay HTTP-free.
2. **Prefer an enum over a bare string literal** if the failure has sibling cases — an exhaustive
   `switch` with no `default` makes the compiler enforce completeness.
3. **Add a handler in `GlobalExceptionHandler`** using the `problem(...)` helper, so `reason` cannot
   be forgotten. If the failure occurs in the security filter chain, add it to
   `ProblemDetailAuthenticationEntryPoint` instead.
4. **Choose the status honestly** — 422 for semantically-invalid-but-well-formed, 502/503 for
   upstream failures, 409 for state conflicts. Do not default everything to 400/500.
5. **Add a `REASON_MESSAGES` entry in the same change.** There is no compile-time link between the
   two sides; a missing entry degrades silently to the generic fallback.
6. **Write the message so the user knows what to do next**, not merely what happened.

---

## 11. Known gaps

Recorded honestly rather than glossed over.

| Gap | Impact | Where it should be fixed |
|---|---|---|
| `LOGO_TOO_LARGE` and `LOGO_DIMENSIONS_EXCEEDED` have **identical** frontend copy (`'This image is too large.'`) | The user cannot tell whether to compress the file or resize the image — two distinct codes are wasted | Plan 04-10, which builds the logo upload control |
| Neither logo message **interpolates the backend-declared limits** | Copy cannot say "max 2 MB" or "max 2000×2000", though the backend exposes both via the catalog endpoint specifically so it could | Plan 04-10 — `REASON_MESSAGES` is a static table with no access to fetched limits, so the control must interpolate at render time |
| Bare string-literal reasons have **no compile-time protection** | A typo in `GlobalExceptionHandler` produces a code no `REASON_MESSAGES` entry matches; degrades silently | Would need a shared enum or a test asserting every emitted reason has a frontend entry |
| No automated check that backend reasons ⊆ frontend `REASON_MESSAGES` keys | Drift is invisible until a user hits the path | A test could grep both sides and assert coverage |

---

## 12. File reference

| Concern | File |
|---|---|
| Exception → status + reason mapping | `src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java` |
| Filter-chain auth failures | `src/main/java/com/evision/releasemgmt/adapters/security/ProblemDetailAuthenticationEntryPoint.java` |
| Jira failure taxonomy | `src/main/java/com/evision/releasemgmt/domain/auth/JiraAuthFailureReason.java` |
| Session failure taxonomy | `src/main/java/com/evision/releasemgmt/adapters/security/SessionRefreshException.java` |
| Logo rejection taxonomy | `src/main/java/com/evision/releasemgmt/domain/template/LogoRejectionReason.java` |
| HTTP boundary, `ApiError`, refresh-retry | `frontend/src/api/client.ts` |
| `REASON_MESSAGES`, `messageForReason` | `frontend/src/api/auth.ts` |
| Credential-invalid banner state | `frontend/src/api/credentialStatus.ts` |
