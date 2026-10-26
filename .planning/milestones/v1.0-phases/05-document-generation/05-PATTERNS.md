# Phase 5: Document Generation - Pattern Map

**Mapped:** 2026-08-10
**Files analyzed:** 27 (17 backend new/modified, 10 frontend new/modified)
**Analogs found:** 22 / 27 (5 explicit gaps — see "No Analog Found")

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `domain/document/DocumentSnapshot.java` | model | CRUD (append-only) | `domain/template/Template.java` | role-match (record + static factory, but no `requireOpen()`-style guard needed) |
| `domain/document/DocumentNotFoundException.java` | model (exception) | — | `domain/template/TemplateNotFoundException.java` | exact |
| `domain/document/NoActiveTemplateException.java` (or similar, D-05-10 defensive case) | model (exception) | — | `domain/release/ReleaseFrozenException.java` (simple no-field domain exception) | exact |
| `domain/template/TemplateDocumentType.java` (reused, not new) | model (enum) | — | — | n/a — Open Question #1 resolved: reuse, do not add `DocumentType` |
| `ports/DocumentSnapshotRepository.java` | model (port interface) | CRUD (append-only) | `ports/TemplateRepository.java` | role-match (create/find/list shape; no update/delete methods to expose) |
| `ports/DocumentExportPort.java` | service (port interface) | file I/O (byte-producing) | `ports/RenderEnginePort.java` | role-match (single-method port returning rendered output; javadoc-documented contract + `@throws`) |
| `adapters/persistence/entity/DocumentSnapshotEntity.java` | model (JPA entity) | CRUD | `adapters/persistence/entity/TemplateEntity.java` | exact (plain getter/setter entity, `@Convert` for JSON list columns) |
| `adapters/persistence/repository/DocumentSnapshotJpaRepository.java` | repository | CRUD | `adapters/persistence/repository/TemplateJpaRepository.java` | exact (derived-query methods, no save/delete override needed) |
| `adapters/persistence/DocumentSnapshotRepositoryAdapter.java` | service (persistence adapter) | CRUD (append-only) | `adapters/persistence/TemplateRepositoryAdapter.java` | role-match (`@Transactional` placement, `toEntity`/`toDomain` mapping pair) |
| `application/DocumentGenerationService.java` | service | request-response (orchestration) | `application/TemplateService.java` | role-match (constructor-injects repository + `RenderService`; no lifecycle guard, unlike `ReleaseService`) |
| `application/DocumentExportService.java` | service | file I/O (byte-producing, on-demand) | `application/RenderService.java` (bounded-render caller shape) + `RenderEnginePort` consumer pattern | partial (new: no existing service calls two sibling export ports and returns raw bytes) |
| `application/DocumentReadinessService.java` | service | transform (timestamp comparison) | `application/ReleaseViewAssembler.java` (pure computation over a loaded aggregate, no port calls) | role-match |
| `adapters/render/PdfExportAdapter.java` | service (render adapter) | file I/O | `adapters/render/MustacheFlexmarkRenderAdapter.java` | role-match for confinement/DI shape; **body-walk logic itself has no analog** — see gaps |
| `adapters/render/DocxExportAdapter.java` | service (render adapter) | file I/O | `adapters/render/MustacheFlexmarkRenderAdapter.java` | role-match for confinement/DI shape only; **AST walker has no analog** — see gaps |
| `adapters/render/MustacheFlexmarkRenderAdapter.java` (modified — add `flexmark-ext-tables`) | service (render adapter) | transform | itself, prior revision (constructor lines 28-46) | exact (same file, additive change) |
| `adapters/web/DocumentController.java` | controller | request-response + file I/O (streaming export) | `adapters/web/TemplateController.java` (esp. `downloadLogo`, lines 155-168) | exact for export; role-match for generate/readiness (closer to `ReleaseController`'s `POST` action-with-DTO-response shape) |
| `adapters/web/GlobalExceptionHandler.java` (modified) | controller (exception mapping) | request-response | itself, prior revision | exact (same file, additive `@ExceptionHandler` methods) |
| `adapters/jira/JiraRestTicketAdapter.java` (modified — add `component`) | service (external adapter) | request-response (HTTP fetch + map) | itself, `fixVersions` handling (lines 110-116) | exact |
| `domain/jira/TicketDetail.java` (modified — add `component` field) | model (record) | — | itself, prior revision | exact |
| `domain/jira/TicketField.java` (modified — add `COMPONENT`) | model (enum) | — | itself, `FIX_VERSIONS` entry | exact |
| `application/ReleaseViewAssembler.java` (modified — `component` + grouping dimension) | service | transform | itself, `groups()` method (lines 58-82) | exact (same file, additive branch) |
| `domain/template/PlaceholderCatalog.java` (modified — `items.component` entry, `groups` description) | model (hand-written catalog) | — | itself, `ITEM`-scope entries (lines 39-60) | exact |
| `src/main/resources/db/migration/V5__document_generation.sql` | migration | batch (DDL) | `src/main/resources/db/migration/V4__template_authoring.sql` | exact (comment style, `CHECK (... IN (...))` enum convention) |
| `frontend/src/routes/ReleasesRoute.tsx` (modified — readiness columns) | component (route) | request-response | itself, prior revision (table rendering, lines 86-140) | exact |
| `frontend/src/routes/ReleaseDetailRoute.tsx` (modified — Documents section) | component (route) | request-response | itself, prior revision (card/section pattern) | exact |
| `frontend/src/routes/DocumentViewerRoute.tsx` (new) | component (route) | request-response | `frontend/src/routes/TemplateDetailRoute.tsx` (param + query shape) + `frontend/src/components/TemplatePreview.tsx` (chrome markup) | role-match (two analogs combined, see below) |
| `frontend/src/components/ReadinessPill.tsx` (new, recommended extraction) | component (presentational) | transform | no direct analog — closest shape is the inline `Frozen`/`Open` pill in `ReleasesRoute.tsx`/`ReleaseDetailRoute.tsx` | partial — see gaps |
| `frontend/src/api/documents.ts` (new) | provider (API module) | request-response + file I/O (export downloads) | `frontend/src/api/templates.ts` | role-match; export-download calls have no analog (see gaps) |
| `frontend/src/router.tsx` (modified) | config (route registration) | — | itself, prior revision | exact |
| `frontend/src/components/AppShell.tsx` (modified, if at all) | component (layout) | — | itself, prior revision | exact — **likely no change needed**, viewer is reached by link, not top nav |
| `frontend/src/api/auth.ts` (modified — `REASON_MESSAGES`) | provider (constants) | — | itself, prior revision (lines 63-99) | exact |

## Pattern Assignments

### `domain/document/DocumentSnapshot.java` (model, CRUD)

**Analog:** `src/main/java/com/evision/releasemgmt/domain/template/Template.java`

**Full pattern** (record + static factory, comment explaining the *absence* of a lifecycle guard):
```java
package com.evision.releasemgmt.domain.template;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// D-04-13: freely editable and freely deletable, so unlike Release there is no requireOpen()-style
// lifecycle guard here. warnings holds the unknown placeholders the last save's validation render
// found (D-04-07) - a save succeeds with warnings, it is not blocked by them.
public record Template(UUID id, String name, TemplateDocumentType documentType, String body,
                       boolean active, ChromeFields chrome, LogoMetadata logo,
                       List<String> warnings, Instant createdAt, String createdBy,
                       Instant updatedAt, String updatedBy) {

    public static Template create(String name, TemplateDocumentType documentType, String actor,
                                  Instant now) {
        return new Template(UUID.randomUUID(), name, documentType, "", false, ChromeFields.empty(),
                null, List.of(), now, actor, now, actor);
    }
}
```
**How this maps onto `DocumentSnapshot`:** copy the record + static-factory shape exactly, but the
factory comment must say the *opposite* of Template's — D-05-06 makes this aggregate **append-only,
never edited**, so there is no `update()`-style repository method at all (contrast with
`TemplateRepository.update`). Fields per D-05-05/D-05-06: `id`, `releaseId`, `documentType`,
`revision` (int, per-(release, docType) numbering), `markdown` (post-Mustache, pre-flexmark),
copied `templateName`/`templateBody`/`chrome` (a `ChromeFields` copy, not a live FK), `generatedAt`,
`generatedBy`. No `active` field — that concept belongs to `Template`, not the snapshot.

### `domain/document/DocumentNotFoundException.java` / `NoActiveTemplateException.java` (model, exception)

**Analog:** `src/main/java/com/evision/releasemgmt/domain/template/TemplateNotFoundException.java`
```java
package com.evision.releasemgmt.domain.template;

public class TemplateNotFoundException extends RuntimeException {
}
```
Copy verbatim (empty body, no message, no fields) for both new exceptions — this codebase's
convention is a marker exception whose *type* carries the meaning, with the human-readable text
supplied entirely by `GlobalExceptionHandler`.

### `ports/DocumentSnapshotRepository.java` (port)

**Analog:** `src/main/java/com/evision/releasemgmt/ports/TemplateRepository.java`

**Method-doc convention to copy** (javadoc `@throws` naming the domain exception, comments citing
decision IDs):
```java
public interface TemplateRepository {

    /**
     * @throws com.evision.releasemgmt.domain.template.TemplateDuplicateException
     *         when (documentType, name) already exists
     */
    Template create(Template template);

    Optional<Template> findById(UUID templateId);

    /** Ordered by document type, then name ascending - the order the /templates screen renders. */
    List<Template> findAll();
    ...
    /** D-04-13: no guard. Deleting the active template is permitted and leaves the type with none. */
    void delete(UUID templateId);
```
**What to keep, what to drop for `DocumentSnapshotRepository`:** keep `create`/`findById`-style
methods and the javadoc convention. **Drop entirely**: `update`, `delete`, `setActive` — D-05-06's
append-only guarantee means this port must expose **no mutation of an existing row**, only
`create` (append a new revision) and reads (`findLatest(releaseId, documentType)`,
`findByRevision(releaseId, documentType, int revision)`, `findAllRevisions(releaseId, documentType)`
ordered newest-first per the UI-SPEC's revision-history convention).

### `adapters/persistence/TemplateRepositoryAdapter.java` → `DocumentSnapshotRepositoryAdapter.java`

**Analog:** `src/main/java/com/evision/releasemgmt/adapters/persistence/TemplateRepositoryAdapter.java`

**`@Transactional` + `DataIntegrityViolationException` translation pattern** (lines 35-46):
```java
@Override
@Transactional
public Template create(Template template) {
    try {
        jpa.saveAndFlush(toEntity(template));
    } catch (DataIntegrityViolationException e) {
        // UNIQUE (document_type, name) - D-04-12. saveAndFlush forces the constraint to fire
        // here rather than at commit, where it would escape this catch.
        throw new TemplateDuplicateException();
    }
    return load(template.id());
}
```
**`toEntity`/`toDomain` static mapper pair convention** (lines 152-179) — copy this shape exactly
for `DocumentSnapshotEntity` ↔ `DocumentSnapshot`. For the snapshot adapter, the `create` method's
`DataIntegrityViolationException` catch target is different: a `UNIQUE (release_id, document_type,
revision)` constraint, not a duplicate-name constraint — but the exact same "force via
`saveAndFlush`, translate here" idiom applies. No `update`/`delete`/`setActive` methods exist on
this adapter at all (contrast with the full Template adapter above).

### `adapters/persistence/entity/DocumentSnapshotEntity.java`

**Analog:** `src/main/java/com/evision/releasemgmt/adapters/persistence/entity/TemplateEntity.java`

Copy the plain-getter/setter `@Entity`/`@Table`/`@Column` shape verbatim (lines 17-70), including
`@Convert(converter = JsonStringListConverter.class)` for the `warnings`-style JSON list column
(reused here if the copied `ChromeFields`' string fields are flattened onto the entity the same way
`TemplateEntity` flattens `ChromeFields` — see `entity.setCoverEnabled(...)` etc., lines 74-79 of
the adapter, not shown again here).

### `application/DocumentGenerationService.java`

**Analog:** `src/main/java/com/evision/releasemgmt/application/TemplateService.java`

**Constructor-injection + comment-on-absent-guard convention** (lines 20-34):
```java
// D-04-13: freely editable and freely deletable - there is no requireOpen()-style lifecycle guard
// anywhere in this service, unlike ReleaseService.
@Service
public class TemplateService {

    private final TemplateRepository repository;
    private final RenderService renderService;
    private final LogoValidator logoValidator;

    public TemplateService(TemplateRepository repository, RenderService renderService,
                           LogoValidator logoValidator) {
        this.repository = repository;
        this.renderService = renderService;
        this.logoValidator = logoValidator;
    }
```
**The MANDATORY call-through pattern** (this is the load-bearing rule from CONTEXT.md — generation
must not bypass `RenderService`):
```java
// TemplateService.saveContent, lines 60-74 — the render call this phase's generation must mirror
public Template saveContent(UUID id, String body, ChromeFields chrome, String actor) {
    Template existing = get(id);
    ReleaseView sample = SampleReleaseView.instance();
    ...
    RenderResult result = renderService.render(source, sample, field);
```
`DocumentGenerationService.generate(releaseId, documentType, actor)` must: load the `Release` via
the existing `ports/ReleaseRepository` (no Jira call — D-03-06), call
`ReleaseViewAssembler.assemble(release)`, load the **active** `Template` for the type via
`TemplateRepository.findByDocumentType(...).stream().filter(Template::active)` (or add a dedicated
`findActive` method mirroring `TemplateJpaRepository.findByDocumentTypeAndActiveTrue`), call
`renderService.render(template.body(), view, "Body")` — **exactly this call, not a new bounded-render
path** — then persist the `DocumentSnapshot` via the new repository, computing `revision` as
`findAllRevisions(...).size() + 1` (or a dedicated `nextRevision` port method).

### `application/DocumentReadinessService.java`

**Analog:** `src/main/java/com/evision/releasemgmt/application/ReleaseViewAssembler.java`

**Pure-computation-over-a-loaded-aggregate shape** (no port calls beyond what's already loaded,
lines 24-52 pattern, e.g. `assemble(Release release)` takes the already-fetched aggregate and
returns a derived view). `DocumentReadinessService.readiness(Release release, List<Template>
templatesForType, Optional<DocumentSnapshot> latestSnapshot)` should be a similarly pure function:
compare `latestSnapshot.generatedAt()` against `release.lastRefreshedAt()` (D-05-09) and return one
of the four UI-SPEC states (`NO_ACTIVE_TEMPLATE`, `NOT_GENERATED`, `STALE`, `UP_TO_DATE`). Keep it a
plain method with no repository dependency of its own if possible — the calling service
(`DocumentGenerationService` or `DocumentController`) already has the release and snapshot loaded.

### `ports/RenderEnginePort.java` → `ports/DocumentExportPort.java`

**Analog:** `src/main/java/com/evision/releasemgmt/ports/RenderEnginePort.java`

**Single-method port with a detailed javadoc contract, including the exact security-relevant
caveats to imitate the style of:**
```java
public interface RenderEnginePort {

    /**
     * Resolves the Mustache placeholders in {@code source} against {@code context}, then parses the
     * resulting Markdown and renders it to HTML.
     * ...
     * @throws com.evision.releasemgmt.domain.template.TemplateMalformedException
     *         when the engine rejects the source
     */
    String render(String source, Map<String, Object> context);
}
```
RESEARCH.md's own illustrative shape for the new port is the right target:
```java
public interface DocumentExportPort {
    byte[] toPdf(String resolvedMarkdown, ChromeFields chrome, byte[] logoBytesOrNull);
    byte[] toDocx(String resolvedMarkdown, ChromeFields chrome, byte[] logoBytesOrNull);
}
```
Two implementations (`PdfExportAdapter`, `DocxExportAdapter`) both under `adapters/render/`, per
Pattern 1 in RESEARCH.md — mirrors how `RenderEnginePort` has exactly one implementation today, but
the interface itself does not preclude more.

### `adapters/render/MustacheFlexmarkRenderAdapter.java` (modified — tables extension)

**Analog:** itself, current revision, lines 28-46 (constructor) and 65-69 (`parseMarkdown`)
```java
this.markdownParser = Parser.builder().build();
this.htmlRenderer = HtmlRenderer.builder().build();
```
becomes (per RESEARCH.md's Code Examples section, verified against the flexmark-java Tables wiki):
```java
this.markdownParser = Parser.builder()
        .extensions(List.of(TablesExtension.create()))
        .build();
this.htmlRenderer = HtmlRenderer.builder()
        .extensions(List.of(TablesExtension.create()))
        .build();
```
The package-private `parseMarkdown` method (lines 65-69) is unchanged in signature — it remains the
single entry point both `PdfExportAdapter` and `DocxExportAdapter` call to get the shared `Document`
AST (D-04-04's guarantee, the whole basis for D-05-07).

### `adapters/web/DocumentController.java`

**Analog (export endpoints):** `src/main/java/com/evision/releasemgmt/adapters/web/TemplateController.java`, `downloadLogo` (lines 155-168)
```java
@GetMapping("/{templateId}/logo")
public ResponseEntity<byte[]> downloadLogo(@PathVariable UUID templateId) {
    LogoImage image = templateService.getLogo(templateId);
    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_TYPE, image.metadata().contentType())
            .header("X-Content-Type-Options", "nosniff")
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "inline; filename=\"" + image.metadata().filename() + "\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(image.bytes());
}
```
For export, swap `inline` for `attachment` and set the two OOXML/PDF content types explicitly
(per RESEARCH.md Pattern 4). **New risk this endpoint introduces that `downloadLogo` does not
carry**: the export filename is built from the release name/version, which is free-text user
input, not a validated upload's own filename — sanitize (strip/replace non-filename-safe
characters, reject/replace CR/LF) before interpolating into `Content-Disposition`. `downloadLogo`
is not a safe copy-paste for the filename line specifically; copy the header-setting *shape*, not
that one value verbatim.

**Analog (generate/readiness endpoints, DTO-in/DTO-out shape):**
`src/main/java/com/evision/releasemgmt/adapters/web/ReleaseController.java` — its `POST
/{releaseId}/freeze`-style action methods (`@PostMapping("/{releaseId}/freeze")`, no request body,
`Principal principal` for the actor, domain object returned and mapped to a `*Response` DTO) are the
better shape for `POST /releases/{id}/documents/{type}` than `TemplateController`'s CRUD endpoints —
generation is a one-shot action, not a resource create/update.

### `adapters/web/GlobalExceptionHandler.java` (modified — new reason codes)

**Analog:** itself, current revision — the `problem(...)` helper (lines 236-242) and the per-exception
handler shape (e.g. lines 82-86):
```java
@ExceptionHandler(ReleaseNotFoundException.class)
public ProblemDetail handleReleaseNotFound(ReleaseNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "Release not found",
            "This release doesn't exist or has been deleted.", "RELEASE_NOT_FOUND");
}
```
Add three handlers in this exact shape per UI-SPEC Planning Input #1: `DOCUMENT_NOT_FOUND` (404),
`NO_ACTIVE_TEMPLATE` (409, mirrors the `ReleaseFrozenException`→`RELEASE_FROZEN` conflict-status
precedent at lines 88-92), `EXPORT_FAILED` (502 or 500 — no existing precedent for an
infra-library-failure code; closest is `JIRA_ERROR`'s `BAD_GATEWAY` for "the thing we called broke").
Reuse `RENDER_BOUND_EXCEEDED` (lines 154-160) unchanged if generation itself can breach the bound —
do not add a second bound-exceeded reason.

### `adapters/jira/JiraRestTicketAdapter.java` (modified — `component` field, D-05-02)

**Analog:** itself, current revision, the `fixVersions` block (lines 110-116):
```java
List<String> fixVersions = new ArrayList<>();
for (JsonNode version : field(fields, fieldIds, TicketField.FIX_VERSIONS)) {
    String name = version.path("name").asText(null);
    if (name != null) {
        fixVersions.add(name);
    }
}
```
`component` is a **single value, not a list** per the UI-SPEC/CONTEXT wording ("the ticket's Jira
component") even though Jira's `fields.components` is itself an array of `{id, name}` objects — take
the **first** entry's `name`, defaulting to `null`/empty (mapped to "Uncategorised" at the
`ReleaseViewAssembler` layer per D-05-03, not at the adapter layer):
```java
String component = null;
JsonNode componentsNode = field(fields, fieldIds, TicketField.COMPONENT);
if (componentsNode.isArray() && !componentsNode.isEmpty()) {
    component = componentsNode.get(0).path("name").asText(null);
}
```
Add `COMPONENT("Components")` to `TicketField.java` (mirrors `FIX_VERSIONS("Fix versions")`, line 12)
and append `component` to `TicketDetail`'s record components (after `fixVersions`, matching
declaration order convention). This field flows through `ReleaseTicketItemEntity` (new column,
V5 migration) exactly the way `fixVersions` already does (see
`ReleaseRepositoryAdapter.java:213`, `entity.setFixVersions(ticket.fixVersions())` — add the
`component` equivalent immediately after it).

### `application/ReleaseViewAssembler.java` (modified — D-05-04 grouping dimension)

**Analog:** itself, current revision, `groups()` (lines 58-82):
```java
// D-04-02: Mustache is logic-less and cannot group, so grouping is always a view-model concern.
// Issue type is DOC-02's stated default and the only dimension currently fetched (D-02-02);
// manual items have a category instead, so they group by that. Phase 5 adds component/epic/label
// as further dimensions on this same shape rather than reshaping the view-model.
private static List<GroupView> groups(List<ItemView> items) {
    List<GroupView> result = new ArrayList<>();
    Map<String, List<ItemView>> byIssueType = new TreeMap<>();
    for (ItemView item : items) {
        if (!item.isManual()) {
            byIssueType.computeIfAbsent(item.issueType(), k -> new ArrayList<>()).add(item);
        }
    }
    byIssueType.forEach((issueType, groupItems) -> result.add(new GroupView(issueType, List.copyOf(groupItems))));
    ...
```
The comment **already anticipates this exact change** ("Phase 5 adds component ... as further
dimensions on this same shape"). Add a `groups(List<ItemView> items, GroupingDimension dimension)`
overload (or a parameter on the existing method) that switches the `byIssueType.computeIfAbsent(...)`
key between `item.issueType()` and `item.component()` (defaulting empty/null component to
`"Uncategorised"` per D-05-03 — this is the one correct place for that default, not the Jira
adapter). `assemble(Release release)` needs the calling `Template`'s grouping-dimension setting
threaded in — a new parameter, `assemble(Release release, GroupingDimension dimension)`, called from
`DocumentGenerationService` with the active template's stored dimension.

### `domain/template/PlaceholderCatalog.java` (modified)

**Analog:** itself, current revision — one `ITEM`-scope entry to copy the shape of exactly
(lines 55-57, `reporter`):
```java
new PlaceholderEntry(PlaceholderScope.ITEM, "reporter", PlaceholderKind.VARIABLE, null,
        "The reporter's display name. Empty for manual items.", List.of("Body only", "Ticket only")),
```
Add, per the UI-SPEC's exact copy: `new PlaceholderEntry(PlaceholderScope.ITEM, "component",
PlaceholderKind.VARIABLE, null, "The ticket's Jira component, or 'Uncategorised' if none is set.
Empty for manual items.", List.of("Body only", "Ticket only"))`. Also update the existing `groups`
entry's description string (line 33-36) per the UI-SPEC's exact new copy. **Remember**: this file
has a reflection-drift test (`PlaceholderCatalogTest`, per D-04-20's comment) — the new `ItemView`
field and this catalog entry must land together or that test fails the build.

### `src/main/resources/db/migration/V5__document_generation.sql`

**Analog:** `src/main/resources/db/migration/V4__template_authoring.sql`

**Comment-with-decision-IDs convention and enum-as-CHECK-constraint convention** (lines 1-13):
```sql
-- Document templates: many named templates per document type with exactly one active (D-04-12).
-- ...
CREATE TABLE template (
    id                   UUID PRIMARY KEY,
    name                 VARCHAR(200) NOT NULL,
    document_type        VARCHAR(32)  NOT NULL
                         CHECK (document_type IN ('RELEASE_NOTES', 'RUNBOOK', 'APPROVAL')),
    ...
```
V5 needs: (1) `ALTER TABLE release_ticket_item ADD COLUMN component VARCHAR(255)` — nullable, no
default, no backfill (D-05-03) — add a one-line comment citing D-05-03 exactly the way V4 cites
D-04-12/D-04-13; (2) a new `document_snapshot` table with the same `CHECK (document_type IN (...))`
enum convention as `template.document_type` (line 12-13 above) and a `UNIQUE (release_id,
document_type, revision)` constraint (the append-only-revision analog of V4's
`template_one_active_per_type_idx` partial unique index, though this one is a plain composite
unique, not partial); (3) a `grouping_dimension` column on `template` (`VARCHAR(16) NOT NULL DEFAULT
'ISSUE_TYPE' CHECK (grouping_dimension IN ('ISSUE_TYPE', 'COMPONENT'))`) for D-05-04. No `ON DELETE
CASCADE` from `document_snapshot` to `template` — D-05-05 explicitly requires the snapshot to survive
template deletion, the opposite of `template_logo`'s cascade-on-delete-with-template relationship.

## Frontend

### `frontend/src/api/documents.ts` (new)

**Analog:** `frontend/src/api/templates.ts`

**Query-key + typed-hook convention** (lines 4-12, 107-145):
```typescript
export const DOCUMENT_TYPES = ['RELEASE_NOTES', 'RUNBOOK', 'APPROVAL'] as const;
export type DocumentType = (typeof DOCUMENT_TYPES)[number];

export const DOCUMENT_TYPE_LABELS: Record<DocumentType, string> = {
  RELEASE_NOTES: 'Release Notes',
  RUNBOOK: 'Runbook',
  APPROVAL: 'Approval',
};
...
export const templateQueryKey = (templateId: string) => ['template', templateId] as const;

export function useTemplate(templateId: string) {
  return useQuery({
    queryKey: templateQueryKey(templateId),
    queryFn: () => apiFetch<Template>(`/api/templates/${templateId}`),
    retry: false,
  });
}
```
`DOCUMENT_TYPES`/`DOCUMENT_TYPE_LABELS` **already exist in `templates.ts` (lines 4-12) — import them,
do not redeclare**. New query keys: `readinessQueryKey(releaseId)`,
`documentQueryKey(releaseId, documentType, revision)`. New mutation shape for generate/regenerate —
copy `useReleaseMutation`'s generic-wrapper pattern from `frontend/src/api/releases.ts` (lines
154-163):
```typescript
function useReleaseMutation<TVars>(request: (vars: TVars) => Promise<Release>) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: request,
    onSuccess: (release) => {
      queryClient.setQueryData(releaseQueryKey(release.id), release);
      queryClient.invalidateQueries({ queryKey: ['releases'] });
    },
  });
}
```
**Export downloads have no analog anywhere in this codebase** — every existing `apiFetch` call
expects a JSON body. See "No Analog Found" below for the concrete gap and the recommended shape.

### `frontend/src/routes/ReleasesRoute.tsx` (modified — DOC-09 columns)

**Analog:** itself, current revision — the `<table>` head/body pattern (lines 88-140):
```tsx
<th scope="col" className="px-4 py-2 text-left font-semibold text-gray-700">
  Last refreshed
</th>
...
<td className="whitespace-nowrap px-4 py-2 text-gray-700">
  {r.lastRefreshedAt ? new Date(r.lastRefreshedAt).toLocaleString() : '—'}
</td>
```
Append three `<th>`/`<td>` pairs in `DOCUMENT_TYPES` order (imported from `api/templates.ts`),
each `<td>` rendering the shared `ReadinessPill` component (see Shared Patterns below) fed by a
per-release readiness map. **Existing `Frozen`/`Open` pill markup is the copy-source for that
component's visual shape** (lines 124-128):
```tsx
{r.status === 'FROZEN' ? (
  <span className="rounded-full bg-gray-800 px-2 py-0.5 text-sm text-white">Frozen</span>
) : (
  <span className="rounded-full bg-gray-100 px-2 py-0.5 text-sm text-gray-700">Open</span>
)}
```
Note the readiness pill is **not** `rounded-full`/`text-sm` like this — UI-SPEC specifies
`text-xs font-semibold` with amber/green/gray variants, not the Frozen/Open gray-scale styling.
Copy the conditional-render *structure*, not these exact classes.

### `frontend/src/routes/DocumentViewerRoute.tsx` (new)

**Analog 1 (route param + search-param shape):** `frontend/src/routes/TemplateDetailRoute.tsx`
```tsx
const { templateId } = useParams({ from: '/authenticated/templates/$templateId' });
```
and, for the `?revision` search param, `frontend/src/router.tsx`'s `releaseDetailRoute.validateSearch`
(lines 66-72):
```tsx
validateSearch: (
  search: Record<string, unknown>,
): { added?: number; alreadyPresent?: number; notFound?: number } => ({
  added: typeof search.added === 'number' ? search.added : undefined,
  ...
}),
```
The new route needs `validateSearch: (search): { revision?: number } => ({ revision: typeof
search.revision === 'number' ? search.revision : undefined })`, registered in `router.tsx` at
`/releases/$releaseId/documents/$documentType` under `authenticatedRoute`, following the exact
`createRoute({ getParentRoute, path, validateSearch, component })` shape both existing routes use.

**Analog 2 (chrome markup to reuse/mirror):** `frontend/src/components/TemplatePreview.tsx`, the
cover-block/header/footer JSX (lines 296-320):
```tsx
{chrome.coverEnabled && (
  <div className="mb-6 rounded border border-gray-300 bg-white p-8 text-center shadow-sm">
    {props.logoUrl && <img src={props.logoUrl} alt="" className="mx-auto max-h-20" />}
    <p className="text-2xl font-semibold">{renderChromeNodes(resolvedCoverTitle)}</p>
    <p className="mt-2 text-base text-gray-600">{renderChromeNodes(resolvedCoverSubtitle)}</p>
  </div>
)}
...
<div className="prose prose-gray max-w-none p-4" dangerouslySetInnerHTML={{ __html: outcome.html }} />
```
UI-SPEC is explicit that the viewer must reuse this markup "so a generated document looks
pixel-identical to what the author saw." **Key difference**: `TemplatePreview` resolves Mustache
+ markdown-it live against a `ReleaseView`; the document viewer's snapshot is **already
Mustache-resolved** (D-05-05), so it only needs the `markdown-it` half (`md.render(...)`, line 9 +
228) — not `Mustache.render`, not `markUnknownTokens`, not `computePreview`'s four-state outcome
logic (a saved snapshot cannot have unknown tokens or empty-body states the way a live draft can).
The UI-SPEC's own recommendation is to extract `TemplatePreview`'s cover/header/footer JSX plus its
`md` instance into a small shared module — do that extraction as part of this plan, not a
speculative future refactor.

### `frontend/src/api/auth.ts` (modified — new reason strings)

**Analog:** itself, current revision, `REASON_MESSAGES` (lines 63-89 pattern):
```typescript
const REASON_MESSAGES: Record<string, string> = {
  INVALID_CREDENTIALS: 'Your Jira credential is no longer valid. Enter a current Jira API token.',
  ACCOUNT_MISMATCH: 'That API token belongs to a different Jira account. Sign out and sign in as that account.',
  SITE_NOT_CONFIGURED: 'No Jira Cloud site is configured yet. Set one on the Settings screen first.',
  ...
};
```
Add `DOCUMENT_NOT_FOUND`, `NO_ACTIVE_TEMPLATE`, `EXPORT_FAILED` entries here, matching
`GlobalExceptionHandler`'s new reason codes one-for-one and the exact copy the UI-SPEC's
Copywriting Contract table specifies. `messageForReason` (lines 93-99) needs no change — it already
falls back generically for any reason not in the map, but the whole point of this file is that every
reason the backend can emit gets a specific, better message here.

## Shared Patterns

### Bounded rendering — generation must call through `RenderService`, never around it
**Source:** `src/main/java/com/evision/releasemgmt/application/RenderService.java` (whole file, esp.
lines 50-57, the public `render(...)` entry point)
**Apply to:** `DocumentGenerationService` exclusively — `DocumentExportService`/`PdfExportAdapter`/
`DocxExportAdapter` do **not** go through `RenderService` again (D-05-05: export re-parses already-
bounded stored Markdown; see Pitfall 5 in RESEARCH.md — this is a deliberate, recorded non-goal, not
an oversight to "fix").
```java
public RenderResult render(String source, ReleaseView view, String field) {
    ScanResult scan = SCANNER.scan(source, field);
    enforceCollectionCap(view);
    String safeSource = markUnknownTokens(source, scan.unknownTokens());
    Map<String, Object> context = RenderContextMapper.toContext(view);
    String html = renderBounded(safeSource, context);
    return new RenderResult(html, scan.unknownTokens());
}
```

### ArchUnit confinement — new render libraries follow the exact existing rule shape
**Source:** `src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java`, lines 54-62
```java
@Test
void renderLibrariesLiveOnlyInTheRenderAdapter() {
    ArchRule rule = noClasses()
            .that().resideOutsideOfPackage("..adapters.render..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.samskivert..", "com.vladsch..")
            .allowEmptyShould(true);
    rule.check(classes);
}
```
**Apply to:** add `"com.openhtmltopdf.."` and `"org.docx4j.."` to this exact same rule's package
list (or add a second, identically-shaped `@Test` method) — do not write a new rule structure.

### Exception → ProblemDetail mapping — the `problem(...)` helper convention
**Source:** `src/main/java/com/evision/releasemgmt/adapters/web/GlobalExceptionHandler.java`, lines
82-86 and 236-242
**Apply to:** every new domain exception this phase introduces (`DocumentNotFoundException`,
`NoActiveTemplateException`, an `ExportFailedException` or equivalent).
```java
@ExceptionHandler(ReleaseNotFoundException.class)
public ProblemDetail handleReleaseNotFound(ReleaseNotFoundException ex) {
    return problem(HttpStatus.NOT_FOUND, "Release not found",
            "This release doesn't exist or has been deleted.", "RELEASE_NOT_FOUND");
}
...
private ProblemDetail problem(HttpStatus status, String title, String detail, String reason) {
    ProblemDetail pd = ProblemDetail.forStatus(status);
    pd.setTitle(title);
    pd.setDetail(detail);
    pd.setProperty("reason", reason);
    return pd;
}
```

### Binary response headers — `ResponseEntity<byte[]>` with explicit Content-Type/Disposition
**Source:** `src/main/java/com/evision/releasemgmt/adapters/web/TemplateController.java`, lines
155-168 (see full excerpt above under `DocumentController`)
**Apply to:** both export endpoints. **Filename sanitization is new exposure** this phase
introduces — not present in the source pattern, must be added (see RESEARCH.md Pattern 4's
anti-pattern note; this is the one place the analog must NOT be copied verbatim).

### `role="alert" data-reason={reason}` error convention (frontend)
**Source:** `frontend/src/routes/ReleasesRoute.tsx`, lines 48-52 (and identically in
`ReleaseDetailRoute.tsx`, lines 92-120)
```tsx
<p role="alert" data-reason={problem.reason} className="text-sm text-red-600">
  {messageForReason(problem)}
</p>
```
**Apply to:** every new error state in the Documents section, the readiness columns, and the
document viewer — UI-SPEC's Copywriting Contract table specifies this exact convention per error
row, scoped per document type (a Release Notes failure must not bleed into the Runbook row).

## No Analog Found

Files/sub-problems with no close match in the codebase — planner should treat these as genuinely
new engineering, using RESEARCH.md's Code Examples/Pattern sections as the primary guide instead of
a codebase analog. A forced analog would mislead the planner here.

| File / Concern | Role | Data Flow | Closest Thing In Codebase | What Does NOT Transfer |
|---|---|---|---|---|
| `adapters/render/DocxExportAdapter.java`'s AST-to-OOXML walker (the `Paragraph`/`Heading`/`StrongEmphasis`/`TableBlock`/etc. → `P`/`R`/`Tbl` node mapping) | service (transform) | transform | `MustacheFlexmarkRenderAdapter`'s `neutralizeRawHtml` (lines 80-92) — the only other place this codebase walks a flexmark `Node` tree | `neutralizeRawHtml` walks the tree to **replace two specific node types with Text**; it is not a general visitor and gives no pattern for docx4j's completely different object model (`P`/`R`/`RPr`/`Tbl` construction, `numbering.xml` for lists). RESEARCH.md's own "fixed AST vocabulary" table is the actual spec to build against — there is no shortcut here. |
| `adapters/render/PdfExportAdapter.java`'s CSS `@page`/`counter()` chrome shell construction | service (transform) | transform | `MustacheFlexmarkRenderAdapter`'s existing `HtmlRenderer.render(document)` call (line 52) — gets the body XHTML fragment "almost for free" per RESEARCH.md | The **existing renderer produces a body fragment only**; wrapping it in a full XHTML document with `<style>@page{...}</style>`, a running-header `position: running()` element, and a cover page is new code with zero precedent in this codebase — copy the openhtmltopdf CSS from RESEARCH.md's Pattern 3 directly, not from any existing file. |
| Binary export streaming beyond the single logo case (specifically: the **two-format, two-endpoint, per-revision** export shape) | controller | file I/O | `TemplateController.downloadLogo` | `downloadLogo` serves **one stored blob with no generation step** — it is a pure read. Export **generates** bytes on every call (fresh `PdfRendererBuilder`/`WordprocessingMLPackage` per RESEARCH.md Pattern 2) from stored Markdown; the header-setting idiom transfers, the "just read and return" simplicity does not. Also: `downloadLogo`'s filename is upload-validated, not user-free-text-derived — see the sanitization gap noted above. |
| `ReadinessPill.tsx` (three-/four-state colored pill reused across two screens) | component | transform | The inline ternary `Frozen`/`Open` pill in `ReleasesRoute.tsx`/`ReleaseDetailRoute.tsx` (2-state, gray-only, never extracted to its own component) | The existing pill is **inlined twice, never a component**, is **2-state not 4-state**, and uses gray-scale only — no amber/green semantic color exists anywhere in the frontend today (`SettingsRoute.tsx`'s `green-600` is inline confirmation *text*, not a pill). This phase must write this component from the UI-SPEC's Color/Copywriting Contract tables directly; the existing pill only supplies the "a `<span>` with rounded/colored classes inside a table cell" idea, nothing about the state machine or the four-way branch. |
| Export-download API calls in `frontend/src/api/documents.ts` (`apiFetch` returning a `Blob`/triggering `Content-Disposition: attachment` download, not JSON) | provider | file I/O | Every existing hook in `api/templates.ts`/`api/releases.ts` (all `apiFetch<T>(...)` expect and parse JSON) | `apiFetch`'s shared client (not read in this session, but every call site confirms JSON-only usage) has never been asked to hand back a `Blob` or trigger a browser download — the planner needs to check `frontend/src/api/client.ts`'s actual implementation and either add a sibling `apiFetchBlob`-style helper or drive the download via a plain `<a href="/api/releases/.../export.pdf" download>` link (bypassing `fetch`/React Query entirely, letting the browser handle the binary transfer natively — often the simpler, more standard choice for this exact case, and worth raising at plan time rather than assuming a JSON-shaped client must be stretched to fit). |

## Metadata

**Analog search scope:** `src/main/java/com/evision/releasemgmt/{domain,ports,application,adapters,config}`,
`src/main/resources/db/migration`, `src/test/java/com/evision/releasemgmt/ArchitectureBoundaryTest.java`,
`frontend/src/{routes,components,api,router.tsx}`
**Files scanned:** ~45 backend source files (read/grepped), 9 frontend source files (read in full or targeted)
**Pattern extraction date:** 2026-08-10
