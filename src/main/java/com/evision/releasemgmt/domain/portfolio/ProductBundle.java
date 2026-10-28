package com.evision.releasemgmt.domain.portfolio;

import java.util.List;
import java.util.UUID;

public record ProductBundle(UUID id, String name, List<String> ticketKeys) {
}
