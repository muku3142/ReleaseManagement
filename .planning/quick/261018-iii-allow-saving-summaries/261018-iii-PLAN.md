---
phase: quick-261018-iii
plan: 01
type: execute
wave: 1
depends_on: []
files_modified:
  - src/main/resources/db/migration/V13__document_snapshot_ai_mode_summarize.sql
  - src/main/java/com/evision/releasemgmt/domain/document/AiMode.java
  - src/main/java/com/evision/releasemgmt/application/assistant/AssistantTransformService.java
  - src/test/java/com/evision/releasemgmt/application/assistant/AssistantTransformServiceTest.java
  - src/test/java/com/evision/releasemgmt/adapters/web/AssistantTransformStreamIT.java
  - frontend/src/api/documents.ts
  - frontend/src/documentAssist/resultState.ts
  - frontend/src/components/AiResultCard.tsx
  - frontend/src/components/DocumentsSection.tsx
  - frontend/src/routes/DocumentViewerRoute.tsx
  - frontend/tests/documentAssist/resultState.test.ts
  - frontend/tests/components/AiResultCard.test.tsx
  - frontend/tests/components/DocumentsSection.test.tsx
  - .planning/phases/14-ai-document-assist/14-CONTEXT.md
  - .planning/phases/14-ai-document-assist/14-UI-SPEC.md
  - .planning/phases/14-ai-document-assist/14-AI-SPEC.md
  - docs/document-assist-flow.md
autonomous: true
requirements: [ASST-10]

must_haves:
  truths:
    - "A complete Summarize result can be edited and saved as revision N+1 with the Accept button, exactly like Clean up"
    - "The saved summary revision records ai_mode=SUMMARIZE and the server's configured summarize instruction for that document type"
    - "Saving a summary still requires a complete result, still returns 409 when the source revision is superseded, and still copies the staleness basis from the source revision"
    - "Revision history shows 'AI instruction: Summarize' and the viewer banner names the summarize mode"
    - "D-14-13 in 14-CONTEXT.md records the reversal of D-14-03, and the UI-SPEC, AI-SPEC §6 and document-assist-flow §7 agree with it"
  artifacts:
    - path: "src/main/resources/db/migration/V13__document_snapshot_ai_mode_summarize.sql"
      provides: "ai_mode CHECK widened to CLEAN_UP, CUSTOM, SUMMARIZE"
      contains: "document_snapshot_ai_mode_check"
    - path: "src/main/java/com/evision/releasemgmt/domain/document/AiMode.java"
      provides: "SUMMARIZE enum constant"
      contains: "SUMMARIZE"
  key_links:
    - from: "AssistantTransformService.saveAiRevision"
      to: "AiRevisionService.save"
      via: "TransformMode.SUMMARIZE mapped to AiMode.SUMMARIZE with config.instruction(SUMMARIZE, type)"
      pattern: "AiMode.SUMMARIZE"
    - from: "frontend/src/components/AiResultCard.tsx"
      to: "canSave(state, mode)"
      via: "Accept button enabled for SUMMARIZE"
      pattern: "canSave"
---

<objective>
Let a complete Summarize result be saved as a new AI-assisted revision, the same way Clean up is. This is a user decision made 2026-10-18 that reverses D-14-03. It is recorded as D-14-13.

Purpose: the user wants to keep a summary as a revision rather than only copying or downloading it.
Output: a V13 migration, a backend save path for SUMMARIZE, frontend Accept for summaries, and docs that agree with the change.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/STATE.md
@./CLAUDE.md
@.planning/phases/14-ai-document-assist/14-CONTEXT.md

<interfaces>
Current state, extracted from the codebase. Use it directly.

V12 (src/main/resources/db/migration/V12__document_snapshot_ai_assist.sql) adds an inline column CHECK:
  `ADD COLUMN ai_mode VARCHAR(16) CHECK (ai_mode IN ('CLEAN_UP', 'CUSTOM'))`
