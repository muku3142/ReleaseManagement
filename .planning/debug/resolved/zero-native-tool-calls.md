---
status: resolved
trigger: "GoldenToolsIT produced 0 native tool_calls on qwen2.5-coder:7b and qwen3:4b-instruct-2507-q4_K_M (most cases REFUSED after 4-36 eval tokens)"
created: 2026-10-18
updated: 2026-10-18
goal: find_and_fix
---

## Current Focus

hypothesis: CONFIRMED (prompt-level). With SOURCES in the user message plus CHAT_SYSTEM rules 1-7, qwen3-instruct 4B answers NO_ANSWER (4 tokens) or prose instead of emitting a tool call. Plumbing (adapter, ChatService, tool shape, stream parsing) is correct.
test: direct curl to Ollama /api/chat with the adapter's body vs ablations
expecting: n/a
next_action: report to caller; fix is a prompt/design change, needs a decision

## Symptoms

expected: native message.tool_calls on tool-needing questions (TC01..TC06, TC11)
actual: 0 tool_calls on both models; qwen3 instruct returns NO_ANSWER after 4 eval tokens
errors: none (outcome REFUSED, layer MODEL)
reproduction: -Pgolden -Dtest=GoldenToolsIT
started: first live run of Phase 11

## Eliminated

- hypothesis: adapter omits or mis-shapes tools (H1)
  evidence: OllamaChatAdapter.stream builds tools as [{type:function,function:{name,description,parameters}}] (lines 105-110); the same shape in curl yields tool_calls (c_minimal, j_nosources).
- hypothesis: score gate / GATE refusal fires (H2)
  evidence: ChatService line 219 skips the gate when withTools; refusal layer is MODEL (guard.refused after a completed generation), eval_count 4 = the literal NO_ANSWER.
- hypothesis: tool_calls parsed from wrong place in the NDJSON (H4)
  evidence: stream:true curl shows tool_calls inside message of the done:true chunk; adapter handles tool_calls before the done check (lines 173-179).
- hypothesis: GoldenToolsIT reads calls wrongly (H5)
  evidence: with a model that does emit calls (curl) the wire shape matches what the adapter parses; failing runs have eval_count 4 and no tool call at all.
- hypothesis: think:true is the cause
  evidence: think true, false, and absent all give identical NO_ANSWER (instruct build has empty thinking).

## Evidence

- Adapter-equivalent body (system = CHAT_SYSTEM + TOOL_RULES, user = SOURCES header + SRC block + QUESTION, tools = get_release_status, num_ctx 4096): NO_ANSWER, eval 4, prompt 728 tokens. With all 4 tools: same.
- Same without every line containing NO_ANSWER: prose "not specified in the sources [1]", no call.
- Minimal tools-only request (no system, question only): tool_calls get_release_status {version:4.3.0}, eval 25.
- Adapter system prompt but user message = QUESTION only (no SOURCES header): tool call. All 10 cases without SOURCES: 7/10 correct calls (TC01-TC06 ok; TC07 correctly none).
- User message "SOURCES:\nQUESTION..." calls; "SOURCES: none\nQUESTION..." or "SOURCES (untrusted reference data...)\nQUESTION..." refuses. Knife-edge.
- Rewording rule 6 / rule 15 / rule 8 (imperative), tool rules first, tool-only system, sources moved to system or second user message or after the question, trailing hints (T1, T3, T4, H1, H2): all still 0 calls on realistic sources.
- Trailing "You must call a tool." after QUESTION: TC01 and TC03 call, TC02/TC04-TC06/TC11 still refuse, and TC09 (expected no call) calls. Not a fix.
- qwen2.5-coder:7b reports capability tools but prints {"name":...} as text (TC01, TC07 with sources); never a native tool_calls. NO_ANSWER elsewhere.

## Resolution

root_cause: The retrieval-QA frame (CHAT_SYSTEM "answer using only SOURCES", the NO_ANSWER sentinel, a SOURCES block in the first user turn) dominates the 4B model; TOOL_RULES rule 8 sits at the tail of the system prompt and loses. Ollama and the adapter deliver the tools correctly (the model calls tools when no SOURCES frame is present).
fix: D-11-13. Round 1 of a tool request is a routing request (Prompts.TOOL_ROUTING, rag.prompts.tool-routing, p11-v2): routing system prompt, history, question, tools, no SOURCES and no NO_ANSWER rule. Calls are validated and emitted as before; text is discarded (never streamed) and the normal answer request runs. Later rounds are unchanged; the routing request is not a tool round.
verification: Hermetic suite 360/0 failures. Live qwen3:4b-instruct-2507-q4_K_M GoldenToolsIT: 30 native tool calls (was 0), overall 0.70 (was ~0.10 to 0.30), TC01-TC03, TC05, TC06, TC09, TC11 3/3; TC04, TC04b (round 2 refuses after a tool error or empty result) and TC07 (answers instead of asking which release) 0/3; TC08/TC10 pass. GoldenChatIT on both models: G06-G10 3/3, unchanged from baseline (T03 0/3 is the pre-existing failure). qwen2.5-coder:7b unchanged: 0 native calls.
files_changed: [rag-service/src/main/java/com/evision/ragservice/application/ChatService.java, PromptBuilder.java, PromptRules.java, Prompts.java, rag-service/src/main/resources/application.yml, ChatServiceTest, PromptBuilderTest, PromptRulesTest, LogHygieneIT]
