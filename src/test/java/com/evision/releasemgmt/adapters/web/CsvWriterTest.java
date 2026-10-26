package com.evision.releasemgmt.adapters.web;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvWriterTest {

    @Test
    void plainTextPassesThroughAndNullIsEmpty() {
        assertThat(CsvWriter.text(null)).isEmpty();
        assertThat(CsvWriter.text("plain")).isEqualTo("plain");
    }

    @Test
    void quotesPerRfc4180() {
        assertThat(CsvWriter.text("a,b")).isEqualTo("\"a,b\"");
        assertThat(CsvWriter.text("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"");
        assertThat(CsvWriter.text("l1\nl2")).isEqualTo("\"l1\nl2\"");
    }

    @Test
    void neutralisesLeadingFormulaCharacters() {
        assertThat(CsvWriter.text("=SUM(A1)")).isEqualTo("'=SUM(A1)");
        assertThat(CsvWriter.text("+1")).isEqualTo("'+1");
        assertThat(CsvWriter.text("-1")).isEqualTo("'-1");
        assertThat(CsvWriter.text("@cmd")).isEqualTo("'@cmd");
        assertThat(CsvWriter.text("\tx")).isEqualTo("'\tx");
        assertThat(CsvWriter.text("\rx")).isEqualTo("\"'\rx\"");
        assertThat(CsvWriter.text("=a,b")).isEqualTo("\"'=a,b\"");
    }

    @Test
    void numbersAreNeverPrefixed() {
        assertThat(CsvWriter.number(new BigDecimal("2.50"))).isEqualTo("2.5");
        assertThat(CsvWriter.number(new BigDecimal("-3"))).isEqualTo("-3");
        assertThat(CsvWriter.number(null)).isEmpty();
    }

    @Test
    void documentHasBomAndCrlfRows() {
        byte[] bytes = CsvWriter.document(List.of("a,b", "c"));

        assertThat(bytes[0]).isEqualTo((byte) 0xEF);
        assertThat(bytes[1]).isEqualTo((byte) 0xBB);
        assertThat(bytes[2]).isEqualTo((byte) 0xBF);
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("﻿a,b\r\nc\r\n");
    }

    @Test
    void nonAsciiRoundTrips() {
        byte[] bytes = CsvWriter.document(List.of(CsvWriter.row(List.of("Zürich"))));

        assertThat(new String(bytes, StandardCharsets.UTF_8)).contains("Zürich");
    }
}