Postgres auto-names an inline column CHECK `<table>_<column>_check`, so the name is `document_snapshot_ai_mode_check`.
The separate `document_snapshot_ai_all_or_none` constraint is unaffected. 'SUMMARIZE' fits VARCHAR(16).

AiMode.java (domain/document):
```java
// SUMMARIZE is never persisted (D-14-03): a summary is shown, not saved as a revision.
public enum AiMode { CLEAN_UP, CUSTOM }
```

AssistantTransformService.saveAiRevision (application/assistant), current body:
```java
if (mode == TransformMode.SUMMARIZE) {
    log.info("... outcome=SUMMARIZE_REJECTED", ...);
    throw new InvalidAssistRequestException("Summaries can't be saved as a revision.");
}
if (markdown == null || markdown.contains(NUL)) { throw ... }
String instruction = resolveInstruction(type, mode, customInstruction);   // non-CUSTOM -> config.instruction(mode, type)
AiMode aiMode = mode == TransformMode.CLEAN_UP ? AiMode.CLEAN_UP : AiMode.CUSTOM;
DocumentSnapshot saved = aiRevisions.save(releaseId, type, sourceRevision, aiMode, instruction, markdown, actor);
```
AiRevisionService.save (application/AiRevisionService.java) owns the 409 superseded check and copying basis_generated_at. Do not change it.

Backend tests that assert rejection:
- AssistantTransformServiceTest.java ~line 258 `saveRejectsSummarize()`: expects InvalidAssistRequestException, "Summaries can't be saved as a revision.", never save, log "outcome=SUMMARIZE_REJECTED". Constant `SUMMARIZE_RUNBOOK = "Summarize the runbook."` is the configured runbook summarize instruction.
- AssistantTransformStreamIT.java ~line 316: posts `saveBody(releaseId, 2, "SUMMARIZE", "summary")`, expects 400 and 2 revisions, then posts a stale CLEAN_UP save on revision 1 expecting 409.

Frontend:
- frontend/src/api/documents.ts:11 `export type AiMode = 'CLEAN_UP' | 'CUSTOM';`
- frontend/src/documentAssist/resultState.ts:75 `return state.phase === 'complete' && mode !== 'SUMMARIZE' && state.buffer.trim() !== '';`
- frontend/src/components/AiResultCard.tsx: line ~203 `const editable = mode !== 'SUMMARIZE' && (...)`; ~332 onSave guard `|| mode === 'SUMMARIZE'`; ~364 `const showSave = mode !== 'SUMMARIZE' && (...)`; ~486-490 the "Summaries are for reading only..." note. The Edit/Source toggle label is "Source" for read-only results; it becomes "Edit" for a complete summary once `editable` is true.
- frontend/src/components/DocumentsSection.tsx:67 `return revision.aiMode === 'CUSTOM' ? \`AI instruction: "${...}"\` : 'AI instruction: Clean up';`
- frontend/src/routes/DocumentViewerRoute.tsx:14 `const AI_MODE_TEXT = { CLEAN_UP: 'Cleaned up', CUSTOM: 'edited with a custom instruction' } as const;` used in the banner "This revision was ${AI_MODE_TEXT[data.aiMode]} with the AI model ...". Adding SUMMARIZE to the AiMode type makes this a type error until a SUMMARIZE entry is added.
- Tests: frontend/tests/documentAssist/resultState.test.ts:79 `expect(canSave(complete, 'SUMMARIZE')).toBe(false);`; frontend/tests/components/AiResultCard.test.tsx ~198-210 (summary note and read-only expectations); frontend/tests/components/DocumentsSection.test.tsx (instruction line tests).
</interfaces>
</context>

<tasks>

