# Orchstro Release Notes — template

Modelled on `Orchstro 4.2.0 Release Notes.docx` (Release4.3 folder), mapped onto the Phase 4
view-model. Paste the **Body** section into the template editor and set the **Chrome** fields on the
Chrome tab.

Every placeholder below is verified against `PlaceholderCatalog` — no invented tokens.

---

## Chrome fields

| Field | Value |
|---|---|
| Cover page | **on** |
| Cover title | `Release Notes` |
| Cover subtitle | `Version {{release.version}}` |
| Header text | `Orchstro {{release.version}} Release Notes` |
| Footer text | `Orchstro` |
| Page numbering | **on** |
| Logo | upload `word/media/image1.jpeg` extracted from the source .docx |

The source footer reads `Orchstro   |   Page X of Y`. The `| Page X of Y` part is supplied by the
page-numbering toggle in Phase 5's renderers (PDF via CSS `counter(page)`, DOCX via `PAGE`/`NUMPAGES`
field codes), so the footer text field holds only the literal `Orchstro`.

---

## Body

```mustache
## Release Summary

Version {{release.version}} of Orchstro delivers improvements across the platform.
Replace this paragraph with the release narrative — it is prose, not generated from tickets.

### Key Enhancements

- Replace with the handful of headline items for this release.
- These are editorial: a curated summary, not one line per ticket.

### Stability & Bug Fixes

This release also delivers fixes and optimizations across:

- Message mapping and parsing
- Matching engine and retry UI
- Dashboard and search UI
- PDF generation and email notifications
- Security, compliance, and database integrity

{{#groups}}
## {{groupName}}

{{#items}}
### {{key}}  |  {{title}}

{{description}}

{{^isManual}}
{{#comments}}
{{text}}
{{/comments}}
{{/isManual}}

{{#isManual}}
*Category: {{category}}*
{{/isManual}}

{{/items}}
{{/groups}}

## Known Issues

Add any known issues here, or record them as manual items on the release so they appear above.

## Updated Services

The following services were updated as part of this release:

| Service | Service |
|---|---|
| Archival Management API | UC Engine |
| Archival Search API | UC Management API |
| alerts-management-ms-api | License Management API |
| Bank Management API | Matching API |
| batch-entry-parser-ms-api | MX Reader |
| Correspondent Fees API | mt-mx-conversion-ms-engine |
| Adapter Engine | Notification Management API |
| Alerts Engine | Correspondent Fees Engine |
| Collector Engine | Posting Engine |
| Matching Engine | Posting API |
| Notification Engine | Reports API |
| Reader Engine | Shared MS API |
```

---

## What matches, and what does not

### Matches the source document

- Cover page, running header, footer, page numbering, logo — all expressible as chrome fields.
- One section per group, with a heading per ticket carrying its key and summary.
- Ticket description under each entry.
- Manual items (config changes, DB scripts, infrastructure) appear inline with ticket-backed items,
  because `{{#items}}` is one unified collection (D-04-01).
- The editorial Release Summary and the static Updated Services table are literal Markdown — they are
  not derived from Jira in the source document either.

### Cannot be reproduced yet — and why

| Source document feature | Blocker | Resolves in |
|---|---|---|
| Sections named **Message Mapping Enhancements / Functional Enhancements / Resolved Issues / Security Audit & Compliance** | `{{#groups}}` is hard-wired to **issue type** (D-04-02). Those headings are a *component* or *label* grouping, and components/labels are not fetched from Jira at all (D-02-02, carried forward by D-04-17). Today `{{groupName}}` yields `Bug`, `Story`, `Task`. | Phase 5 (DOC-02) — it must extend the Jira field selection, the `release_ticket_item` snapshot and the view-model together |
| `CNV-2843    **Swift Day Screen**  \|  MT196 Tag 79…` — the component before the pipe | Same: no `component` in the ItemView | Phase 5 |
| **Contents / table of contents** | A Word auto-field. Markdown + Mustache has no TOC construct, and neither renderer generates one today | Would need a Phase 5 renderer feature; not currently scoped |
| **`Resolution:` as a distinct labelled block** | Not a separate field — in the source it is a convention inside the ticket's description text. `{{description}}` renders it as written | Nothing to fix; write "Resolution:" in the Jira description and it carries through |
| A date on the cover such as "Released July 2026" | `{{generatedAt}}` does not exist — D-04-03 restricted the root context to release data only | Use `{{release.frozenAt}}`, or revisit D-04-03 |

### The one that matters

The five named sections are the document's defining structure, and **they are the one thing this
template cannot reproduce today**. Two options:

1. **Ship it grouped by issue type now.** Headings become `Bug` / `Story` / `Task`. Structurally
   correct, editorially wrong.
2. **Flatten it.** Drop `{{#groups}}`, use a single `{{#items}}` loop, and let the release manager add
   section headings by hand. Closer to the source's look, no false grouping.

Option 2 is the better interim, and swapping is a two-line edit: replace the `{{#groups}} … {{/groups}}`
wrapper with a bare `{{#items}} … {{/items}}`.

Once Phase 5 adds component/label grouping, the section headings in the source document become
expressible directly and this template converges on the original.
