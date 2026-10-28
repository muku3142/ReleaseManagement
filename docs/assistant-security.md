# Assistant threat model (injection and related)

Scope: the assistant screen, the chat and transform paths to rag-service, and the quality gate. Deployment and
accepted chat risks of the service itself are in `docs/docker.md` (rag-service). The measurement procedure is in
`docs/quality-gate.md`.

## Trust boundaries

Browser to app (authenticated cookie) | app to rag-service (client key) | rag-service to Ollama |
untrusted text (tickets, comments, documents, manual items, history, tool results) into the prompt.

## Entries

| Threat | Mitigation | Residual |
|--------|------------|----------|
| Injection through browser-borne history | Roles limited to user and assistant, turns and length bounded, history fenced as data by rag-service | A small model can still be steered; answers are advisory text, nothing executes |
| Injection through ticket, comment, document or manual-item text | Retrieved text is fenced as data; answers are plain text | Same; measured by the gate injection cases with planted canaries |
| Injection through tool-result text | Results are fenced; tool summaries are size-capped; tool calls are signed so the browser cannot forge them | Same; measured by golden TC06 |
| Prompt-config edits weakening the rules | Data fences are fixed in code, rule text is validated at startup, its hash is returned in `done` | A hash change requires a gate and golden rerun |
| Release-scope bypass through tools | The scoped release is forced server-side into tool arguments; gate scope-leak cases check sources and tool arguments | None known |
| XSS from answers | Answers render as plain text; links are validated internal routes | None |
| CSRF on chat | The access cookie is `SameSite=Strict` (locked decision); there is no token-based CSRF layer | Relies on browser SameSite support |
| Key exposure | The client key lives in server-side environment only and never reaches the browser | Operator handling of the key |
| Sensitive data in logs | Logs carry metadata only; a sentinel test asserts questions, tokens and keys are absent | None |
| Transform injection (T03) | Document text is fenced | Accepted: the dev model fails golden T03 0/3 (SAFE-02); a production model must pass T03 3/3 before v1.1 ships |
| Gate harness damaging real data | Own scratch rag client, throwaway database that must be empty, teardown deletes and revokes | `teardown: INCOMPLETE` needs manual cleanup |
