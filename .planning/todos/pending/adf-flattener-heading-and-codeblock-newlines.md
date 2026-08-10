---
type: bug
created: 2026-08-10
source: user-report (template editor preview, Phase 4 surface)
resolves_phase: 05
severity: high
files:
  - src/main/java/com/evision/releasemgmt/adapters/jira/AdfFlattener.java
  - src/test/java/com/evision/releasemgmt/adapters/jira/AdfFlattenerTest.java
---

# ADF flattening breaks document formatting when `{{description}}` is used

Reported by the user against the live template editor: inserting the `{{description}}`
placeholder into the release-notes template corrupts the rendered preview. Ticket
descriptions are the only field that carries multi-block ADF, so this is invisible until
`{{description}}` is on the template.

## Symptoms

1. The description's first heading is glued to the following paragraph —
   rendered as `DescriptionWhen sending an MT192 cancellation request...`.
2. Everything after the description's first code block renders as one giant
   unterminated code block, swallowing the remainder of the document.

## Root causes (confirmed by reading the source)

**1. `heading` is an unhandled node type.** `AdfFlattener.walk` (`AdfFlattener.java:26-52`)
has no `case "heading"`. Headings fall through to `default`, which emits the text content
with no `#` prefix and — critically — no trailing newline. The heading therefore merges
into the next block's first word.

**2. The `codeBlock` closing fence is not newline-terminated.**
`AdfFlattener.java:39-43` appends ` ```\n `, walks children (text nodes append raw text with
no trailing newline), then appends ` ``` `. The content and the closing fence end up on the
same line, e.g. `` :79:/AGNT/``` ``. A CommonMark closing fence must begin its own line, so
it is not recognised, the block never closes, and it consumes the rest of the document.

`AdfFlattenerTest.java:140` currently **asserts this broken output**
(`"```\nvar x = 1;```\n"`), so the defect is pinned in place by its own test. Any fix must
correct that assertion — it is a wrong expectation, not a regression.

Note `listItem` (`AdfFlattener.java:35-38`) relies on a child `paragraph` to supply its
trailing newline. Check it still terminates correctly once headings emit their own newlines.

## Related but separate — template authoring, not flattening

Even with both bugs fixed, the starter release-notes template places `{{description}}`
unindented on the line directly after the `- **{{title}}**` bullet. Markdown treats a
multi-line description there as a lazy continuation of the bullet's paragraph rather than
nesting it under the bullet. Fixing this means indenting the placeholder line (or emitting
the description as an indented block), and it overlaps plan 05-09's placeholder/template
work — resolve the two together rather than twice.

## Why deferred

Reported mid-Wave-2 of Phase 5 with four executors running in worktrees branched off the
current HEAD. `AdfFlattener.java` is touched by none of them, but landing a commit on the
shared base mid-wave buys merge risk for no schedule gain. Fix after Wave 2 merges.
