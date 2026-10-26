---
phase: 05-document-generation
reviewed: 2026-08-10T00:00:00Z
depth: standard
files_reviewed: 62
files_reviewed_list:
  - frontend/src/api/auth.ts
  - frontend/src/api/client.ts
  - frontend/src/api/documents.ts
  - frontend/src/api/releases.ts
  - frontend/src/api/templates.ts
  - frontend/src/api/tickets.ts
  - frontend/src/components/DocumentsSection.tsx
  - frontend/src/components/ReadinessPill.tsx
  - frontend/src/routes/ReleaseDetailRoute.tsx
  - frontend/src/routes/ReleasesRoute.tsx
  - frontend/src/routes/TemplateDetailRoute.tsx
  - src/main/java/com/evision/releasemgmt/adapters/jira/AdfFlattener.java
  - src/main/java/com/evision/releasemgmt/adapters/jira/JiraRestTicketAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/DocumentSnapshotRepositoryAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/TemplateRepositoryAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/DocumentSnapshotEntity.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/DocumentSnapshotLogoEntity.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseEntity.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/ReleaseTicketItemEntity.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/entity/TemplateEntity.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/DocumentSnapshotJpaRepository.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/DocumentSnapshotLogoJpaRepository.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseJpaRepository.java
  - src/main/java/com/evision/releasemgmt/adapters/persistence/repository/ReleaseSummaryRow.java
  - src/main/java/com/evision/releasemgmt/adapters/render/DocxAstWalker.java
  - src/main/java/com/evision/releasemgmt/adapters/render/DocxExportAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/render/MustacheFlexmarkRenderAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/render/PdfExportAdapter.java
  - src/main/java/com/evision/releasemgmt/adapters/web/DocumentController.java
  - src/main/java/com/evision/releasemgmt/adapters/web/DocumentReadinessController.java
  - src/main/java/com/evision/releasemgmt/adapters/web/ExportFilename.java
  - src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java
  - src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java
  - src/main/java/com/evision/releasemgmt/adapters/web/TemplateController.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/DocumentResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/DocumentRevisionListResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/DocumentRevisionResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReadinessEntryResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReadinessResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/ReleaseItemResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/SaveTemplateContentRequest.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/TemplateResponse.java
  - src/main/java/com/evision/releasemgmt/adapters/web/dto/TicketResponse.java
  - src/main/java/com/evision/releasemgmt/application/DocumentExportService.java
  - src/main/java/com/evision/releasemgmt/application/DocumentGenerationService.java
  - src/main/java/com/evision/releasemgmt/application/DocumentReadinessService.java
  - src/main/java/com/evision/releasemgmt/application/ReleaseViewAssembler.java
  - src/main/java/com/evision/releasemgmt/application/RenderContextMapper.java
  - src/main/java/com/evision/releasemgmt/application/RenderService.java
  - src/main/java/com/evision/releasemgmt/application/TemplateService.java
  - src/main/java/com/evision/releasemgmt/config/TemplateSeeder.java
  - src/main/java/com/evision/releasemgmt/domain/document/DocumentNotFoundException.java
  - src/main/java/com/evision/releasemgmt/domain/document/DocumentReadinessState.java
  - src/main/java/com/evision/releasemgmt/domain/document/DocumentSnapshot.java
  - src/main/java/com/evision/releasemgmt/domain/document/ExportFailedException.java
  - src/main/java/com/evision/releasemgmt/domain/document/NoActiveTemplateException.java
  - src/main/java/com/evision/releasemgmt/domain/jira/TicketDetail.java
  - src/main/java/com/evision/releasemgmt/domain/jira/TicketField.java
  - src/main/java/com/evision/releasemgmt/domain/release/Release.java
  - src/main/java/com/evision/releasemgmt/domain/release/ReleaseSummary.java
  - src/main/java/com/evision/releasemgmt/domain/template/GroupingDimension.java
  - src/main/java/com/evision/releasemgmt/domain/template/PlaceholderCatalog.java
  - src/main/java/com/evision/releasemgmt/domain/template/Template.java
  - src/main/java/com/evision/releasemgmt/domain/template/view/ItemView.java
  - src/main/java/com/evision/releasemgmt/domain/template/view/SampleReleaseView.java
  - src/main/java/com/evision/releasemgmt/ports/DocumentSnapshotRepository.java
  - src/main/java/com/evision/releasemgmt/ports/DocxExportPort.java
  - src/main/java/com/evision/releasemgmt/ports/PdfExportPort.java
  - src/main/java/com/evision/releasemgmt/ports/RenderEnginePort.java
  - src/main/java/com/evision/releasemgmt/ports/TemplateRepository.java
  - src/main/resources/db/migration/V5__document_generation.sql
  - src/main/resources/templates/starter-approval.md
  - src/main/resources/templates/starter-runbook.md
