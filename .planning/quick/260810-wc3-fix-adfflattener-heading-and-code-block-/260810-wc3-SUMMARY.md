---
task: 260810-wc3
type: quick
subsystem: backend / jira-adapter
tags: [adf-flattener, bugfix, tdd]
key-files:
  created: []
  modified:
    - src/main/java/com/evision/releasemgmt/adapters/jira/AdfFlattener.java
    - src/test/java/com/evision/releasemgmt/adapters/jira/AdfFlattenerTest.java
decisions: []
metrics:
  duration: ~15 min
  completed: 2026-08-10
---

# Quick Task 260810-wc3: Fix AdfFlattener heading and code block newlines Summary

Fixed two confirmed defects in `AdfFlattener` that corrupted every generated document containing
the `{{description}}` placeholder: an unhandled `heading` node type that glued headings to the
following block, and a `codeBlock` closing fence missing its preceding newline, which caused
CommonMark to never recognise the fence and swallow the rest of the document.

## What Changed

**`AdfFlattener.java`** (`src/main/java/com/evision/releasemgmt/adapters/jira/AdfFlattener.java`):
- Added `case "heading"`: emits `"#".repeat(level) + " "` before walking children, then a
  trailing `\n`. `level` is read from `attrs.level`, defaults to `1` when absent, and is clamped
  to `[1, 6]` via `Math.clamp` to protect the class's never-throw invariant against a malformed
  level (e.g. `0` or `9`).
- Modified `case "codeBlock"`: after walking children, appends a newline before the closing
  ` ``` ` fence unless the buffer already ends with one (avoids a doubled blank line when the
  content already supplies its own trailing newline via `hardBreak`).

**`AdfFlattenerTest.java`** (`src/test/java/com/evision/releasemgmt/adapters/jira/AdfFlattenerTest.java`):
- Corrected `codeBlockWrapsContentInFences`'s expected value from the previously-pinned broken
  output `"```\nvar x = 1;```\n"` to the correct `"```\nvar x = 1;\n```\n"`.
- Added `headingProducesAtxPrefixOnItsOwnLine` — reduces the live symptom
  (`DescriptionWhen sending...`) to a unit test: heading level 2 followed by a paragraph now
  flattens to `"## Description\nBody text\n"`.
- Added `headingWithoutLevelAttrDefaultsToLevelOne` — heading with no `attrs` flattens to
  `"# Title\n"`.
- Added `headingWithOutOfRangeLevelStaysValidMarkdown` — heading with `level: 9` clamps to
  `"###### Deep\n"`.

## TDD Cycle

- RED (`63ab072`): corrected the wrong codeBlock assertion and added the three heading tests.
  Confirmed exactly 4 failures (the corrected codeBlock assertion + 3 new heading tests), all
  other tests in the class green.
- GREEN (`1396ffc`): added the `heading` case and the newline-terminated codeBlock fence. All 16
  tests in `AdfFlattenerTest` pass; full backend suite returns 544 tests green (541 baseline + 3
  new heading tests), 0 failures, 0 errors.

## Verification

- `mvn -Dtest=AdfFlattenerTest test` — 16/16 pass, including the two unmodified bullet-list tests
  (proves `listItem` still terminates correctly via its child `paragraph`).
- `mvn test` — 544 tests run, 0 failures, 0 errors, BUILD SUCCESS.
- `grep -n 'case "heading"' AdfFlattener.java` — exactly one match (line 32).
- `git diff --stat` across both commits touches exactly two files: `AdfFlattener.java` and
  `AdfFlattenerTest.java`. No frontend file touched.

## Deviations from Plan

None - plan executed exactly as written.

## Related But Not Resolved Here

The original todo (`.planning/todos/pending/adf-flattener-heading-and-codeblock-newlines.md`,
now moved to `.planning/todos/completed/`) also flagged a **separate, related** issue: the
starter release-notes template places `{{description}}` unindented directly after the
`- **{{title}}**` bullet, which causes Markdown to treat a multi-line description as a lazy
continuation of the bullet's paragraph rather than nesting it. That is a template-authoring
issue, not a flattening defect, and is **not resolved by this task** — it still belongs to plan
05-09.

## Commits

- `63ab072` — test(adf-flattener): correct codeBlock fence assertion and add heading coverage
- `1396ffc` — fix(adf-flattener): handle heading nodes and newline-terminate codeBlock fence

## Self-Check: PASSED

- FOUND: src/main/java/com/evision/releasemgmt/adapters/jira/AdfFlattener.java (heading case present, line 32)
- FOUND: src/test/java/com/evision/releasemgmt/adapters/jira/AdfFlattenerTest.java (3 new heading tests + corrected codeBlock assertion)
- FOUND: commit 63ab072 in git log
- FOUND: commit 1396ffc in git log
