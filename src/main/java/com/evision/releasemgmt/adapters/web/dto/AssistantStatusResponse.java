package com.evision.releasemgmt.adapters.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AssistantStatusResponse(boolean enabled, Starters starters) {

    public record Starters(List<String> unscoped, List<String> release) {
    }
}