findings:
  critical: 1
  warning: 5
  info: 1
  total: 7
status: issues_found
---

# Phase 5: Code Review Report

**Reviewed:** 2026-08-10T00:00:00Z
**Depth:** standard
**Files Reviewed:** 62
**Status:** issues_found

## Summary

Reviewed the document-generation vertical slice (render/export adapters, snapshot persistence,
readiness computation, template/document controllers, and the frontend document/readiness UI).
The core untrusted-template security posture is solid: `RenderService` enforces a single bounded
entry point, `MustacheFlexmarkRenderAdapter` neutralises raw HTML post-parse, and
`DocumentSnapshotRepository` genuinely exposes no update/delete path (append-only holds).
`ExportFilename` correctly allowlists release-name-derived `Content-Disposition` filenames.

However, one sibling code path that also builds a `Content-Disposition` header from
user-controlled input — the uploaded logo's original filename — does **not** get the same
allowlist treatment, which is a real gap against this phase's stated header-injection threat
model. There is also a genuine staleness-correctness bug (an idempotent "add tickets already on
the release" call still marks the release as changed, which can mark up-to-date documents stale
for no reason), a revision/filename race on export, and a nested-list DOCX formatting defect.

## Critical Issues

### CR-01: Logo filename is not sanitised before being interpolated into `Content-Disposition`

**File:** `src/main/java/com/evision/releasemgmt/application/LogoValidator.java:120-134` (root cause), consumed at
`src/main/java/com/evision/releasemgmt/adapters/web/TemplateController.java:174-176` and
`src/main/java/com/evision/releasemgmt/adapters/web/DocumentController.java:142-144`

**Issue:** `LogoValidator.safeFilename()` strips only path separators (`/`, `\`) and trims/truncates the
uploaded file's original filename (`MultipartFile.getOriginalFilename()`, fully attacker-controlled).
It does **not** strip or escape double quotes, control characters, or CR/LF. That raw string is then
persisted as `LogoMetadata.filename()` and later interpolated verbatim into an HTTP response header
in two places:

```java
// TemplateController.downloadLogo
.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + image.metadata().filename() + "\"")

// DocumentController.toImageResponse
.header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + image.metadata().filename() + "\"")
```

`ExportFilename.java`'s own header comment states this codebase's threat model explicitly:
"This is the first place in this codebase that interpolates user-supplied free text into an HTTP
header value... an ALLOWLIST is used... That single rule closes header injection... at once,"
and calls out `downloadLogo` by name as relying on the upload being "validated" — but the actual
validation performed by `LogoValidator` does not close the same gap. A filename containing a raw
`"` breaks out of the quoted `filename=` value (header/attribute injection into the same header,
e.g. forging a second `filename=` parameter for content-sniffing/download-spoofing purposes);
whether raw CR/LF can additionally survive depends on the leniency of the multipart parser, but the
quote-breakout alone is sufficient to violate the documented invariant.

