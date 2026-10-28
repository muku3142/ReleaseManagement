import { useEffect, useRef, type KeyboardEvent } from 'react';
import { AssistantChat } from './AssistantChat';
import { FreshnessLine } from './FreshnessLine';
import { ScopeChip } from './ScopeChip';
import { chatActions, useChatSession } from './chatStore';

// Non-modal on purpose (D-13-01): the release page underneath stays visible and interactive, and a
// stray click must never close the drawer because that would stop an answer the user is reading.
export function ReleaseAssistantDrawer(props: {
  releaseId: string;
  label: string;
  starters: string[];
  onClose: () => void;
}) {
  const { releaseId, label, starters, onClose } = props;
  const sessionKey = `drawer:${releaseId}`;
  const session = useChatSession(sessionKey);
  const bodyRef = useRef<HTMLDivElement>(null);
  const empty = session.turns.length === 0;

  useEffect(() => {
    chatActions.setScope(sessionKey, { releaseId, label });
  }, [sessionKey, releaseId, label]);

  useEffect(() => {
    const body = bodyRef.current;
    if (!body) return;
    const starter = [...body.querySelectorAll('button')].find((b) => !b.closest('form'));
    (empty && starter ? starter : body.querySelector('textarea'))?.focus();
    // Focus once per opening of a release's drawer, not on every turn.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sessionKey]);

  function close() {
    chatActions.stop(sessionKey);
    onClose();
  }

  // Capture phase: the composer's own Escape handler stops the stream first, so the in-flight state
  // has to be read before it runs to tell "stop" from "close".
  function onKeyDownCapture(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Escape') return;
    const inComposer = (event.target as HTMLElement).closest('form') !== null;
    if (session.inFlight !== null && inComposer) return;
    close();
  }

  return (
    <div
      role="region"
      id="assistant-drawer"
      aria-label="Ask about this release"
      onKeyDownCapture={onKeyDownCapture}
      className="absolute right-0 top-0 z-10 h-full w-[380px] border-l border-gray-300 bg-white shadow-lg max-lg:inset-x-0 max-lg:bottom-0 max-lg:top-auto max-lg:h-auto max-lg:max-h-[70vh] max-lg:w-full max-lg:border-l-0 max-lg:border-t"
    >
      <div className="sticky top-0 flex max-h-screen flex-col overflow-y-auto max-lg:max-h-[70vh]">
        <div className="flex items-center gap-2 border-b border-gray-200 p-4">
          <h3 className="text-base font-semibold text-gray-900">Ask about this release</h3>
          <span className="grow" />
          <button
            type="button"
            onClick={() => chatActions.newChat(sessionKey)}
            className="text-sm text-gray-700 focus-visible:ring-2 focus-visible:ring-blue-600"
          >
            New chat
          </button>
          <button
            type="button"
            onClick={close}
            aria-label="Close assistant"
            className="text-gray-600 hover:text-gray-900 focus-visible:ring-2 focus-visible:ring-blue-600"
          >
            ×
          </button>
        </div>
        <div className="px-4 pt-2">
          <FreshnessLine enabled />
        </div>
        <div ref={bodyRef} className="px-4">
          <AssistantChat sessionKey={sessionKey} starters={starters} scopeRow={<ScopeChip label={label} />} />
        </div>
      </div>
    </div>
  );
}
