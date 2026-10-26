package com.evision.releasemgmt.adapters.web;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CsvWriter {

    // Excel assumes the ANSI code page for CSV without a BOM, which mangles non-ASCII text.
    private static final byte[] UTF8_BOM = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
    private static final String ROW_END = "\r\n";
    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    private CsvWriter() {
    }

    // OWASP CSV injection: a cell starting with a formula trigger is prefixed so spreadsheets read it as text.
    public static String text(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String safe = FORMULA_TRIGGERS.indexOf(value.charAt(0)) >= 0 ? "'" + value : value;
        boolean needsQuotes = safe.indexOf(',') >= 0 || safe.indexOf('"') >= 0
                || safe.indexOf('\r') >= 0 || safe.indexOf('\n') >= 0;
        return needsQuotes ? "\"" + safe.replace("\"", "\"\"") + "\"" : safe;
    }

    public static String number(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    public static String row(List<String> cells) {
        return String.join(",", cells);
    }

    public static byte[] document(List<String> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(UTF8_BOM);
        for (String row : rows) {
            out.writeBytes((row + ROW_END).getBytes(StandardCharsets.UTF_8));
        }
        return out.toByteArray();
    }
}
