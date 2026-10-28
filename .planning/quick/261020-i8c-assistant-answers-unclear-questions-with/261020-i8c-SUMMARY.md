---
phase: quick-261020-i8c
status: complete
completed: 2026-10-20
---

# Quick 261020-i8c: Clarifications and tool errors instead of the generic refusal Summary

The assistant no longer answers every unclear question with "I can't answer that from the data I have access to."

## What changed
- **rag-service (0049d80, 88ba3c2):** when the answer round refuses or is empty, the routing round's own clarification is streamed instead (`outcome=CLARIFIED`, `refusal=false`, empty RETRIEVED citations). Routing text that is NONE, NO_ANSWER, blank, or a tool call printed as JSON or a code fence still refuses.
- **ReleaseRef (1c52d17):** a no-match error now ends with "; known releases: Name (version), ..." (up to 5).
- **AssistantService (5da6972):** a refused final answer after a tool argument error becomes `app.assistant.prompts.tool-error-answer` ("I couldn't look that up: {error}."), streamed as discard + token. Only `ToolArgumentException` messages are shown; internal failures still refuse.
- **compose.app.yaml (0bcab23):** `RAG_CHAT_MODEL` default corrected from qwen2.5-coder:7b to qwen3:4b-instruct-2507-q4_K_M; c4f4174 had missed this override.

## Verification
- rag-service suite 367/367 (executor) and ChatServiceTest 54/54 after the JSON guard (on main).
- App: the 10 affected classes, 101 tests, all pass except 2 in `AssistantPropertiesTest`, which fail only because of the user's uncommitted edits to the document-assist instructions in both ymls (the two files differ, and the new summarize text lacks "Markdown summary").
- Live (rebuilt rag-service and app): "how many tickets are covered in Relase. ?" now returns "…Could you clarify which release you are referring to?"; "how many tickets are in Release 4.3?" calls tickets {release:"4.3"} and answers 3.
- Fix B (tool-error answer) is unit-tested only; it needs a signed-in UI session to see live.

## Deviations
- The executor worktree started on the stale base 968b5e3 and skipped the reset; its three commits were code-only, so they were cherry-picked onto main instead of merged.
- The live rebuild exposed the compose model override and text-printed tool calls leaking through the new fallback; both fixed in this task.
