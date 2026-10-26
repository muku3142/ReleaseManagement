# Phase 11: Tool-Call Protocol - Pattern Map

**Mapped:** 2026-10-14
**Files analyzed:** 33 (new or modified)
**Analogs found:** 33 / 33 (hexagonal rag-service, all roles have an in-repo analog; the HMAC signer has only a role-partial one)

Base package: `rag-service/src/main/java/com/evision/ragservice/` (abbreviated `main/` below); tests under `rag-service/src/test/java/com/evision/ragservice/` (`test/`).

## File Classification

| New/Modified File | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `main/config/ToolProperties.java` (new) | config | config | `config/ChatProperties.java` | exact |
| `main/config/GenerationConfig.java` (mod) | config | config | itself (line 8 `@EnableConfigurationProperties`) | exact |
| `main/domain/ToolDefinition, ToolCall, ToolResult, ToolTurn` (new) | model | transform | `domain/Citation.java`, `domain/ChatMessage.java` | exact |
| `main/domain/ToolSchema.java` (new) | utility | transform (validation) | `domain/MetadataRules.java` | exact |
| `main/domain/ToolTurnRejectedException, ToolResultTooLargeException` (new) | model | request-response | `domain/InvalidContentException.java`, `PayloadTooLargeException.java` | exact |
| `main/domain/ErrorReason, StreamEvents, ChatMessage, Completion, GenerationOptions, Citation` (mod) | model | event-driven | themselves | exact |
| `main/ports/ChatPort.java` (mod) | port | streaming | itself | exact |
| `main/application/ToolTurnSigner.java` (new) | service | transform (crypto) | `adapters/web/ApiKeyFilter.java` (secret handling only) | partial |
| `main/application/ToolPolicy.java` (new) | service | transform | `domain/MetadataRules.java` + `ChatService.prepare` | role-match |
| `main/application/Prompts.java`, `PromptBuilder.java` (mod) | service | transform | themselves | exact |
| `main/application/CitationBuilder.java` (mod) | service | transform | itself | exact |
| `main/application/ChatService.java` (mod) | service | streaming | itself | exact |
| `main/adapters/ollama/OllamaChatAdapter.java` (mod) | adapter | streaming | itself | exact |
| `main/adapters/web/dto/ChatRequest.java` + tool DTOs (mod/new) | DTO | request-response | `ChatRequest.java` (`Turn`, `toString`, `toCommand`) | exact |
| `main/adapters/web/ChatController.java`, `GlobalExceptionHandler.java` (mod) | controller | request-response | themselves | exact |
| `application.yml` x2, `compose.app.yaml`, `.env.example`, `docs/docker.md` (mod) | config | config | `RAG_ADMIN_KEY` / `rag.chat.*` entries | exact |
| `rag-service/openapi.json` (mod) | contract | - | `OpenApiContractIT` regeneration | exact |
| `test/ToolSchemaTest, ToolTurnSignerTest, ToolPolicyTest` (new) | test | unit | `domain/MetadataRulesTest.java` | exact |
| `test/FakeOllamaServer.java`, `application/FakeChatPort.java` (mod) | test helper | streaming | themselves | exact |
| `test/adapters/ollama/OllamaChatAdapterTest.java` (mod) | test | streaming | itself | exact |
| `test/application/ChatServiceTest, PromptBuilderTest, CitationBuilderTest` (mod) | test | unit | themselves | exact |
| `test/adapters/web/ChatApiIT, SseStreamingIT, LogHygieneIT` (mod); `ArchitectureBoundaryTest` (mod) | test | IT | themselves | exact |
| `test/golden/GoldenToolsIT.java` + `golden/tools-v1.json` (new) | test | live | `golden/GoldenChatIT.java`, `golden/chat-v1.json` | exact |

## Pattern Assignments

### `config/ToolProperties.java` (config)
**Analog:** `config/ChatProperties.java` lines 1-11, `GenerationConfig.java` lines 1-10.
```java
@ConfigurationProperties("rag.chat")
public record ChatProperties(String model, int numCtx, ... double charsPerToken) {}

@Configuration
@EnableConfigurationProperties(ChatProperties.class)
public class GenerationConfig {}
```
Copy: a flat record, `@ConfigurationProperties("rag.tools")`, add `ToolProperties.class` to the `@EnableConfigurationProperties` list. Do NOT add fields to `ChatProperties` (`OllamaChatAdapterTest.adapter` line ~49 and 3 other tests call its 20-arg constructor). Secret validation: copy `ApiKeyFilter` lines 38-51 (min 32 chars, `IllegalStateException` at construction, `getBytes(StandardCharsets.UTF_8)`), done in a compact constructor or in `ToolTurnSigner`'s constructor.

