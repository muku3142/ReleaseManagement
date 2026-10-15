package com.evision.ragservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evision.ragservice.domain.ToolCall;
import com.evision.ragservice.domain.ToolTurn;
import com.evision.ragservice.domain.ToolTurnRejectedException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ToolTurnSignerTest {

    private static final String S1 = "secret-one-secret-one-secret-one-1234";
    private static final String S2 = "secret-two-secret-two-secret-two-5678";
    private static final UUID C1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String Q1 = "What is the status of 2.4.0?";
    private static final String QID = "5f1c6a52-0000-4000-8000-000000000001";

    private final ToolTurnSigner signer = new ToolTurnSigner(S1, JsonMapper.builder().build());

    private static List<ToolCall> callsA() {
        return List.of(new ToolCall("call_a", "get_release_status", Map.of("version", "2.4.0")));
    }

    private static List<ToolCall> callsB(Object limit) {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("version", "2.4.0");
        args.put("limit", limit);
        return List.of(new ToolCall("call_b", "get_deployments", args));
    }

    private static ToolTurn turn(int round, String qid, List<ToolCall> calls, String signature) {
        return new ToolTurn(round, qid, calls, signature, List.of());
    }

    private List<ToolTurn> chain(ToolTurnSigner s, List<ToolCall> second) {
        String sig1 = s.sign(C1, Q1, QID, 1, "", callsA());
        String sig2 = s.sign(C1, Q1, QID, 2, sig1, second);
        return List.of(turn(1, QID, callsA(), sig1), turn(2, QID, second, sig2));
    }

    @Test
    void acceptsAValidChainAndTheEmptyChain() {
        assertThatCode(() -> signer.verifyChain(C1, Q1, chain(signer, callsB(3)))).doesNotThrowAnyException();
        assertThatCode(() -> signer.verifyChain(C1, Q1, List.of())).doesNotThrowAnyException();
    }

    @Test
    void signatureIsStableUrlSafeBase64Of32Bytes() {
        String a = signer.sign(C1, Q1, QID, 1, "", callsA());
        assertThat(a).hasSize(43).matches("[A-Za-z0-9_-]{43}").isEqualTo(signer.sign(C1, Q1, QID, 1, "", callsA()));
    }

    @Test
    void rejectsEveryTamperedChain() {
        List<ToolTurn> good = chain(signer, callsB(3));
        ToolTurn t1 = good.get(0);
        ToolTurn t2 = good.get(1);
        Map<String, List<ToolTurn>> cases = new LinkedHashMap<>();
        cases.put("argument value changed", List.of(t1, turn(2, QID,
                List.of(new ToolCall("call_b", "get_deployments", Map.of("version", "9.9.9", "limit", 3))), t2.signature())));
        cases.put("argument added", List.of(t1, turn(2, QID,
                List.of(new ToolCall("call_b", "get_deployments", Map.of("version", "2.4.0", "limit", 3, "x", 1))),
                t2.signature())));
        cases.put("call id changed", List.of(t1, turn(2, QID,
                List.of(new ToolCall("call_z", "get_deployments", t2.calls().get(0).arguments())), t2.signature())));
        cases.put("call name changed", List.of(t1, turn(2, QID,
                List.of(new ToolCall("call_b", "other_tool", t2.calls().get(0).arguments())), t2.signature())));
        cases.put("round renumbered", List.of(t1, turn(3, QID, t2.calls(), t2.signature())));
        cases.put("first turn dropped", List.of(t2));
        cases.put("reordered", List.of(t2, t1));
        cases.put("other questionId on one turn", List.of(t1, turn(2, "other-qid", t2.calls(), t2.signature())));
        cases.put("empty signature", List.of(turn(1, QID, t1.calls(), "")));
        cases.put("invented signature", List.of(turn(1, QID, t1.calls(), "A".repeat(43))));
        cases.put("null signature", List.of(turn(1, QID, t1.calls(), null)));
        cases.put("blank questionId", List.of(turn(1, " ", t1.calls(), t1.signature())));
        cases.put("string n:1 for number 1", List.of(
                turn(1, QID, List.of(new ToolCall("c", "t", Map.of("v", "n:1"))),
                        signer.sign(C1, Q1, QID, 1, "", List.of(new ToolCall("c", "t", Map.of("v", 1)))))));
        cases.put("infinite number", List.of(
                turn(1, QID, List.of(new ToolCall("c", "t", Map.of("v", Double.POSITIVE_INFINITY))), t1.signature())));
        cases.put("NaN number", List.of(
                turn(1, QID, List.of(new ToolCall("c", "t", Map.of("v", Double.NaN))), t1.signature())));

        cases.forEach((name, turns) -> assertThatThrownBy(() -> signer.verifyChain(C1, Q1, turns))
                .as(name).isExactlyInstanceOf(ToolTurnRejectedException.class));
    }

    @Test
    void rejectsOtherClientOtherQuestionAndRotatedSecret() {
        List<ToolTurn> good = chain(signer, callsB(3));
        assertThatThrownBy(() -> signer.verifyChain(UUID.randomUUID(), Q1, good))
                .isExactlyInstanceOf(ToolTurnRejectedException.class);
        assertThatThrownBy(() -> signer.verifyChain(C1, "another question", good))
                .isExactlyInstanceOf(ToolTurnRejectedException.class);
        List<ToolTurn> signedWithS2 = chain(new ToolTurnSigner(S2, JsonMapper.builder().build()), callsB(3));
        assertThatThrownBy(() -> signer.verifyChain(C1, Q1, signedWithS2))
                .isExactlyInstanceOf(ToolTurnRejectedException.class);
    }

    @Test
    void numbersAndKeyOrderAreCanonical() {
        List<ToolTurn> signedWithInteger = chain(signer, callsB(3));
        String sig2 = signedWithInteger.get(1).signature();
        for (Object variant : List.of(3.0, 3L)) {
            List<ToolTurn> turns = List.of(signedWithInteger.get(0), turn(2, QID, callsB(variant), sig2));
            assertThatCode(() -> signer.verifyChain(C1, Q1, turns)).as(String.valueOf(variant)).doesNotThrowAnyException();
        }
        Map<String, Object> reordered = new LinkedHashMap<>();
        reordered.put("limit", 3);
        reordered.put("version", "2.4.0");
        List<ToolTurn> turns = List.of(signedWithInteger.get(0),
                turn(2, QID, List.of(new ToolCall("call_b", "get_deployments", reordered)), sig2));
        assertThatCode(() -> signer.verifyChain(C1, Q1, turns)).doesNotThrowAnyException();
    }

    @Test
    void messagesCarryNoContent() {
        List<ToolTurn> bad = List.of(turn(1, QID, callsA(), "A".repeat(43)));
        assertThatThrownBy(() -> signer.verifyChain(C1, Q1, bad))
                .hasMessage("tool turn 1 failed verification");
    }
}
