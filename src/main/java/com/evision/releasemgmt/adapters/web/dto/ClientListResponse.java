package com.evision.releasemgmt.adapters.web.dto;

import java.util.List;
import java.util.UUID;

public record ClientListResponse(List<Item> clients) {

    public record Item(UUID id, String name, boolean archived, List<UUID> productIds) {
    }
}
