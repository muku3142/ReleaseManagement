# Phase 13 Discussion Log (2026-10-15)

Human reference only; CONTEXT.md is canonical. Every answer below was the recommended option unless noted.

## Screen placement
- Placement: options were nav page + release drawer / nav page only / global drawer. Chosen: nav page + release drawer.
- Scope display: options were removable chip / fixed per entry point / you decide. Chosen: removable chip.
- Scope change mid-chat: options were start a new chat / keep the turns. Chosen: start a new chat.
- Entry points: options were only those two / also the dashboard. Chosen: only those two.
- Assistant disabled: options were hide entirely / show disabled. Chosen: hide entirely.

## Answer streaming UX
- Tool phase: options were status line only / stream everything. Chosen: status line only.
- Citations: options were inline [n] + chip row / chip row only. Chosen: inline + chips.
- How-I-answered: options were collapsed per answer / always open / side panel. Chosen: collapsed.
- Rendering: options were plain text / minimal Markdown. Chosen: plain text.
- Stop: options were Stop button / no stop. Chosen: Stop button.
- Refusal: options were styled note / same as any answer. Chosen: styled note.

## Session lifetime
- Lifetime: options were until reload / sessionStorage / per screen visit. Chosen: until reload.
- Page vs drawer: options were separate / one shared chat. Chosen: separate.
- History: options were service trims / last N turns. Chosen: service trims.
- Starters: options were fixed list per scope / generated from data. Chosen: fixed list per scope.

## Quality gate and model
- Production host: options were split gate / phase blocks until prod / pull a tool-capable model. Chosen: split gate.
- Authoring: options were Claude drafts and the user reviews / the user supplies. Chosen: Claude drafts.
- Data: options were fixed seed / live dev data. Chosen: fixed seed.
- Pass bar: options were safety 3/3 + quality >= 80% / quality >= 90% / safety only. Chosen: >= 80%.

## Prompts as configuration (user request mid-discussion)
- "I will also need to add the prompts as variables in the application.properties."
- Which prompts (multi-select): the user chose all four: starter questions, document-assist instructions (Phase 14), rag-service system prompts, and the tool-use instruction.
- rag-service prompts: options were rule text only with fences kept in code / everything including fences. Chosen: rule text only.
- Tool-use text: options were Phase 13 picks it up / amend Phase 12 now. Chosen: Phase 13.
