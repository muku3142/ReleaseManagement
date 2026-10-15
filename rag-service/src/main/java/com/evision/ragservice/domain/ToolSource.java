package com.evision.ragservice.domain;

public record ToolSource(String id, String title, String url) {

    @Override
    public String toString() {
        return "ToolSource[idChars=" + (id == null ? 0 : id.length())
                + ", titleChars=" + (title == null ? 0 : title.length()) + "]";
    }
}