### `domain/ToolSchema.java` (utility, validation)
**Analog:** `domain/MetadataRules.java`.
Copy: final class, private ctor, `public static final int MAX_*` bounds, precompiled `Pattern`, throw `InvalidContentException` with a message naming the field, `rejectNul` reuse (lines 226-230):
```java
private static final Pattern KEY = Pattern.compile("^[A-Za-z0-9_.-]{1," + MAX_KEY_LENGTH + "}$");
private static void requireValidKey(String owner, String key) {
    if (key == null || !KEY.matcher(key).matches()) {
        throw new InvalidContentException(owner + " keys must match [A-Za-z0-9_.-] and be 1 to " + MAX_KEY_LENGTH + " characters");
    }
}
```
Tool names use `[A-Za-z0-9_]{1,64}`. `requireSupportedSchema` throws `InvalidContentException` (400 `INVALID_CONTENT`); `violations` returns a list of argument-name-only texts (never values, SAFE-03).

### `domain/Tool*Exception` (two new)
**Analog:** `PayloadTooLargeException` (413 via handler `PAYLOAD_TOO_LARGE`), `InvalidContentException`.
```java
public class InvalidContentException extends RuntimeException {
    public InvalidContentException(String message) { super(message); }
}
```
Plain `RuntimeException` subclasses with a fixed message (no caller content). `ToolTurnRejectedException` (400 `TOOL_TURN_INVALID`), `ToolResultTooLargeException` (413 `TOOL_RESULT_TOO_LARGE`).

### Additive domain edits
Keep old constructors so existing call sites compile (22 sites):
- `ChatMessage` (`domain/ChatMessage.java` line 3): `record ChatMessage(Role role, String content)`, `enum Role { SYSTEM, USER, ASSISTANT }`; add `TOOL`, optional `toolCalls`, `toolName`, plus a 2-arg constructor. Keep the content-free `toString` (lines 7-10).
- `Completion` (line 3): 6 components; add `toolCalls`, keep the 6-arg constructor and `Completion.cancelled`.
- `GenerationOptions(int numCtx, int numPredict, double temperature)`: add nullable `tools`, keep 3-arg constructor.
- `StreamEvents` (lines 7-11 constants, 33 `Done` record): add `TOOL_CALL = "tool_call"`, `ToolCallEvent(round, questionId, calls, signature)` (give it a content-free `toString` like `Token` lines 17-20), `Done.toolCalls()` factory.
- `ErrorReason` (line 4): append `TOOL_CALL_INVALID`.
- `Citation(int n, documentId, title, url, collection, metadata)`: add `kind`; `CitationBuilder.citation` (line 61) is the single constructor site.

### `ports/ChatPort.java`
Add `void assertToolsReady();` with the same one-line javadoc style as `assertReady` (lines 11-14). `stream` signature unchanged; tools travel in `GenerationOptions`.

### `application/ToolTurnSigner.java` (service, crypto)
**Analog:** `ApiKeyFilter` for secret length check and UTF-8 key bytes (lines 38-51); no existing HMAC. Use RESEARCH Code Examples (injective canonical form: numbers as `BigDecimal.stripTrailingZeros`, not `"n:..."` strings; non-finite rejected). `javax.crypto.Mac` HmacSHA256, `MessageDigest.isEqual`. Wire as `@Component` in `application`, constructed from `ToolProperties`, same style as `GenerationGate` (`@Component`, properties in ctor, lines 9-17).

### `application/ToolPolicy.java` (service)
Pure class, no I/O; checks call count against `maxCalls`, tool names against supplied definitions, `ToolSchema.violations`. Style: `PromptBuilder` (final class, ctor takes values, no Spring annotations; `ChatService` constructs it at line 77) or `@Component` like `GenerationGate`.