<task type="auto" tdd="true">
  <name>Task 1: Backend - persist SUMMARIZE as an AI-assisted revision</name>
  <files>src/main/resources/db/migration/V13__document_snapshot_ai_mode_summarize.sql, src/main/java/com/evision/releasemgmt/domain/document/AiMode.java, src/main/java/com/evision/releasemgmt/application/assistant/AssistantTransformService.java, src/test/java/com/evision/releasemgmt/application/assistant/AssistantTransformServiceTest.java, src/test/java/com/evision/releasemgmt/adapters/web/AssistantTransformStreamIT.java</files>
  <behavior>
    - saveAiRevision(..., SUMMARIZE, null, "# E", actor) calls aiRevisions.save with AiMode.SUMMARIZE and instruction SUMMARIZE_RUNBOOK (the configured one; a client customInstruction is ignored), and logs outcome=SAVED.
    - IT: after the CLEAN_UP save (revision 2), a SUMMARIZE save on source revision 2 returns 201, revision 3, aiMode "SUMMARIZE", aiInstruction equal to properties.documentAssist().instruction(SUMMARIZE, RUNBOOK), markdown "summary". The basis is copied from the source revision. Assert basis equality the same way the existing CLEAN_UP save test does, if it does. If it does not, read revision 3 via `snapshots` and compare its basis to revision 2's.
    - IT: the stale save on revision 1 still returns 409. Add one stale SUMMARIZE save on revision 2 (now superseded by 3) that returns 409, and assert the revision count is unchanged.
  </behavior>
  <action>
    Per D-14-13 (reverses D-14-03):
    1. Create `V13__document_snapshot_ai_mode_summarize.sql`. Start with a one-line comment: "D-14-13: summaries can be saved as AI-assisted revisions (reverses D-14-03)." Then add:
       `ALTER TABLE document_snapshot DROP CONSTRAINT IF EXISTS document_snapshot_ai_mode_check;`
       `ALTER TABLE document_snapshot ADD CONSTRAINT document_snapshot_ai_mode_check CHECK (ai_mode IN ('CLEAN_UP', 'CUSTOM', 'SUMMARIZE'));`
       Do not edit V12, because Flyway checksums are immutable.
    2. In AiMode.java, delete the "never persisted" comment and add the constant `SUMMARIZE`.
    3. In saveAiRevision, delete the SUMMARIZE rejection block and its SUMMARIZE_REJECTED log. Replace the ternary with an exhaustive switch: `AiMode aiMode = switch (mode) { case CLEAN_UP -> AiMode.CLEAN_UP; case SUMMARIZE -> AiMode.SUMMARIZE; case CUSTOM -> AiMode.CUSTOM; };`. resolveInstruction already returns the configured instruction for non-CUSTOM modes, so don't change it. Leave the NUL/null check and the "only when complete is UI-only" comment unchanged. The completeness gate stays in the UI, per the existing comment.
    4. In AssistantTransformServiceTest, replace `saveRejectsSummarize` with `saveSummarizeStoresTheConfiguredInstruction`. It verifies `aiRevisions.save(releaseId, RUNBOOK, 2, AiMode.SUMMARIZE, SUMMARIZE_RUNBOOK, "# E", "a")`. Pass a non-null customInstruction such as "ignored" to prove the client text is not stored. Stub `aiRevisions.save` the same way the existing CLEAN_UP save test does. Check lastLog contains outcome=SAVED.
    5. In AssistantTransformStreamIT, replace the 400 SUMMARIZE block with the 201 path and the extra 409 from the behavior list. Keep the existing stale CLEAN_UP 409 assertion.
    Run Maven with JAVA_HOME set on each call.
  </action>
  <verify>
    <automated>JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -B -P '!frontend' clean test</automated>
  </verify>
  <done>The suite is green with no fewer tests than the 1260 baseline. `grep -rn SUMMARIZE_REJECTED src/` returns nothing. V13 applies on Testcontainers Postgres, which the IT proves.</done>
</task>

