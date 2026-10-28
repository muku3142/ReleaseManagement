---
phase: quick-261022-iv7
plan: 01
subsystem: document-assist
tags: [sse, assistant, clean-up, save]
key-files:
  modified:
    - src/main/java/com/evision/releasemgmt/application/assistant/AssistantTransformService.java
    - src/main/java/com/evision/releasemgmt/adapters/web/BrowserStreamListener.java
    - frontend/src/documentAssist/resultState.ts
    - frontend/src/components/AiResultCard.tsx
    - docs/document-assist-flow.md
metrics:
  completed: 2026-10-22
---

# Quick 261022-iv7: Save timed-out assist results

A sectioned Clean up/Custom run that stops after at least one finished section now gets a `remainder {keep, text}` SSE event (original text of the stopped section onward), so the browser buffer is the whole document and can be saved.

Worktree: D:/IdeaProjects/ReleaseManagement-live, branch live/phase15-chunking.

## Commits
- f8b466c fix(assist): server fill (`remainder` event, service, listener, tests)
- c0357e1 feat(assist): card applies remainder, filled stop is saveable
- 40843dc docs(assist): flow doc

## Decisions
- D-Q1 Explicit `remainder {keep, text}` event; browser does `slice(0, keep) + text`.
- D-Q2 Any stop cause after a finished section is filled (rag Failed/TooLarge, app stream deadline, other RuntimeException except ClientGone); filled + error DEADLINE -> partial 'deadline', else 'failed'.
- D-Q3 `length` stays blocked.
- D-Q4 No fill when no section finished.
- D-Q5 Summarize stays unsaveable (never sends remainder).
- D-Q6 User Stop stays unsaveable.
- D-Q7 Server save path unchanged.

## Verification
- Backend `AssistantTransform*,DocumentSections*`: AssistantTransformStreamIT 13, AssistantTransformServiceTest 32, DocumentSectionsTest 9; 0 failures. AssistantPropertiesTest was not run.
- Frontend: `tsc -b` clean, vitest 53 files / 593 tests passed.
- App container rebuilt from the live worktree and healthy.

## Deviations
- Rebuild: the first `up -d --build app` failed in the Maven package stage (log tail only showed the failure, cause not captured). A rerun of `build` with full logging succeeded without changes, then `up -d app`. Cause of the first failure is unknown.
- Frontend `node_modules` was missing; ran `npm ci`.
- Mid-task sed escaping mangled a test block in AiResultCard.test.tsx; it was reverted and rewritten with Edit before commit.
- Line endings: test files were temporarily converted to LF; git normalises on commit.
- Uncommitted compose.app.yaml and application.yml were left untouched.
