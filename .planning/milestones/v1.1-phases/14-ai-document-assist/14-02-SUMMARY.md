---
phase: 14-ai-document-assist
plan: 02
subsystem: render
tags: [pdf, openhtmltopdf, ssrf, security]
requires: []
provides:
  - PDF export refuses every non-data: resource (no outbound fetch for body images)
affects: [PdfExportAdapter]
key-files:
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/render/PdfExportAdapter.java
    - src/test/java/com/evision/releasemgmt/adapters/render/PdfExportAdapterTest.java
requirements-completed: [ASST-10]
metrics:
  completed: 2026-10-18
---

# Phase 14 Plan 02: PDF remote-image probe and fix Summary

PDF export now refuses every non-`data:` resource via `useUriResolver`, closing a confirmed outbound fetch of remote body images.

## A1 probe result

FETCHED. Against the unmodified adapter, `![x](http://localhost:{port}/img.png)` produced 1 WireMock request to `/img.png` (expected 0). Research assumption A1 was true, so Task 2 was needed.

Note: the https variant passed even before the fix, but only vacuously (WireMock's self-signed cert is rejected during the handshake, so no request is recorded). It is kept as a scheme-variant pin; the http case is the real evidence.

## Fix (Task 2)

`useExternalResourceAccessControl` was tried first, with both `RUN_AFTER_RESOLVING_URI` and `RUN_BEFORE_RESOLVING_URI`. Neither stopped the image fetch in 1.0.10 fast mode; the probe still saw 1 request. `useUriResolver((base, uri) -> uri != null && uri.startsWith("data:") ? uri : null)` does stop it. Probe is now 0 requests, the PDF is still produced, and the cover-logo data URI still renders (image XObject count is 1). Base URI stays null (T-05-23). Applies to all documents.

DOCX: `grep -ic image DocxAstWalker.java` returns 0, so DOCX does no body image handling and needs no change.

## Commits

Task 1 and Task 2 were committed together (one commit), as the plan permits when the probe is FETCHED. No `@Disabled` was used.

## Verification

Full backend suite: 1081 tests, 0 failures (baseline 1078 plus 3 new tests).

## Deviations from Plan

[Rule 3] The plan's preferred API (`useExternalResourceAccessControl`) did not block the fetch, so the plan's fallback (`useUriResolver`) was used.

## Threat coverage

T-14-04 and T-14-05 mitigated and pinned by `remoteImageOverHttpIsNeverFetched`.

## Self-Check: PASSED
