#!/usr/bin/env bash
# Usage: RAG_KEY=... RAG_COLLECTION=... rag-service/scripts/chat-smoke.sh [chat|transform] [question-or-instruction]
# RAG_URL defaults to http://localhost:8081. Needs bash, curl and node.
set -euo pipefail

mode="${1:-chat}"
text="${2:-}"
url="${RAG_URL:-http://localhost:8081}"
usage="usage: RAG_KEY=... RAG_COLLECTION=... $0 [chat|transform] [question-or-instruction]"

if [ -z "${RAG_KEY:-}" ]; then
  echo "$usage" >&2
  exit 2
fi

case "$mode" in
  chat)
    if [ -z "${RAG_COLLECTION:-}" ]; then
      echo "$usage" >&2
      exit 2
    fi
    body=$(node -e 'console.log(JSON.stringify({collections: [process.env.RAG_COLLECTION], question: process.argv[1]}))' \
      "${text:-What is in this release?}")
    required="event:status event:citations event:done"
    ;;
  transform)
    body=$(node -e 'console.log(JSON.stringify({text: "Release 4.2.0 ships PROJ-101 and PROJ-102.", instruction: process.argv[1]}))' \
      "${text:-Rewrite this as a bulleted list.}")
    required="event:status event:token event:done"
    ;;
  *)
    echo "$usage" >&2
    exit 2
    ;;
esac

out=$(mktemp)
trap 'rm -f "$out"' EXIT

curl -sS -N --max-time 150 -X POST "$url/v1/$mode" \
  -H "Authorization: Bearer $RAG_KEY" \
  -H 'Content-Type: application/json' \
  -H 'Accept: text/event-stream' \
  --data "$body" | tee "$out"

missing=""
for event in $required; do
  grep -q "^$event" "$out" || missing="$missing $event"
done
# A chat answer is either streamed tokens or a refusal, which still carries a done event
if [ "$mode" = chat ] && ! grep -q '^event:token' "$out" && ! grep -q '"refusal":true' "$out"; then
  missing="$missing event:token(or refusal)"
fi
if [ "$mode" = transform ] && grep -q '^event:citations' "$out"; then
  missing="$missing (unexpected event:citations)"
fi

if grep -q '^event:error' "$out"; then
  echo "SMOKE FAILED: error event: $(grep -A1 '^event:error' "$out" | grep '^data:')" >&2
  exit 1
fi
if [ -n "$missing" ]; then
  echo "SMOKE FAILED:$missing" >&2
  exit 1
fi
echo "SMOKE OK ($mode)"
