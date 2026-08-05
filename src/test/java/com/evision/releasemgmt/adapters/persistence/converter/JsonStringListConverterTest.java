package com.evision.releasemgmt.adapters.persistence.converter;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JsonStringListConverterTest {

    private final JsonStringListConverter converter =
            new JsonStringListConverter(JsonMapper.builder().build());

    @Test
    void convertsListOfStringsToJsonArray() {
        assertThat(converter.convertToDatabaseColumn(List.of("a", "b"))).isEqualTo("[\"a\",\"b\"]");
    }

    @Test
    void convertsNullListToEmptyJsonArray() {
        assertThat(converter.convertToDatabaseColumn(null)).isEqualTo("[]");
    }

    @Test
    void convertsEmptyListToEmptyJsonArray() {
        assertThat(converter.convertToDatabaseColumn(List.of())).isEqualTo("[]");
    }

    @Test
    void convertsJsonArrayToListOfStrings() {
        assertThat(converter.convertToEntityAttribute("[\"a\",\"b\"]")).isEqualTo(List.of("a", "b"));
    }

    @Test
    void convertsNullColumnToEmptyList() {
        assertThat(converter.convertToEntityAttribute(null)).isEmpty();
    }

    @Test
    void convertsBlankColumnToEmptyList() {
        assertThat(converter.convertToEntityAttribute("")).isEmpty();
    }

    @Test
    void roundTripsQuotesNewlinesAndNonAsciiCharacters() {
        String value = "He said \"ship it\"\nDone — ✓";
        String stored = converter.convertToDatabaseColumn(List.of(value));
        assertThat(converter.convertToEntityAttribute(stored)).containsExactly(value);
    }
}
