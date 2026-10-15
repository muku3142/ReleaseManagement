package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseSummary;

import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    /** Read-only: the releases that hold this ticket key (D-12-13). */
    List<ReleaseSummary> releasesContaining(String ticketKey);

    List<String> findTicketKeys(UUID releaseId);

    Release addItems(UUID releaseId, List<ReleaseItem> items);

    Release removeItem(UUID releaseId, String ticketKey);

    /** D-03-07: replaces the stored snapshot of every supplied item and stamps last_refreshed_at. */
    Release replaceItemSnapshots(UUID releaseId, List<ReleaseItem> items, Instant refreshedAt);

    /**
     * D-06-01: writes each item's code links and history under the release row lock. Stamps items_changed_at only when
     * what documents render changed (D-06-04); unknown ticket keys are ignored because the item may have been removed meanwhile.
     *
     * @throws com.evision.releasemgmt.domain.release.ReleaseFrozenException when the release froze before the write
     */
    Release saveEnrichment(UUID releaseId, Map<String, CodeLinks> codeLinksByTicketKey);

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