**Fix:** Route the stored filename through the same allowlist approach `ExportFilename` already
uses (or at minimum strip/replace `"`, `\`, and all control characters including CR/LF) inside
`LogoValidator.safeFilename()`, so the sanitisation happens once at the point untrusted data enters
the system, matching this codebase's own stated pattern:

```java
private static String safeFilename(String filename) {
    if (filename == null) return "logo";
    String base = filename;
    int lastSlash = Math.max(base.lastIndexOf('/'), base.lastIndexOf('\\'));
    if (lastSlash >= 0) base = base.substring(lastSlash + 1);
    base = base.replaceAll("[\"\\\\\\r\\n\\p{Cntrl}]", "_").trim();
    if (base.isBlank()) return "logo";
    return base.length() > 255 ? base.substring(0, 255) : base;
}
```

## Warnings

### WR-01: `addItems` marks the release as changed even when nothing was actually added

**File:** `src/main/java/com/evision/releasemgmt/adapters/persistence/ReleaseRepositoryAdapter.java:78-89`

**Issue:**

```java
public Release addItems(UUID releaseId, List<ReleaseItem> items) {
    ReleaseEntity entity = lockOpen(releaseId);
    Set<String> present = Set.copyOf(findTicketKeys(releaseId));
    insertItems(releaseId, items.stream().filter(i -> !present.contains(i.ticketKey())).toList());
    stampItemsChanged(entity);
    jpa.save(entity);
    return load(releaseId);
}
```

`stampItemsChanged(entity)` (which sets `items_changed_at = now()`, the exact signal
`DocumentReadinessService` uses to flip a document from `UP_TO_DATE` to `STALE`) runs
unconditionally, even when the filter above removes every candidate because all requested ticket
keys are already present on the release (`ReleaseAddResult.alreadyPresent` — an explicitly
supported, common outcome per `ReleaseController`/`ReleaseAddResult`'s own "idempotent add"
contract). The net effect: calling "add tickets" with tickets already on the release — a normal,
harmless, no-op user action — silently marks every generated document for that release stale,
even though the release's actual content did not change. This directly undermines the
staleness-correctness guarantee `DocumentReadinessService`/`items_changed_at` exists to provide.

The same unconditional-stamp pattern exists in `removeItem` (line 93-99) and `removeManualItem`
(line 167-173), which stamp even when the target key/id does not exist and the delete is a no-op;
worth auditing alongside this fix.

**Fix:** Only stamp when an actual mutation occurred, e.g.:

```java
public Release addItems(UUID releaseId, List<ReleaseItem> items) {
    ReleaseEntity entity = lockOpen(releaseId);
    Set<String> present = Set.copyOf(findTicketKeys(releaseId));
    List<ReleaseItem> toInsert = items.stream().filter(i -> !present.contains(i.ticketKey())).toList();
    if (!toInsert.isEmpty()) {
        insertItems(releaseId, toInsert);
        stampItemsChanged(entity);
        jpa.save(entity);
    }
    return load(releaseId);
}
```

### WR-02: Export filename's reported revision can disagree with the exported content under concurrent generation

**File:** `src/main/java/com/evision/releasemgmt/adapters/web/DocumentController.java:88-133`

**Issue:** `exportPdf`/`exportDocx` resolve "which revision is latest" twice, independently, when the
`revision` query parameter is omitted:

```java
int effectiveRevision = effectiveRevision(releaseId, documentType, revision); // resolves "latest" (call #1)
byte[] bytes = exportService.toPdf(releaseId, documentType, revision);        // resolves "latest" again inside (call #2)
String filename = exportFilename(releaseId, documentType, effectiveRevision, "pdf");
```

Both `effectiveRevision()` and `DocumentExportService.resolve()` independently call
`snapshotRepository.findLatest(...)` when `revision == null`. If a concurrent `POST .../documents/{type}`
(generate) commits a new revision between these two reads, the filename embeds the revision number
observed by call #1 while the actual PDF/DOCX bytes streamed to the client are generated from the
revision found by call #2 — the downloaded file's name and its content can name different revisions.
This is a narrow window but a real data-labelling inconsistency for an audit-sensitive artifact.

**Fix:** Resolve the snapshot once and derive both the filename and the export bytes from the same
`DocumentSnapshot` instance (e.g. have `DocumentExportService` return the resolved snapshot's
revision alongside the bytes, or add an overload that accepts an already-resolved `DocumentSnapshot`).

### WR-03: Fixed DOCX numbering definition only defines level 0, but nested lists emit deeper `ilvl` values

**File:** `src/main/java/com/evision/releasemgmt/adapters/render/DocxExportAdapter.java:56-80` (numbering
definition) and `src/main/java/com/evision/releasemgmt/adapters/render/DocxAstWalker.java:166-210`
(nested list emission)

**Issue:** `DocxAstWalker.appendListItem` recurses into nested `BulletList`/`OrderedList` children with
`appendList(child, target, depth + 1, ...)`, and `applyListFormatting` sets `ilvl` to that `depth`
(0, 1, 2, ...) referencing the shared `numId` (1 for bullets, 2 for decimal). But the hand-authored
`NUMBERING_XML` constant in `DocxExportAdapter` defines exactly one `<w:lvl w:ilvl="0">` per
`abstractNum`, with no levels 1+ defined. A Markdown list nested inside a list item (a construct the
walker explicitly supports) therefore references an undefined numbering level. Word's behaviour for
an undefined `ilvl` on an abstract numbering definition that only declares level 0 is inconsistent
across versions (typically falling back to no visible indent/bullet change, sometimes triggering a
"repair" prompt on open) — nested lists will not render as expected in exported DOCX.

**Fix:** Either define at least a handful of indent levels (e.g. ilvl 0-4) in `NUMBERING_XML` for both
abstract numbering ids, or cap `depth` passed into `applyListFormatting` at the highest level actually
defined (`Math.min(depth, maxDefinedLevel)`).

### WR-04: `TemplateService.getLogo` reports "template not found" when the template exists but simply has no logo

**File:** `src/main/java/com/evision/releasemgmt/application/TemplateService.java:120-122`

**Issue:**

```java
public LogoImage getLogo(UUID templateId) {
    return repository.findLogo(templateId).orElseThrow(TemplateNotFoundException::new);
}
```

`repository.findLogo` returns `Optional.empty()` both when the template id doesn't exist *and* when
the template exists but has no logo uploaded (the normal, common case per `TemplateSummary.hasLogo`).
Both cases collapse to the same `TemplateNotFoundException`, which `GlobalExceptionHandler` turns into
`404 TEMPLATE_NOT_FOUND` — "This template doesn't exist or has been deleted." — a misleading message
for a template that plainly does exist and is simply logo-less. Any caller of
`GET /api/templates/{id}/logo` for a logo-less-but-real template gets an incorrect diagnostic.

**Fix:** Distinguish the two cases, e.g. verify template existence first (`get(templateId)`), then
translate an absent logo into a distinct outcome (404 with a `LOGO_NOT_FOUND`-style reason, or let the
controller return a plain empty 404 without the misleading template-existence claim) rather than
reusing `TemplateNotFoundException` for both.

### WR-05: Render timeout only bounds caller wait time, not actual template execution

**File:** `src/main/java/com/evision/releasemgmt/application/RenderService.java:77-98`

**Issue:** `bounded()` submits the jmustache render to a virtual-thread executor and calls
`future.cancel(true)` on timeout, but the class's own comment acknowledges jmustache does not poll
for interruption, so "a pathological render may keep running until it finishes." The phase's stated
security requirement is that the rendering path "must be bounded... in execution time," but the
mechanism implemented only bounds how long the HTTP caller waits for a response — it does not bound
CPU consumed by the underlying render. Combined with an unbounded virtual-thread-per-task executor,
repeated requests carrying a template that trips the timeout (nested sections operating on
near-the-cap collections, for instance) can accumulate long-running orphaned virtual threads that
never get reclaimed, which is a resource-exhaustion vector distinct from the collection-size cap that
`enforceCollectionCap` already covers.

**Fix:** At minimum, cap the number of concurrently in-flight bounded renders (e.g. a bounded
semaphore/executor rather than an unbounded virtual-thread-per-task executor) so a burst of
pathological requests cannot accumulate unboundedly many long-running orphaned tasks. A full fix
would require either a jmustache execution model that supports cooperative cancellation, or a
tighter static bound on template complexity (e.g. bounding nesting depth × collection size, not just
raw tag count and per-collection size independently).

## Info

### IN-01: `PdfExportAdapter.escapeHtml` does not escape single quotes

**File:** `src/main/java/com/evision/releasemgmt/adapters/render/PdfExportAdapter.java:130-135`

**Issue:** `escapeHtml` replaces `&`, `<`, `>`, and `"` but not `'`. All current call sites place the
escaped value either inside double-quoted attributes or as element text content, so this is not
currently exploitable, but it is an incomplete escaping function that would become a defect the
moment a future caller places the value inside a single-quoted attribute.

**Fix:** Add `.replace("'", "&#39;")` for defense-in-depth completeness.

---

_Reviewed: 2026-08-10T00:00:00Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
