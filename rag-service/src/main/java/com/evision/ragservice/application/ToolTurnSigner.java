package com.evision.ragservice.application;

import com.evision.ragservice.domain.ToolCall;
import com.evision.ragservice.domain.ToolTurn;
import com.evision.ragservice.domain.ToolTurnRejectedException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public final class ToolTurnSigner {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int CANONICAL_VERSION = 1;

    private final SecretKeySpec key;
    private final ObjectMapper mapper;

    public ToolTurnSigner(String secret, ObjectMapper mapper) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), ALGORITHM);
        this.mapper = mapper;
    }

    public String sign(UUID clientId, String question, String questionId, int round, String previousSignature,
                       List<ToolCall> calls) {
        Map<String, Object> payload = new TreeMap<>();
        payload.put("v", CANONICAL_VERSION);
        payload.put("client", clientId.toString());
        payload.put("q", sha256Hex(question));
        payload.put("qid", questionId);
        payload.put("round", round);
        payload.put("prev", previousSignature == null ? "" : previousSignature);
        List<Object> canonicalCalls = new ArrayList<>(calls.size());
        for (ToolCall call : calls) {
            Map<String, Object> entry = new TreeMap<>();
            entry.put("id", call.id());
            entry.put("name", call.name());
            entry.put("arguments", canonical(call.arguments()));
            canonicalCalls.add(entry);
        }
        payload.put("calls", canonicalCalls);
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] tag = mac.doFinal(mapper.writeValueAsString(payload).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(tag);
        } catch (GeneralSecurityException | JacksonException e) {
            throw new IllegalStateException("tool turn signing failed");
        }
    }

    public void verifyChain(UUID clientId, String question, List<ToolTurn> turns) {
        for (int i = 0; i < turns.size(); i++) {
            ToolTurn turn = turns.get(i);
            String questionId = turn.questionId();
            boolean wellFormed = turn.round() == i + 1
                    && questionId != null && !questionId.isBlank()
                    && questionId.equals(turns.get(0).questionId())
                    && turn.signature() != null
                    && turn.calls() != null;
            if (!wellFormed || !matches(clientId, question, turns, i)) {
                throw new ToolTurnRejectedException("tool turn " + (i + 1) + " failed verification");
            }
        }
    }

    static Object canonical(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sorted = new TreeMap<>();
            map.forEach((k, v) -> sorted.put(String.valueOf(k), canonical(v)));
            return sorted;
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            list.forEach(element -> result.add(canonical(element)));
            return result;
        }
        if (value instanceof Number number) {
            try {
                return new BigDecimal(number.toString()).stripTrailingZeros();
            } catch (NumberFormatException e) {
                throw new ToolTurnRejectedException("tool turn contains an invalid number");
            }
        }
        return value;
    }

    private boolean matches(UUID clientId, String question, List<ToolTurn> turns, int index) {
        ToolTurn turn = turns.get(index);
        String previous = index == 0 ? "" : turns.get(index - 1).signature();
        String expected = sign(clientId, question, turn.questionId(), turn.round(), previous, turn.calls());
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), turn.signature().getBytes(StandardCharsets.US_ASCII));
    }

    private static String sha256Hex(String text) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 unavailable");
        }
    }
}
