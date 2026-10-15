package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.assistant.ChatOutcome;
import com.evision.releasemgmt.domain.assistant.ChatRequest;

public interface RagChatPort {

    // One chat request; service errors come back as ChatOutcome.Failed, never thrown.
    ChatOutcome chat(ChatRequest request, ChatListener listener);

    interface ChatListener {

        ChatListener NONE = new ChatListener() {
            @Override
            public void token(String text) {
            }

            @Override
            public void roundDiscarded() {
            }
        };

        void token(String text);

        void roundDiscarded();
    }
}