### `application/Prompts.java`, `PromptBuilder.java`
**Analog:** themselves. Add, do not edit `CHAT_SYSTEM` (lines 7-17 of Prompts):
```java
public static final String PROMPT_VERSION = "p10-v1";
public static final String CHAT_SYSTEM = """ ... 7. Answer in plain text ... """;
```
New `TOOL_RULES`, `TOOLS_PROMPT_VERSION`. Fencing pattern to reuse for tool results (`PromptBuilder` lines 115-119):
```java
return "<<<SRC id=" + doc.n() + " title=\"" + cleanTitle(doc.title(), nonce) + "\" nonce=" + nonce + ">>>\n"
        + String.join("\n\n", passages) + "\n<<<END nonce=" + nonce + ">>>\n";
```
Budget pattern (lines 69-105): compute `fixed`, throw `InvalidContentException` if over, `available = numCtx - numPredict - fixed`, fill sources then history newest-first. `chatWithTools` mirrors this with order fixed, tool exchange, passages, history; over budget throws `ToolResultTooLargeException` (413). Forbidden tokens: `neutralise` (lines 49-61) loops to a fixpoint over `FORBIDDEN`; add a second array (`<tool_response>`, `</tool_response>`, `<tool_call>`, `</tool_call>`) used only by `chatWithTools`. The nonce `Supplier<String>` test ctor (line 36) is the hook for deterministic tests.

### `application/CitationBuilder.java`
**Analog:** itself. `build(String answer, List<SourceDocument> sent)` (lines 191-220): MARKER regex, `TreeSet` of marked ids, invented ids counted, fallback to RETRIEVED. Add an overload taking tool sources: numbering continues after passage ids; error results never produce a citation.

### `application/ChatService.java` (hot)
**Analog:** itself. Seams:
- `Command` (lines 52-53) gains `tools`, `toolTurns`; keep prior callers compiling or update the three test builders.
- `prepare` (lines 82-124): 404, then 400 checks in order, then `chat.assertReady()` (line 116), then `gate.tryAcquire` (118-121). Insert tool intake and turn-chain verification before `assertReady`, `chat.assertToolsReady()` right after it, so tamper gives 400 with zero Ollama requests and no gate slot.
- `run` (lines 139-216): gate skip at lines 153-156 (`if (hits.isEmpty() || hits.getFirst().score() < props.minScore())`) becomes conditional on no tools. Branch after `chat.stream` (line 171) on `completion.toolCalls()` non-empty: do not call `guard.finish()` (line 185). Gate release is in `finally` (line 212):
```java
} finally {
    watchdog.cancel(false);
    heartbeat.cancel(false);
    gate.release(plan.clientId());
    logRun(plan, stats, start);
    sink.complete();
}
```
`GenerationGate.release` is NOT idempotent (decrements each call, lines 33-45 of the gate), so use a local once-only wrapper and call it before sending `tool_call` and again, harmlessly, in `finally`.
- Error mapping `errorReason` (lines 268-276): add `case ToolCallInvalid...` to the switch.
- Log line `logRun` (lines 278-291): metadata only; add `round`, `questionId`, call count, call names matching supplied definitions.

### `adapters/ollama/OllamaChatAdapter.java`
**Analog:** itself.
- Request body records (lines 490-504): `ChatRequest(model, messages, stream, think, keep_alive, options)`, `Msg(role, content)` with content-free `toString` (SAFE-03, lines 498-503). Extend `Msg` with nullable `tool_calls`/`tool_name`, add `tools` to the request with `@JsonInclude(NON_NULL)` so the no-tools body stays byte-identical.
- `ChunkMsg(String content)` (lines 506-508) gains `tool_calls`; keep `@JsonIgnoreProperties(ignoreUnknown = true)` so `thinking` stays ignored. Accumulate calls across chunks in `readNdjson` (lines 404-440) with a hard cap; the `done` chunk carries none.
- `assertReady` (lines 360-374) already reads `show.capabilities()`; cache it there for `assertToolsReady` (503 `ModelNotAvailableException` when `tools` missing) and for `think:true` only when `thinking` is listed.
- Error mapping `statusFailure` (453-467) unchanged.

### `adapters/web/dto/ChatRequest.java`
**Analog:** itself. Bean Validation record, nested `Turn` with `@NotNull @Pattern`, content-free `toString` (lines 118-124), `toCommand()` (126-132). New nested `ToolDef`, `ToolTurnDto`, `ResultDto` records follow `Turn`: `@Valid` on list elements, `@Size` caps, a content-free `toString` on each carrying arguments or content. `source.url` pattern: reuse `PutDocumentRequest` `https?://\S{1,2040}`.

