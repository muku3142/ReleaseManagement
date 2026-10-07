# Phase 8 (and Phase 6) Discussion Log — 2026-10-07

Human reference only; downstream agents read the CONTEXT.md files. Phase 6 was pulled into this session
at the user's request ("this will affect the insights dashboard"). Four research agents ran in parallel
(ADO enrichment timing, ADO credential model, Phase 6 and Phase 8 codebase assumptions).

| Area | Question | Chosen |
|---|---|---|
| P8 Metrics | When is a release "in Production"? | First successful Production promotion |
| P8 Metrics | Failed promotions | Visible, never advance stage, release flagged |
| P8 Metrics | Durations | Per release + median of visible set |
| P8 Metrics | ADO coverage | Jira-backed denominator; linked / no link / failed separate |
| P8 Screen | Structure | Summary strip + release matrix + history panel |
| P8 Screen | Charts | Numbers + inline bars only |
| P8 Screen | Landing | Dashboard is the home route |
| P8 Releases | Default set | In-flight + shipped in last 90 days, toggle for all |
| P8 History | Placement | Dashboard section |
| P8 History | Default filter | All environments, last 30 days, failures marked |
| P7 (via P8) | Promote unfrozen? | Only frozen releases |
| P8 Todo | items_changed_at no-op stamping | Folded into Phase 8 |
| P6 Timing | Enrichment model | Stored per item, refreshed, frozen with release |
| P6 Timing | Refresh coupling | Refresh enriches; ADO never blocks it |
| P6 Timing | Freeze with failed/unattempted items | Warn + offer final enrichment |
| P6 Timing | Partial ADO detail | LINKED, flagged incomplete |
| P6 Display | Release page | Count chips expanding to list |
| P6 Display | Failed enrichment in documents | Omitted silently |
| INSIGHT-03 | User request | Commits-to-done, issues raised per commit, and required commits to reach Done |
| INSIGHT-03 | "Issues raised from each commit" | Bugs traced to the work + rework after Done (PR review findings not chosen) |
| INSIGHT-03 | Bug tracing | Jira issue link (caused-by) |
| INSIGHT-03 | Placement | New INSIGHT-03 in Phase 8, data captured in Phase 6 |
| INSIGHT-03 | Done moment | First: resolution date. **Revised** after the user asked to separate reopen-after-Done (testing) from commits-before-Done (development): scoped, cached `/changelog` |
| INSIGHT-03 | Done rule | Jira status category Done |
| INSIGHT-03 | Commit split | Development (before first Done) vs post-Done rework |

Not discussed by the user and recorded as Claude's discretion, from research: the shared org-scoped ADO
PAT with a hand-entered expiry, a 14-day warning banner, and any signed-in user may set it.
