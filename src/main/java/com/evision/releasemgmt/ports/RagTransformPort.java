package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.assistant.TransformOutcome;

public interface RagTransformPort {

    // Service errors come back as TransformOutcome, never thrown; exceptions thrown by the listener propagate.
    TransformOutcome transform(String text, String instruction, RagChatPort.ChatListener listener);
}
