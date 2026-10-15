package com.evision.ragservice.ports;

import com.evision.ragservice.domain.Cancellation;
import com.evision.ragservice.domain.ChatMessage;
import com.evision.ragservice.domain.Completion;
import com.evision.ragservice.domain.GenerationOptions;
import java.util.List;
import java.util.function.Consumer;

public interface ChatPort {

    /** Throws OllamaUnavailableException when the backend is unreachable and ModelNotAvailableException when the chat model is missing. */
    void assertReady();

    /** Throws ModelNotAvailableException when the chat model cannot call tools; call after assertReady. */
    void assertToolsReady();

    /** Streams tokens to onToken; returns Completion.cancelled(reason) once cancel fires; throws OllamaUnavailableException, ModelNotAvailableException or GenerationFailedException. */
    Completion stream(List<ChatMessage> messages, GenerationOptions options, Consumer<String> onToken, Cancellation cancel);
}
