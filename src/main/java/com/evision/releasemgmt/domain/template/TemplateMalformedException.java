package com.evision.releasemgmt.domain.template;

// TMPL-04's save-blocking case. The detail is dynamic (it names the specific defect) and the field
// says which of Body / Cover title / Cover subtitle / Header text / Footer text it came from, so
// GlobalExceptionHandler can hand the SPA a message that points at the right input.
public class TemplateMalformedException extends RuntimeException {

    private final String field;

    public TemplateMalformedException(String field, String detail) {
        super(detail);
        this.field = field;
    }

    public String field() {
        return field;
    }
}