<task type="auto" tdd="true">
  <name>Task 2: Frontend - Accept and edit for summaries, history and viewer labels</name>
  <files>frontend/src/api/documents.ts, frontend/src/documentAssist/resultState.ts, frontend/src/components/AiResultCard.tsx, frontend/src/components/DocumentsSection.tsx, frontend/src/routes/DocumentViewerRoute.tsx, frontend/tests/documentAssist/resultState.test.ts, frontend/tests/components/AiResultCard.test.tsx, frontend/tests/components/DocumentsSection.test.tsx</files>
  <behavior>
    - canSave(complete non-empty, 'SUMMARIZE') === true. canSave for incomplete or empty buffer stays false.
    - AiResultCard with mode SUMMARIZE, after a complete run: the toggle shows "Edit" (not "Source"), the textarea is editable, the Accept button is present and enabled, the text "Summaries are for reading only" is absent, and clicking Accept posts mode SUMMARIZE with the buffer. A stopped or truncated summary shows the same blocked message as Clean up. The runbook verify banner still shows for a SUMMARIZE runbook.
    - DocumentsSection: a revision with aiMode 'SUMMARIZE' shows "AI instruction: Summarize".
  </behavior>
  <action>
    Per D-14-13:
    1. In documents.ts, set `AiMode = 'CLEAN_UP' | 'SUMMARIZE' | 'CUSTOM'`.
    2. In resultState.ts canSave, drop `mode !== 'SUMMARIZE'`. If `mode` is then unused, remove the parameter and update every caller. Keep the signature if TS/ESLint allows the unused parameter. The simpler choice is to remove it.
    3. In AiResultCard.tsx, remove the `mode !== 'SUMMARIZE'` conditions from `editable` and `showSave`, and the `|| mode === 'SUMMARIZE'` from the onSave guard. Delete the summary-only note block (~486-490). Summaries then use the same editable buffer, fact check and "Accept and save as revision N+1" button as Clean up.
    4. In DocumentsSection.tsx line 67, map the modes explicitly: CUSTOM -> the quoted instruction, SUMMARIZE -> 'AI instruction: Summarize', CLEAN_UP -> 'AI instruction: Clean up'.
    5. In DocumentViewerRoute.tsx AI_MODE_TEXT, add `SUMMARIZE: 'Summarized'`, matching the capitalisation of the existing 'Cleaned up' entry.
    6. Update the tests: flip the resultState assertion. In AiResultCard.test.tsx, rewrite the ~198-210 summary cases for the new behavior, and keep the runbook verify-banner case. Add a DocumentsSection case for the SUMMARIZE instruction line, following the existing CLEAN_UP or CUSTOM case.
  </action>
  <verify>
    <automated>npm --prefix frontend test -- --run && cd frontend && npx tsc -b</automated>
  </verify>
  <done>Vitest is green with at least the 535 baseline. tsc -b is clean. `grep -rn "reading only" frontend/src` returns nothing.</done>
</task>

