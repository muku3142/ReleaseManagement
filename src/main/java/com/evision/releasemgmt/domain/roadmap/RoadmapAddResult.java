package com.evision.releasemgmt.domain.roadmap;

import java.util.List;
import java.util.UUID;

public record RoadmapAddResult(List<Added> added, List<AlreadyPresent> alreadyPresent, List<String> notFound) {

    public record Added(UUID itemId, String jiraKey) {}

    public record AlreadyPresent(String jiraKey, UUID itemId) {}
}
