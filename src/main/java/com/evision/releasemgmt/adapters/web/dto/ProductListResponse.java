package com.evision.releasemgmt.adapters.web.dto;

import java.util.List;
import java.util.UUID;

public record ProductListResponse(List<Item> products) {

    public record Item(UUID id, String name, boolean archived, long clientCount, long releaseCount,
                      List<String> jiraProjectKeys) {
    }
}
