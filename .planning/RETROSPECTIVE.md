# Retrospective

## Milestone: v1.1 — Release Assistant

**Shipped:** 2026-10-20 · **Phases:** 6 (9-14) · **Plans:** 70

### What Was Built
A standalone RAG service (index, cited SSE chat, tool-call protocol) and its first client: release data push, an assistant screen and AI document assist.

### What Worked
- Service-first ordering: ReleaseManagement integration (12-14) ran mostly in parallel once 9-11 settled the contract.
- Debugging the zero-tool-call problem as a prompt-frame issue (D-11-13 routing split) instead of swapping models blindly.
- Security review inside execution caught a real SSRF in PDF export (14-02).

### What Was Inefficient
- The model question (thinking vs instruct builds, a 4 GB GPU) blocked Phases 10-12 for days.
- Gate and live-check plans ended in human steps, and their scripted evidence (live check, gate reports) was never produced before close.
- Worktree executors started from stale bases and had to self-reset every time.

### Key Lessons
- Put the measured gate run *before* the human approval in a plan, so closing it cannot skip the evidence.
- Test configuration can shadow the main one (GoldenChatIT ran on the test-yml model); assert the model in the report.

## Milestone: v1.0 — MVP

**Shipped:** 2026-10-20 · **Phases:** 12 (1-8, with 5.1-5.4) · **Plans:** 89

### What Was Built
Jira-sourced release assembly, logic-less user templates, immutable PDF/DOCX documents, ADO enrichment, the promotion ladder with audit trail, and the insights dashboard.

### What Worked
- Decimal phases for problems found in use (5.1-5.4) kept fixes scoped and recorded.
- Capturing a real tenant (spike-capture) before planning Phase 6 surfaced the 302-on-bad-PAT and status-id collisions early.

### What Was Inefficient
- Seam defects passed hundreds of green tests (HTML stored as Markdown, unregistered viewer route) because each side was tested against its own fixture.
- Human checks piled up across eight phases and were finally closed on the user's word, not observation.
- `verifier` and `plan_check` were off for the whole milestone.

### Key Lessons
- Add one test that feeds a consumer what the producer actually stores.
- Close a phase's human check while its context is fresh; batching them for later loses the observations.

## Cross-Milestone Trends

| Milestone | Phases | Plans | Days | Human checks closed unobserved |
|-----------|--------|-------|------|--------------------------------|
| v1.0 | 12 | 89 | 86 | 8 |
| v1.1 | 6 | 70 | 8 | 5 |
