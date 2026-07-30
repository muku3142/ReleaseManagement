---
status: partial
phase: 01-foundation-authentication
plan: 08
source: [01-08-PLAN.md]
started: 2026-07-30
updated: 2026-07-30
site: https://evisionegypt.atlassian.net
---

# Plan 01-08 — Live-Jira UAT Results

Partial. The sign-in path is confirmed against the organisation's real Jira Cloud site.
The cases that require a second Jira account or a mid-session revocation are outstanding.

**Environment:** containerised stack (`compose.app.yaml`), `RM_PROFILES=local` — the
`stub-jira` profile is **off**, so every credential check below hit the real site. Verified
absent from the startup log; the container reaches
`https://evisionegypt.atlassian.net/rest/api/3/myself` and gets `401` unauthenticated, as
expected.

## Results

| # | Criterion | Result | Evidence |
|---|-----------|--------|----------|
| 1 | Release manager can configure which Jira Cloud site the system reads from, and see it in the UI | **PASS** | `PUT /api/settings/jira-site` accepted `https://evisionegypt.atlassian.net`; `GET` returns it with `updated_by = dev-account-id`. Trailing-slash variant correctly rejected 400 `VALIDATION_FAILED`. |
| 2 | Sign in with real Jira email + API token, validated live against that site before access is granted | **PASS** | User-confirmed 2026-07-30. Credential row created for real Atlassian accountId `5f5f5f58aba9660071082b49` (`amr.mekawy@evision.ws`) — an accountId can only exist if Jira itself returned it from `/myself`. |
| 3 | Invalid/expired/revoked token rejected with a clear, actionable, distinguishable message | **PARTIAL** | Invalid token verified live: `401`, `reason: INVALID_CREDENTIALS`, detail "Your Jira credential is no longer valid. Enter your Jira email and a current API token." Expired and revoked variants not yet exercised — see Outstanding. |
| 4 | A real stored token is ciphertext in the database and appears in no API response body or header | **PASS** | `encrypted_token` for the real account is 296 chars of base64 ciphertext, envelope key-version byte `0x01`; does not match `ATATT%`. Login response headers and body contain no `ATATT`/`apiToken`/`api_token`. |
| 5 | Release manager can replace a real expired/revoked token from settings without losing the credential row, the configured site, or any other record | **NOT RUN** | Requires a second real token. |

## Outstanding — needs a human

These cannot be automated and were not attempted. Per the plan: *"Do not mark a criterion as
passing on inference. If the human does not confirm it, it is not confirmed."*

1. **Token replacement (criterion 5, AUTH-04).** Generate a second API token, replace via
   Settings → "New Jira API token", confirm the credential row survives (same `created_at`,
   new `updated_at`) and the configured site is untouched.
2. **Revocation mid-session (D-04, D-14).** Sign in, revoke that token in the Atlassian UI,
   then trigger a Jira-backed call. Expect the credential-invalid message, with **no**
   forced logout and no background revalidation.
3. **`ACCOUNT_MISMATCH` (AUTH-04, T-07-08).** Needs a token from a *different* Jira account
   with access to the same site. Submit it via Settings while signed in as the first account;
   expect `409 ACCOUNT_MISMATCH` and the stored credential unchanged.
4. **Capture the real `/myself` fixture.** Replace RESEARCH.md's inferred response shape with
   a captured one (identifying values redacted) at
   `src/test/resources/wiremock/jira-myself-real.json`, and point
   `JiraRestAuthAdapterTest` at it. This answers RESEARCH.md Open Question 1 — whether this
   tenant's `/myself` matches what plan 03's mapper assumed.

## Notes

- The stub credential row (`dev-account-id` / `dev@example.com`) remains in
  `jira_credential` from earlier stub-profile testing. Harmless — rows are keyed by
  `accountId` — but worth deleting before any production use.
- Requirement status: criteria 1-2 and 4 evidence **AUTH-01**, **AUTH-03** and **JIRA-01**.
  **AUTH-04** stays open pending items 1-3. **AUTH-02** remains vacuous until Phase 2
  introduces actual Jira ticket reads.