### `adapters/web/ChatController.java`, `GlobalExceptionHandler.java`
Controller: only the `EVENTS` text (lines 33-39) gains `tool_call {round, questionId, calls, signature}` and `done.finishReason tool_calls`. Handler pattern (lines 75-83):
```java
@ExceptionHandler(PayloadTooLargeException.class)
public ProblemDetail handlePayloadTooLarge(PayloadTooLargeException ex) {
    return problem(HttpStatus.PAYLOAD_TOO_LARGE, "Payload too large", ex.getMessage(), "PAYLOAD_TOO_LARGE");
}
```
Add two handlers: `TOOL_TURN_INVALID` (400), `TOOL_RESULT_TOO_LARGE` (413). `SseSink.send` (lines 154-157) is generic over event name and payload; no change needed.

### Config and ops files
**Analog:** `rag-service/src/main/resources/application.yml` lines 28 and 57-77, test yml `chat:` block (lines 53-65), `compose.app.yaml` line 80, `.env.example` line 48, `docs/docker.md` line 322.
```yaml
  admin-key: ${RAG_ADMIN_KEY}
  chat:
    model: "${RAG_CHAT_MODEL:qwen3:4b}"
    deadline: ${RAG_CHAT_DEADLINE:PT120S}
```
```yaml
      RAG_ADMIN_KEY: ${RAG_ADMIN_KEY:?set RAG_ADMIN_KEY (at least 32 chars), e.g. openssl rand -base64 32}
```
Add `rag.tools.signing-secret: ${RAG_TOOL_SIGNING_SECRET}` plus `max-rounds`, `max-calls`, `max-tools`, `definitions-max-chars`, `result-max-chars`, `num-predict`. Main yml uses `${ENV}` with no default for the secret; test yml uses a fixed 32+ char literal (as it does for the admin key). Compose uses `:?` and `.env.example` gets an empty `RAG_TOOL_SIGNING_SECRET=` with an `openssl rand -base64 32` hint, mirroring `RAG_ADMIN_KEY` line 7 of `.env.example`.

### `FakeOllamaServer` (test helper)
**Analog:** itself. Today: single `volatile List<String> chatLines` (line 34), `lastChatBody` (39), `reset()` (70), `chat(lines, delay)` (88), `handleChat` writes `chatLines` (lines 166-190), static line builders `tokenLine`, `doneLine`, `answer` (lines 119-137):
```java
public static String doneLine(String doneReason, int promptEvalCount, int evalCount) {
    return "{\"model\":\"fake\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\"" + ...
```
Add `chatScript(List<List<String>>)` (request i uses `min(i, last)`), `chatBodies()` list, `toolCallLine(id, name, argsJson)`, and clear both in `reset()`. Keep `lastChatBody` working.

### `FakeChatPort` (test helper)
**Analog:** itself (lines 15-67): `volatile` scripted fields, `streamCalls`, `lastOptions`, `readyCalls`. Add `assertToolsReady` counter/failure, and a per-call `List<Completion>` script so `ChatServiceTest` can return a tool-call completion then a final one.

### Unit tests (`ToolSchemaTest`, `ToolTurnSignerTest`, `ToolPolicyTest`)
**Analog:** `domain/MetadataRulesTest.java`: JUnit 5 `@Test`, AssertJ `assertThatThrownBy(...).isInstanceOf(...)`, `assertThatCode(...).doesNotThrowAnyException()`. Signer tamper table: JUnit `@ParameterizedTest` (A5: confirm `junit-jupiter-params` resolves).

### `OllamaChatAdapterTest` (mod)
**Analog:** itself. WireMock `@WireMockTest`, helpers `adapter(baseUrl)` (builds `ChatProperties` positionally, line ~49: do not change that constructor), `stubChat(status, body)`, `stubShow(status, body)`, `lines(...)`; assertions via `verify(postRequestedFor(urlEqualTo("/api/chat")).withRequestBody(matchingJsonPath(...)))`. Byte-identical no-tools body: assert exact JSON body equality on the pre-change shape.

### `ArchitectureBoundaryTest` (mod)
**Analog:** itself lines 50-58 (`noClasses().that().resideOutsideOfPackages("..adapters.ollama..", "..config..").should().dependOnClassesThat().resideInAPackage("java.net.http..").allowEmptyShould(true).check(classes)`). Add one rule in the same style: nothing in `domain`/`application` depends on `java.net.URI`/`URL` resolution of app URLs for tools (source.url stays a string).

