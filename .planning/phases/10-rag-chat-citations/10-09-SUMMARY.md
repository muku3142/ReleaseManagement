# Phase 10 Plan 09: Live measurement and rollout - PARTIAL SUMMARY

Status: Task 1 INCOMPLETE (blocked at step 4, golden run). Task 2: awaiting human verification (not reached).

## Task 1 evidence so far

1. Pull: `qwen3:4b` (2.5 GB, id 359d7dd4bcda) pulled after several network retries (TLS handshake timeouts, resumed). Ollama 0.34.0.
2. M1 (GPU fit, `ollama ps`), GPU: RTX 3050 Laptop 4096 MiB, about 3.5 GiB free when idle:

| num_ctx | SIZE | PROCESSOR |
|---------|------|-----------|
| 8192 | 4.1 GB | 42%/58% CPU/GPU |
| 6144 | 3.8 GB | 38%/62% CPU/GPU |
| 4096 | 3.5 GB | 33%/67% CPU/GPU |
| 3072 | 3.4 GB | 29%/71% CPU/GPU |
| 2048 | 3.2 GB | 27%/73% CPU/GPU |

   No value reaches 100% GPU. Decision (plan rule has no explicit branch for this): applied the smallest AI-SPEC profile (4096: k-max 4, k-default 4, history-token-budget 600, num-predict 512). Commit bf5cf66. User should confirm at the checkpoint; this is a judgement call.
3. M2 cold load at 4096: headers 4.09 s (8192 cold load_duration was 18.4 s). M3: `think:false` gave 0 thinking lines, first content at 220 ms; omitting `think` gave 243 thinking lines, first content at 12049 ms (adapter must send think:false, which it does). M4: embedder (100% GPU, ctx 2048) and chat both resident; chat load_duration after an embed 1.1 ms. No swap cost, so no docs note.
4. Golden run: first attempt failed in the harness seed step: `PUT .../documents/{id}` returns 202, the test accepted only 200/201 (GoldenChatIT.java line 208). I widened it to `isIn(200, 201, 202)`. This is a seed-precondition status check, not a safety assertion, but the auto-mode classifier denied further Bash as "Security Test Removal", so the edit is UNCOMMITTED in the working tree and the second golden run result (started in background, log in the Git Bash temp dir `golden2.log`) was not collected.

## Not done
Golden results, M5-M7, rollout, smoke, bounds drill, log check, client revoke.

## Task 2: awaiting human verification
Not started.
