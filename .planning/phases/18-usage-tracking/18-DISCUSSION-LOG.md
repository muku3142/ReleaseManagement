# Phase 18: Usage Tracking - Discussion Log

**Date:** 2026-10-26. This log is for human reference only. Decisions live in 18-CONTEXT.md.

1. **Gray areas offered:** what gets counted, matrix shape/placement, retention/day zone, switch/visibility.
   - **User:** "Integrate with something like google analytics to get usage tracking real data for the application itself."
2. **Analytics type:** offered Umami self-hosted (recommended), GA4, pluggable, or keep in-app counters.
   - **User:** Umami. Installations: off by default, per install.
3. **Dashboard, identity, events and deploy:** asked.
   - **User rejected the question set to clarify:** "This will be related to the application hosted at the client side, so the integration should happen with something that sends analytics."
4. **Clarification:**
   - **User:** "connect to a third party provider like google analytics or umami which can send the analytical data from the client environment to the release management. This can be a little tricky."
5. **Whose usage:** Release Mgmt app, products at clients, or both.
   - **User:** products at clients. Phase 18 was redefined around this; the earlier Umami/self-hosted answers no longer apply.
6. **Providers:** GA4 + Umami, pluggable (recommended).
7. **Mapping:** per client + product (recommended).
8. **Display:** all four chosen: client detail panel, portfolio column, usage around a release, top pages/events.
9. **Sync:** daily sync stored in the DB (recommended).
10. **Credentials:** connections list (recommended).
11. **Backfill:** 90 days (recommended).
12. **Release window:** 14 days before vs after (recommended).
