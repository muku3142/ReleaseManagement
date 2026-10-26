package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.roadmap.JiraSnapshot;
import com.evision.releasemgmt.domain.roadmap.RiceInputs;
import com.evision.releasemgmt.domain.roadmap.RoadmapItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemDetails;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

public interface RoadmapRepository {

    /** Archived included; unordered (ranking is RoadmapRanking's job). */
    List<RoadmapItem> findAll();

    Optional<RoadmapItem> findById(UUID itemId);

    /** jira_key -> item id for the given keys that are already on the roadmap (archived included). */
    Map<String, UUID> findIdsByJiraKey(Collection<String> jiraKeys);

    /** ROADMAP_JIRA_KEY_DUPLICATE when jira_key or jira_issue_id is already used. */
    RoadmapItem add(RoadmapItem item);

    /**
     * Row-locks the item, applies decide to it, writes the returned inputs and one InputChange per changed field
     * (actor, at) in the same transaction. No change = no write. RoadmapItemNotFoundException when absent.
     */
    RoadmapItem setInputsUnderLock(UUID itemId, Function<RoadmapItem, RiceInputs> decide, String actor, Instant at);

    /**
     * Writes status, product, quarter, owner, notes; title only for MANUAL items and only when non-null.
     * Never touches inputs or jira_* columns.
     */
    RoadmapItem updateDetails(UUID itemId, RoadmapItemDetails details);

    void setArchivedAt(UUID itemId, Instant archivedAt);

    /**
     * Targeted update of title, jira_key, jira_status, jira_updated_at, jira_synced_at; clears jira_unavailable_since.
     * Never touches inputs, product or status (ROAD-04).
     */
    void applyJiraSnapshot(UUID itemId, JiraSnapshot snapshot);

    /** Sets jira_unavailable_since only when it is currently null (keeps the first time it vanished). */
    void markJiraUnavailable(UUID itemId, Instant since);
}
