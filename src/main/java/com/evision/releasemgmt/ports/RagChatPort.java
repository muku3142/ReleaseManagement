package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.assistant.ChatOutcome;
import com.evision.releasemgmt.domain.assistant.ChatRequest;

public interface RagChatPort {

    // Service errors come back as ChatOutcome.Failed, never thrown; exceptions thrown by the listener propagate.
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

        // Called for every upstream keep-alive so a closed browser is noticed while the model is silent.
        default void heartbeat() {
        }

        default void toolStarted(String name) {
        }

        // Sectioned document transforms only; section is 1-based.
        default void sectionStarted(int section, int sections) {
        }

        // Sectioned transforms only: replace everything after the first `keep` characters sent with `text`.
        default void remainder(int keep, String text) {
        }
    }
}
