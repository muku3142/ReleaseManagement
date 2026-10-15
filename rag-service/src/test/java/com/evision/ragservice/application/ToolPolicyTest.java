package com.evision.ragservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.evision.ragservice.domain.InvalidContentException;
import com.evision.ragservice.domain.ToolCall;
import com.evision.ragservice.domain.ToolDefinition;
import com.evision.ragservice.domain.ToolResult;
import com.evision.ragservice.domain.ToolResultTooLargeException;
import com.evision.ragservice.domain.ToolTurn;
import com.evision.ragservice.domain.ToolTurnRejectedException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ToolPolicyTest {

    private final ToolPolicy policy = new ToolPolicy(4, 4, 8000);

    private static final List<ToolDefinition> DEFINITIONS = List.of(
            new ToolDefinition("get_release_status", "d", Map.of("type", "object",
                    "properties", Map.of("version", Map.of("type", "string")), "required", List.of("version"))),
            new ToolDefinition("get_deployments", "d", Map.of("type", "object",
                    "properties", Map.of("version", Map.of("type", "string"),
                            "environment", Map.of("type", "string", "enum", List.of("DEV", "SIT"))),
                    "required", List.of("version"))));

    private static ToolCall status(String id, String version) {
        return new ToolCall(id, "get_release_status", Map.of("version", version));
    }

    private static ToolTurn turn(int round, List<ToolCall> calls, List<ToolResult> results) {
        return new ToolTurn(round, "qid", calls, "sig", results);
    }

    private static ToolTurn okTurn(int round) {
        return turn(round, List.of(status("c" + round, "1." + round)),
                List.of(new ToolResult("c" + round, "ok", false, null)));
    }

    @Test
    void acceptsValidCalls() {
        assertThat(policy.check(List.of(status("a", "1")), DEFINITIONS, List.of())).isEmpty();
        assertThat(policy.check(List.of(status("a", "1"), status("b", "2")), DEFINITIONS, List.of())).isEmpty();
    }

    @Test
    void flagsTooManyCallsAndBadIds() {
        List<ToolCall> five = IntStream.range(0, 5).mapToObj(i -> status("c" + i, "v")).toList();
        assertThat(policy.check(five, DEFINITIONS, List.of())).contains("too many tool calls: at most 4 per round");
        assertThat(policy.check(List.of(status("a", "1"), status("a", "2")), DEFINITIONS, List.of()))
                .contains("duplicate or missing call id");
        assertThat(policy.check(List.of(status(" ", "1")), DEFINITIONS, List.of()))
                .contains("duplicate or missing call id");
    }

    @Test
    void unknownToolIsNotEchoed() {
        List<String> problems = policy.check(
                List.of(new ToolCall("a", "delete_release", Map.of())), DEFINITIONS, List.of());
        assertThat(problems).contains("unknown tool; available tools: get_release_status, get_deployments");
        assertThat(problems.toString()).doesNotContain("delete_release");
    }

    @Test
    void flagsSchemaViolationsWithoutValues() {
        assertThat(policy.check(List.of(new ToolCall("a", "get_release_status", Map.of())), DEFINITIONS, List.of()))
                .contains("tool 'get_release_status': missing required argument 'version'");
    }

    @Test
    void flagsRepeatedCallsIncludingEquivalentNumbers() {
        ToolTurn answered = turn(1, List.of(status("a", "2.4.0")), List.of());
        assertThat(policy.check(List.of(status("b", "2.4.0")), DEFINITIONS, List.of(answered)))
                .contains("tool 'get_release_status' was already called with these arguments; use its result above");
        assertThat(policy.check(List.of(status("b", "2.5.0")), DEFINITIONS, List.of(answered))).isEmpty();

        List<ToolDefinition> numeric = List.of(new ToolDefinition("t", "d", Map.of("type", "object",
                "properties", Map.of("n", Map.of("type", "number")))));
        ToolTurn earlier = turn(1, List.of(new ToolCall("a", "t", Map.of("n", 1))), List.of());
        assertThat(policy.check(List.of(new ToolCall("b", "t", Map.of("n", 1.0))), numeric, List.of(earlier)))
                .hasSize(1);
    }

    @Test
    void acceptsWellFormedResumes() {
        assertThatCode(() -> policy.requireValidResume(List.of(), List.of())).doesNotThrowAnyException();
        for (int n = 1; n <= 4; n++) {
            List<ToolTurn> turns = IntStream.rangeClosed(1, n).mapToObj(ToolPolicyTest::okTurn).toList();
            assertThatCode(() -> policy.requireValidResume(turns, DEFINITIONS)).doesNotThrowAnyException();
        }
    }

    @Test
    void rejectsMalformedResumes() {
        List<ToolTurn> five = IntStream.rangeClosed(1, 5).mapToObj(ToolPolicyTest::okTurn).toList();
        assertThatThrownBy(() -> policy.requireValidResume(five, DEFINITIONS))
                .isExactlyInstanceOf(ToolTurnRejectedException.class).hasMessage("at most 4 tool rounds");
        assertRejected(List.of(okTurn(1)), List.of());
        assertRejected(List.of(turn(1, List.of(new ToolCall("a", "delete_release", Map.of())),
                List.of(new ToolResult("a", "x", false, null)))), DEFINITIONS);
        assertRejected(List.of(turn(1, List.of(status("a", "1"), status("b", "2")),
                List.of(new ToolResult("a", "x", false, null)))), DEFINITIONS);
        assertRejected(List.of(turn(1, List.of(status("a", "1")),
                List.of(new ToolResult("a", "x", false, null), new ToolResult("z", "x", false, null)))), DEFINITIONS);
        assertRejected(List.of(turn(1, List.of(status("a", "1"), status("b", "2")),
                List.of(new ToolResult("a", "x", false, null), new ToolResult("a", "x", false, null)))), DEFINITIONS);
        assertRejected(List.of(turn(1, List.of(), List.of())), DEFINITIONS);
    }

    @Test
    void acceptsResultsInADifferentOrder() {
        ToolTurn turn = turn(1, List.of(status("a", "1"), status("b", "2")),
                List.of(new ToolResult("b", "x", false, null), new ToolResult("a", "y", false, null)));
        assertThatCode(() -> policy.requireValidResume(List.of(turn), DEFINITIONS)).doesNotThrowAnyException();
        assertThat(turn.resultFor("a").content()).isEqualTo("y");
    }

    @Test
    void enforcesPerResultCapAndNul() {
        ToolTurn big = turn(1, List.of(status("a", "1")), List.of(new ToolResult("a", "x".repeat(8001), false, null)));
        assertThatThrownBy(() -> policy.requireValidResume(List.of(big), DEFINITIONS))
                .isExactlyInstanceOf(ToolResultTooLargeException.class).hasMessageContaining("8000 characters");
        ToolTurn nul = turn(1, List.of(status("a", "1")), List.of(new ToolResult("a", "x\u0000", false, null)));
        assertThatThrownBy(() -> policy.requireValidResume(List.of(nul), DEFINITIONS))
                .isInstanceOf(InvalidContentException.class);
    }

    @Test
    void finalRoundIsReachedAtMaxRounds() {
        assertThat(policy.isFinalRound(List.of(okTurn(1), okTurn(2), okTurn(3)))).isFalse();
        assertThat(policy.isFinalRound(List.of(okTurn(1), okTurn(2), okTurn(3), okTurn(4)))).isTrue();
    }

    private void assertRejected(List<ToolTurn> turns, List<ToolDefinition> definitions) {
        assertThatThrownBy(() -> policy.requireValidResume(turns, definitions))
                .isExactlyInstanceOf(ToolTurnRejectedException.class);
    }
}
