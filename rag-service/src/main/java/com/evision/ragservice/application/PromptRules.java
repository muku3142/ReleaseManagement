package com.evision.ragservice.application;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("rag.prompts")
public record PromptRules(String chatSystem, String transformSystem, String toolRules, String toolRouting,
                          String finalRoundRule, String correction) {

    private static final int HASH_CHARS = 12;

    public PromptRules {
        chatSystem = orDefault("chat-system", chatSystem, Prompts.CHAT_SYSTEM);
        transformSystem = orDefault("transform-system", transformSystem, Prompts.TRANSFORM_SYSTEM);
        toolRules = orDefault("tool-rules", toolRules, Prompts.TOOL_RULES);
        toolRouting = orDefault("tool-routing", toolRouting, Prompts.TOOL_ROUTING);
        finalRoundRule = orDefault("final-round-rule", finalRoundRule, Prompts.FINAL_ROUND_RULE);
        correction = orDefault("correction", correction, Prompts.CORRECTION);
        // RefusalGuard matches the literal NO_ANSWER, so a chat prompt that never asks for it cannot refuse.
        if (!chatSystem.contains("NO_ANSWER")) {
            throw new IllegalStateException("rag.prompts.chat-system must contain NO_ANSWER");
        }
        if (correction.split("%s", -1).length != 2) {
            throw new IllegalStateException("rag.prompts.correction must contain exactly one %s");
        }
    }

    public static PromptRules defaults() {
        return new PromptRules(null, null, null, null, null, null);
    }

    public String hash() {
        String joined = String.join("\u0000", Prompts.PROMPT_VERSION, Prompts.TOOLS_PROMPT_VERSION, chatSystem,
                transformSystem, toolRules, toolRouting, finalRoundRule, correction);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(joined.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, HASH_CHARS);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String orDefault(String key, String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        if (value.isBlank()) {
            throw new IllegalStateException("rag.prompts." + key + " must not be blank");
        }
        return value;
    }
}