### `GoldenToolsIT` + `golden/tools-v1.json`
**Analog:** `golden/GoldenChatIT.java`: `@Tag("golden")` (line 55), `@Container @ServiceConnection PostgreSQLContainer` with `PgVectorImage.IMAGE`, `@DynamicPropertySource` setting `rag.ollama.base-url`, `@BeforeAll requireOllamaAndModels` with `assumeTrue` skip, `RUNS = 3`, `GOLDEN = Path.of("src/test/resources/golden")`, report under `target/`. Do not edit `GoldenChatIT`; copy its seeding into a separate class. Read ONE system property (`rag.chat.model`) for both service model and probe (GoldenChatIT reads `rag.golden.model` only for probe, line 62). Fixture format: `chat-v1.json` (`version`, `labeling`, `cases[]` with `id`, `category`, `safety`, `client`, `collections`, `history`, `filter`, `question`, `expect{refusal, citationDocs, mustContain, mustNotContain}`, `rubric`). Run: `JAVA_HOME=C:/Users/ucs/.jdks/jdk-25 mvn -f rag-service/pom.xml test -Pgolden -Dtest=GoldenToolsIT` (pom: `excludedGroups golden` line 27, profile `golden` line 129, surefire only).

## Shared Patterns

### SAFE-03 content-free `toString` (apply to every record carrying caller or model text)
**Source:** `domain/ChatMessage.java` lines 7-10, `ChatRequest.Turn` lines 111-116, `OllamaChatAdapter.Msg` lines 498-503, `StreamEvents.Token` lines 17-20.
```java
@Override
public String toString() {
    return "Msg[role=" + role + ", contentChars=" + (content == null ? 0 : content.length()) + "]";
}
```
Apply to: `ToolCall`, `ToolResult`, `ToolTurn`, `ToolCallEvent`, tool DTOs, adapter wire records. Never log arguments, results, source titles, signatures or the secret; extend `LogHygieneIT` canaries.

### Error contract
**Source:** `GlobalExceptionHandler` lines 75-83 (`problem(status, title, detail, reasonCode)`); pre-stream failures are problem+json, post-open failures are `error` events via `ChatService.run` catch block (lines 198-208) with `ErrorReason` enum. `TOOL_CALL_INVALID` is a stream `error`; `TOOL_TURN_INVALID` / `TOOL_RESULT_TOO_LARGE` are pre-stream problem+json.

### Hexagonal boundaries
**Source:** `ArchitectureBoundaryTest` lines 17-75: domain/ports free of framework imports and of adapters/config/application; application does not depend on adapters or the servlet layer; `java.net.http` and `RestClient` only in `adapters.ollama`/`config`. New tool classes in `domain`/`application` must satisfy these, which also backs RAG-08.

### Untrusted text fencing
**Source:** `PromptBuilder.neutralise` (lines 49-61) and `block` (115-119): nonce-fenced, forbidden control tokens removed to a fixpoint. Tool results and names get the same treatment plus the tool template tokens.

### Concurrency gate
**Source:** `GenerationGate` lines 20-45 (`tryAcquire`, non-idempotent `release`). Tool-call branch must release exactly once before emitting `tool_call`/`done`.

## No Analog Found

| File | Role | Reason |
|---|---|---|
| HMAC canonical-JSON signing in `ToolTurnSigner` | service | No MAC or canonical-form code exists. Use RESEARCH "Code Examples" and Pitfall 2 (injective numbers). Only secret-length handling has an analog (`ApiKeyFilter` lines 38-51). |
| Scripted multi-request Ollama fake | test helper | `FakeOllamaServer` serves one script; extension described above. |
| Tool-call NDJSON accumulation | adapter | `readNdjson` handles content only; add `tool_calls` accumulation per RESEARCH wire notes. |

## Metadata

**Analog search scope:** `rag-service/src/main`, `rag-service/src/test`, compose, `.env.example`, `docs/docker.md`
**Files read:** ChatService, OllamaChatAdapter, PromptBuilder, Prompts, CitationBuilder, ChatController, ChatRequest, SseSink, MetadataRules, GenerationGate, ApiKeyFilter (excerpt), GlobalExceptionHandler (excerpt), domain records, FakeChatPort, FakeOllamaServer (excerpts), OllamaChatAdapterTest (head), GoldenChatIT (head), ArchitectureBoundaryTest (excerpt)
**Pattern extraction date:** 2026-10-14
