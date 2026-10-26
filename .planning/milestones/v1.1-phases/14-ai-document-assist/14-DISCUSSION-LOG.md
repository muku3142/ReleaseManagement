# Phase 14 Discussion Log (2026-10-15)

This log is for human reference only; CONTEXT.md is canonical. The user picked the recommended option for every question.

## Revision semantics
- Is the AI revision current? Options were "current like any revision" and "side copy". Chosen: current.
- Regenerating after an AI revision: options were "new revision on top" and "block until confirmed". Chosen: new revision on top.
- Saving a summary: options were "view/copy only" and "saveable". Chosen: view/copy only.

## Compare and edit UX
- Layout: options were side by side, tabs and a diff view. Chosen: side by side.
- Editing: options were raw Markdown with a preview toggle, or a rich-text editor. Chosen: raw Markdown with preview.
- Marking: options were a history badge plus viewer banner with no export stamp, or also stamping exports. Chosen: badge and banner, no stamp.

## Modes and prompts
- Prompts per document type: options were per-type prompts in config, or one prompt per mode. Chosen: per type.
- Inputs: options were document text only, adding tickets and commits, or adding a Jira field. Chosen: document text only.
- Custom instructions: options were free text with the last few remembered, free text only, or presets only. Chosen: free text with the last 5 kept in memory.

## Size and fact safety
- Too long: options were a clear message, or chunking by section. Chosen: clear message.
- Fact check: options were a ticket-key diff that warns, one that blocks, or no check. Chosen: diff that warns.
- Warning: options were a banner on the result and viewer, or a confirmation checkbox on save. Chosen: banner.
