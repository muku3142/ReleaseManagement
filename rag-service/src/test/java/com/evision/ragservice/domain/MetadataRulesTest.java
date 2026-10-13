package com.evision.ragservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class MetadataRulesTest {

    @Test
    void acceptsFlatStringAndNumberValuesAndNull() {
        assertThatCode(() -> MetadataRules.validateMetadata(Map.of("release_id", "R-1", "count", 3, "ratio", 0.5)))
                .doesNotThrowAnyException();
        assertThatCode(() -> MetadataRules.validateMetadata(null)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNonScalarValues() {
        assertThatThrownBy(() -> MetadataRules.validateMetadata(Map.of("a", Map.of("b", 1))))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> MetadataRules.validateMetadata(Map.of("a", List.of("x"))))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> MetadataRules.validateMetadata(Map.of("a", true)))
                .isInstanceOf(InvalidContentException.class);
        Map<String, Object> withNull = new HashMap<>();
        withNull.put("a", null);
        assertThatThrownBy(() -> MetadataRules.validateMetadata(withNull)).isInstanceOf(InvalidContentException.class);
    }

    @Test
    void rejectsTooManyKeysBadKeysLongValuesAndNul() {
        Map<String, Object> tooMany = new HashMap<>();
        IntStream.rangeClosed(0, MetadataRules.MAX_KEYS).forEach(i -> tooMany.put("k" + i, "v"));
        assertThatThrownBy(() -> MetadataRules.validateMetadata(tooMany)).isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> MetadataRules.validateMetadata(Map.of("bad key", "v")))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> MetadataRules.validateMetadata(Map.of("k".repeat(65), "v")))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> MetadataRules.validateMetadata(Map.of("a", "x".repeat(1001))))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> MetadataRules.validateMetadata(Map.of("a", "x\u0000y")))
                .isInstanceOf(InvalidContentException.class);
    }

    @Test
    void parseFilterBuildsEqualityAndInListClausesInOrder() {
        Map<String, Object> filter = new LinkedHashMap<>();
        filter.put("release_id", "R-1");
        filter.put("project_key", List.of("A", "B"));

        MetadataFilter parsed = MetadataRules.parseFilter(filter);

        assertThat(parsed.clauses()).containsExactly(
                new MetadataFilter.Clause("release_id", List.of("R-1")),
                new MetadataFilter.Clause("project_key", List.of("A", "B")));
    }

    @Test
    void parseFilterOfNullOrEmptyIsNone() {
        assertThat(MetadataRules.parseFilter(null)).isEqualTo(MetadataFilter.none());
        assertThat(MetadataRules.parseFilter(Map.of())).isEqualTo(MetadataFilter.none());
    }

    @Test
    void parseFilterRejectsMalformedValues() {
        Map<String, Object> nullValue = new HashMap<>();
        nullValue.put("a", null);
        List<Map<String, Object>> bad = List.of(
                Map.of("a", List.of()),
                Map.of("a", List.of(List.of("x"))),
                Map.of("a", true),
                nullValue,
                Map.of("a", IntStream.range(0, MetadataRules.MAX_IN_LIST_VALUES + 1).boxed().toList()),
                Map.of("bad key!", "v"));

        for (Map<String, Object> filter : bad) {
            assertThatThrownBy(() -> MetadataRules.parseFilter(filter)).isInstanceOf(InvalidContentException.class);
        }
    }

    @Test
    void parseFilterAppliesTheMetadataKeyAndLengthLimits() {
        Map<String, Object> tooMany = new HashMap<>();
        IntStream.rangeClosed(0, MetadataRules.MAX_KEYS).forEach(i -> tooMany.put("k" + i, "v"));
        String tooLong = "x".repeat(MetadataRules.MAX_STRING_VALUE_LENGTH + 1);

        assertThatThrownBy(() -> MetadataRules.parseFilter(tooMany)).isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> MetadataRules.parseFilter(Map.of("a", tooLong)))
                .isInstanceOf(InvalidContentException.class);
        assertThatThrownBy(() -> MetadataRules.parseFilter(Map.of("a", List.of("ok", tooLong))))
                .isInstanceOf(InvalidContentException.class);
    }

    @Test
    void messagesNameTheKeyButNeverTheValue() {
        assertThatThrownBy(() -> MetadataRules.validateMetadata(Map.of("secret_field", "SENSITIVE\u0000VALUE")))
                .satisfies(e -> assertThat(e.getMessage()).contains("secret_field").doesNotContain("SENSITIVE"));
        assertThatThrownBy(() -> MetadataRules.parseFilter(Map.of("k", List.of("SENSITIVE", true))))
                .satisfies(e -> assertThat(e.getMessage()).contains("k").doesNotContain("SENSITIVE"));
    }

    @Test
    void rejectNulNamesFieldOnly() {
        assertThatThrownBy(() -> MetadataRules.rejectNul("title", "a\u0000b"))
                .hasMessageContaining("title");
        assertThatCode(() -> MetadataRules.rejectNul("title", null)).doesNotThrowAnyException();
    }
}
