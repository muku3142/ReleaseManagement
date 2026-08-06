package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseSummary;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReleaseRepository {

    /**
     * Creates the release and its initial items in one transaction, so a failed item write
     * cannot leave an orphan empty release behind (D-03-02).
     *
     * @throws com.evision.releasemgmt.domain.release.ReleaseDuplicateException
     *         when (name, version) already exists
     */
    Release create(Release release, List<ReleaseItem> items);

    Optional<Release> findById(UUID id);

    /** Case-insensitive substring match on name or version; a blank query returns everything. */
    List<ReleaseSummary> search(String query);

    List<String> findTicketKeys(UUID releaseId);

    Release addItems(UUID releaseId, List<ReleaseItem> items);

    Release removeItem(UUID releaseId, String ticketKey);

    /** D-03-07: replaces the stored snapshot of every supplied item and stamps last_refreshed_at. */
    Release replaceItemSnapshots(UUID releaseId, List<ReleaseItem> items, Instant refreshedAt);

    Release freeze(UUID releaseId, String actor, Instant at);

    void delete(UUID releaseId);

    Release addManualItem(UUID releaseId, ManualItem item);

    /**
     * @throws com.evision.releasemgmt.domain.release.ManualItemNotFoundException
     *         when the release has no manual item with this id - an edit that silently saved
     *         nothing would look identical to a successful one
     */
    Release updateManualItem(UUID releaseId, ManualItem item);

    /** Idempotent: an id that is not on the release is a no-op, matching {@link #removeItem}. */
    Release removeManualItem(UUID releaseId, UUID manualItemId);
}
