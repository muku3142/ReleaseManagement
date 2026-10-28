import { useRef, useState } from 'react';
import { useAssistantStatus } from '../api/assistant';
import { messageForReason } from '../api/reasonMessages';
import { ApiError } from '../api/client';
import { useReleases } from '../api/releases';
import { AssistantChat } from '../assistant/AssistantChat';
import { FreshnessLine } from '../assistant/FreshnessLine';
import { ScopeChip } from '../assistant/ScopeChip';
import { chatActions, useChatSession, type Scope } from '../assistant/chatStore';

const SESSION_KEY = 'page';

export function AssistantRoute() {
  const status = useAssistantStatus(true);
  const releases = useReleases('');
  const session = useChatSession(SESSION_KEY);
  const [notice, setNotice] = useState<string | null>(null);
  const pageRef = useRef<HTMLDivElement>(null);

  if (status.isPending) {
    return (
      <p role="status" className="flex items-center gap-2 text-sm text-gray-600">
        <span aria-hidden="true" className="h-4 w-4 animate-spin rounded-full border-2 border-gray-300 border-t-gray-600" />
        Loading assistant…
      </p>
    );
  }

  if (status.isError) {
    const reason = status.error instanceof ApiError ? messageForReason(status.error.problem) : 'The server could not be reached';
    return (
      <div role="alert" className="mx-auto max-w-3xl rounded border border-red-600 bg-red-50 p-4 text-sm text-red-700">
        {`Couldn't load the assistant — ${reason}. `}
        <button type="button" onClick={() => void status.refetch()} className="font-semibold underline">
          Try again
        </button>
      </div>
    );
  }

  if (!status.data.enabled) return null;

  const { starters } = status.data;
  const scope = session.scope;
  const sortedReleases = [...(releases.data?.releases ?? [])].sort((a, b) => b.createdAt.localeCompare(a.createdAt));

  function changeScope(next: Scope) {
    setNotice(chatActions.setScope(SESSION_KEY, next));
  }

  function onNewChat() {
    chatActions.newChat(SESSION_KEY);
    setNotice('Chat cleared.');
    pageRef.current?.querySelector('textarea')?.focus();
  }

  function onPick(releaseId: string) {
    const release = sortedReleases.find((r) => r.id === releaseId);
    if (release) changeScope({ releaseId, label: `${release.name} ${release.version}` });
  }

  const scopeRow = scope ? (
    <ScopeChip label={scope.label} onRemove={() => changeScope(null)} />
  ) : (
    <select
      aria-label="Scope to a release"
      value=""
      onChange={(e) => onPick(e.target.value)}
      className="rounded border border-gray-300 bg-white px-2 py-1 text-sm text-gray-700 focus-visible:ring-2 focus-visible:ring-blue-600"
    >
      <option value="">All releases</option>
      {sortedReleases.map((r) => (
        <option key={r.id} value={r.id}>{`${r.name} ${r.version}`}</option>
      ))}
    </select>
  );

  return (
    <div ref={pageRef} className="mx-auto flex max-w-3xl flex-col">
      <div className="flex items-center justify-between">
        <h2 className="text-xl font-semibold text-gray-900">Assistant</h2>
        <button
          type="button"
          disabled={session.turns.length === 0}
          onClick={onNewChat}
          className="rounded border border-gray-300 px-3 py-1 text-sm text-gray-700 disabled:opacity-50 focus-visible:ring-2 focus-visible:ring-blue-600"
        >
          New chat
        </button>
      </div>
      <FreshnessLine enabled />
      {notice && session.turns.length === 0 && <p className="mt-4 text-sm text-gray-600">{notice}</p>}
      <div role="status" aria-live="polite" className="sr-only">
        {notice}
      </div>
      <AssistantChat
        sessionKey={SESSION_KEY}
        starters={scope ? starters.release : starters.unscoped}
        scopeRow={scopeRow}
      />
    </div>
  );
}