<task type="auto">
  <name>Task 3: Docs - record D-14-13 and align the specs</name>
  <files>.planning/phases/14-ai-document-assist/14-CONTEXT.md, .planning/phases/14-ai-document-assist/14-UI-SPEC.md, .planning/phases/14-ai-document-assist/14-AI-SPEC.md, docs/document-assist-flow.md</files>
  <action>
    1. In 14-CONTEXT.md, under "### Revision semantics" right after D-14-03, add: `- **D-14-13 (2026-10-18, reverses D-14-03):** A complete **Summarize result can be edited and saved as an AI-assisted revision N+1**, the same way as Clean up. It records ai_mode SUMMARIZE and the configured summarize instruction. Like any AI revision it becomes current (D-14-01), so exports and the assistant index use the summary until the user regenerates or saves another revision. This is the user's accepted trade-off.` Also prefix D-14-03 with `~~superseded by D-14-13~~` or append "(superseded by D-14-13)". Keep the original text so the history stays readable.
    2. In 14-UI-SPEC.md: the line ~162 read-only toggle wording (Summarize is no longer read-only; only incomplete results show "Source"); line ~170, remove "summary-only note"; line ~82, remove "summary-only note" from the Muted row; the line ~187 state-table row (COMPLETE, Summarize: same as Clean up, with Edit, Accept, Copy, Download and Discard, per D-14-13); the line ~315 copy row (delete the note, or mark it removed by D-14-13); the line ~331 viewer banner (add "Summarized" to the mode alternatives); the line ~373 traceability row (D-14-03 superseded by D-14-13, Summarize saves like Clean up); and the history instruction line wording if listed (add "AI instruction: Summarize").
    3. In 14-AI-SPEC.md §6 Guardrails (starts ~line 476): replace the "Server-side SUMMARIZE rejection (D-14-03)" row with a guardrail note for D-14-13. It covers: SUMMARIZE saves are accepted; the server stores the configured instruction and never client text for non-CUSTOM modes; the same completeness (UI), 409-superseded and basis-copy rules apply; and the risk that a summary becomes current and replaces the full document in exports and the index is accepted by the user. The verify banner and fact-check warning are the mitigations. Fix the Discard row and the ~line 27 sentence of the §6 subsection, which say "Summarize results are never stored". Only Discard is never stored.
    4. In docs/document-assist-flow.md, retitle §7 "## 7. Saving (Clean up and Custom only)" to "## 7. Saving" and rewrite any text saying SUMMARIZE is rejected or that summaries aren't saved, so that SUMMARIZE maps to AiMode.SUMMARIZE with the configured instruction. Check the rest of the file for other "Summarize ... not saved" mentions (`grep -n -i "summar" docs/document-assist-flow.md`) and fix them. This file is currently untracked; this task adds it.
    Stage only the files listed in this plan by name, with no Co-Authored-By trailer. Do not stage the other untracked files (key.txt, *.sh, .vscode/, docs/authentication.md) or src/main/resources/application.yml.
  </action>
  <verify>
    <automated>grep -n "D-14-13" .planning/phases/14-ai-document-assist/14-CONTEXT.md && ! grep -n "Summaries are for reading only" .planning/phases/14-ai-document-assist/14-UI-SPEC.md | grep -v -i "removed\|superseded" && ! grep -n "SUMMARIZE rejection" .planning/phases/14-ai-document-assist/14-AI-SPEC.md && ! grep -n "Clean up and Custom only" docs/document-assist-flow.md</automated>
  </verify>
  <done>D-14-13 is recorded. The UI-SPEC, AI-SPEC §6 and document-assist-flow §7 describe Summarize as saveable, and none still claims summaries are rejected or read-only.</done>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| browser -> POST /api/assistant/documents/ai-revisions | The mode, instruction and markdown come from the client |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-q261018-01 | Tampering | saveAiRevision instruction for SUMMARIZE | mitigate | resolveInstruction returns config.instruction(SUMMARIZE, type) and ignores client customInstruction; the unit test passes a non-null customInstruction and asserts that the stored value is the configured one |
| T-q261018-02 | Tampering | document_snapshot.ai_mode | mitigate | The V13 named CHECK still restricts values to the three enum names |
| T-q261018-03 | Repudiation/Integrity | summary becomes current revision in exports/index | accept | This is the user decision D-14-13. The revision is append-only and badged AI-assisted, the earlier revisions stay in history, and regenerating restores the full document |
</threat_model>

<verification>
- `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -B -P '!frontend' clean test` is green (>= 1260)
- `npm --prefix frontend test -- --run` is green (>= 535) and `cd frontend && npx tsc -b` is clean
</verification>

<success_criteria>
A user runs Summarize on a generated document, edits the result, clicks Accept and gets revision N+1. That revision is badged AI-assisted, the instruction line reads "AI instruction: Summarize", the viewer banner reads "Summarized", and D-14-13 is documented everywhere D-14-03 was cited.
</success_criteria>

<output>
After completion, create `.planning/quick/261018-iii-allow-saving-summaries/261018-iii-SUMMARY.md`
</output>
