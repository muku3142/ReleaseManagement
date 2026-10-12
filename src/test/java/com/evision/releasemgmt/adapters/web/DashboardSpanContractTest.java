package com.evision.releasemgmt.adapters.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DashboardSpanContractTest {

    // The UI date validation and the server bound must agree, or a valid-looking range gets a 400.
    @Test
    void frontendSpanLimitMatchesTheServer() throws IOException {
        String source = Files.readString(Path.of("frontend/src/api/dashboard.ts"));

        assertThat(source)
                .contains("export const MAX_HISTORY_SPAN_DAYS = " + DashboardController.MAX_HISTORY_SPAN_DAYS + ";");
    }
}
