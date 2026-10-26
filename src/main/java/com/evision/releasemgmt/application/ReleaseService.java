package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.domain.portfolio.ReleaseProduct;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import com.evision.releasemgmt.domain.release.AddTicketsResult;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.PackagesUrl;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseRefreshIncompleteException;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import com.evision.releasemgmt.domain.release.UnfreezeRejection;
import com.evision.releasemgmt.ports.ProductRepository;
import com.evision.releasemgmt.ports.ReleaseProductRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class ReleaseService {

    // A Jira issue key is <PROJECT>-<number>. Anything else never reaches the JQL string: a key
    // is the one value on this path that is composed into a query rather than bound, so it is
    // validated at the sink as well as at the REST boundary.
    private static final Pattern TICKET_KEY = Pattern.compile("^[A-Z][A-Z0-9]*-[0-9]+$");

    // Jira's /search/jql has a practical URL/body limit and a release can hold hundreds of items,
    // so refresh fetches in chunks rather than one enormous key list.
    private static final int FETCH_BATCH_SIZE = 100;

    private static final Logger log = LoggerFactory.getLogger(ReleaseService.class);

    private final ReleaseRepository repository;
    private final TicketSearchService ticketSearchService;
    private final EnrichmentService enrichmentService;
    private final ReleaseProductRepository releaseProducts;
    private final ProductRepository products;

    public ReleaseService(ReleaseRepository repository, TicketSearchService ticketSearchService,
                          EnrichmentService enrichmentService, ReleaseProductRepository releaseProducts,
                          ProductRepository products) {
        this.repository = repository;
        this.ticketSearchService = ticketSearchService;
        this.enrichmentService = enrichmentService;
        this.releaseProducts = releaseProducts;
        this.products = products;
    }

    public List<ReleaseProduct> productsOf(UUID releaseId) {
        return releaseProducts.productsOf(releaseId);
    }

    public Map<UUID, List<ReleaseProduct>> productsOfReleases(Collection<UUID> releaseIds) {
        return releaseProducts.productsOfReleases(releaseIds);
    }

    // D-15-01: no requireOpen - a FROZEN release's products stay editable so v1.1 releases can be tagged and receive installs.
    // D-15-18: an empty set is rejected by replace (RELEASE_LAST_PRODUCT), the single home of the never-empty rule.
    public Release setProducts(UUID releaseId, Collection<UUID> productIds) {
        releaseProducts.replace(releaseId, Set.copyOf(productIds));
        return get(releaseId);
    }

    // D-17-21: no requireOpen - the packages link is editable in every status and is not release content.
    public Release setPackagesUrl(UUID releaseId, String url) {
        return repository.setPackagesUrl(releaseId, PackagesUrl.normalize(url));
    }

    public AddTicketsResult createWithTickets(String name, String version, String jiraProjectKey,
                                              List<String> ticketKeys, List<UUID> productIds,
                                              String actor) {
        Set<UUID> requestedProducts = Set.copyOf(productIds);
        if (requestedProducts.isEmpty()) {
            throw new PortfolioEditRejectedException(PortfolioEditRejection.RELEASE_PRODUCT_REQUIRED);
        }
        for (UUID productId : requestedProducts) {
            Product product = products.findById(productId).orElseThrow(ProductNotFoundException::new);
            if (product.archived()) {
                throw new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_ARCHIVED);
            }
        }
        Instant now = Instant.now();
        List<String> requested = distinct(ticketKeys);
        List<TicketDetail> fetched = fetchTickets(requested);
        List<ReleaseItem> items = fetched.stream()
                .map(ticket -> new ReleaseItem(ticket, now, CodeLinks.notAttempted()))
                .toList();
        Release release = Release.create(name.trim(), version.trim(), blankToNull(jiraProjectKey),
                actor, now);
        Release created = repository.create(release, items);
        try {
            releaseProducts.replace(created.id(), requestedProducts);
        } catch (RuntimeException e) {
            repository.delete(created.id());   // D-15-18: a just-created OPEN release must not survive without products
            throw e;
        }
        enrichQuietly(created.id(), fetched);
        return new AddTicketsResult(get(created.id()), items.size(), 0, requested.size() - items.size());
    }

    public Release get(UUID id) {
        return repository.findById(id).orElseThrow(ReleaseNotFoundException::new);
    }

    public List<ReleaseSummary> search(String query) {
        return repository.search(query == null ? "" : query.trim());
    }

    public List<ReleaseSummary> releasesContaining(String ticketKey) {
        return repository.releasesContaining(ticketKey);
    }

    public AddTicketsResult addTickets(UUID releaseId, List<String> ticketKeys) {
        Release release = get(releaseId);
        release.requireOpen();

        List<String> requested = distinct(ticketKeys);
        Set<String> present = Set.copyOf(repository.findTicketKeys(releaseId));
        List<String> toFetch = requested.stream().filter(key -> !present.contains(key)).toList();
        int alreadyPresent = requested.size() - toFetch.size();

        if (toFetch.isEmpty()) {
            // D-03-13: a bulk push whose whole selection is already on the release is a success,
            // not a 409. The counts let the UI say so honestly.
            return new AddTicketsResult(release, 0, alreadyPresent, 0);
        }

        Instant now = Instant.now();
        // De-duplicated against the keys Jira returned, not the ones requested: an issue moved
        // between projects is requested as OLD-1 and comes back as NEW-5, so filtering the
        // request alone would insert a key already on the release and trip the unique constraint.
        List<TicketDetail> fetched = fetchTickets(toFetch);
        List<TicketDetail> inserted = fetched.stream()
                .filter(ticket -> !present.contains(ticket.key()))
                .toList();
        List<ReleaseItem> items = inserted.stream()
                .map(ticket -> new ReleaseItem(ticket, now, CodeLinks.notAttempted()))
                .toList();
        Release updated = release;
        if (!items.isEmpty()) {
            repository.addItems(releaseId, items);
            enrichQuietly(releaseId, inserted);
            updated = get(releaseId);
        }
        return new AddTicketsResult(updated, items.size(),
                alreadyPresent + (fetched.size() - items.size()), toFetch.size() - fetched.size());
    }

    public Release removeTicket(UUID releaseId, String ticketKey) {
        get(releaseId).requireOpen();          // REL-02: removal only while OPEN
        return repository.removeItem(releaseId, ticketKey);
    }

    public Release refresh(UUID releaseId) {
        Release release = get(releaseId);
        release.requireOpen();                 // freeze is simply the point after which this is refused
        List<String> keys = repository.findTicketKeys(releaseId);
        Instant now = Instant.now();
        List<TicketDetail> fetched = fetchTickets(keys);
        List<ReleaseItem> refreshed = fetched.stream()
                .map(ticket -> new ReleaseItem(ticket, now, CodeLinks.notAttempted()))
                .toList();
        if (refreshed.size() != keys.size()) {
            // replaceItemSnapshots only overwrites the items Jira returned but stamps the whole
            // release, so a partial response would leave stale rows under a fresh timestamp.
            throw new ReleaseRefreshIncompleteException();
        }
        repository.replaceItemSnapshots(releaseId, refreshed, now);
        enrichQuietly(releaseId, fetched);
        return get(releaseId);
    }

    /** D-06-04: the standalone action; a frozen release is refused (409) and per-item failures are stored, not thrown. */
    public Release refreshCodeLinks(UUID releaseId, String ticketKeyOrNull) {
        get(releaseId).requireOpen();
        List<String> keys = repository.findTicketKeys(releaseId);
        if (ticketKeyOrNull != null) {
            keys = keys.contains(ticketKeyOrNull) ? List.of(ticketKeyOrNull) : List.of();
        }
        // The search is re-run so pre-Phase-6 rows get their numeric id and history sees the current updated;
        // snapshots are not overwritten here, so a no-op run cannot mark documents stale (saveEnrichment
        // stamps only on document-visible change).
        enrichmentService.enrich(releaseId, fetchTickets(keys));
        return get(releaseId);
    }

    public Release freeze(UUID id, String actor) {
        // D-03-10: freezing an already-frozen release is a conflict, not a no-op - silently succeeding would hide a stale UI.
        get(id).requireOpen();
        return repository.freeze(id, actor, Instant.now());
    }

    // D-15.1-01/02: the adapter checks status and both ledgers under the release row lock; this layer only records who did it
    // (an INFO line is the record - there is no audit table, and Phase 15 recorded none for product or client edits).
    public Release unfreeze(UUID id, String actor) {
        Release release = repository.unfreeze(id);
        log.info("Release {} unfrozen by {}", id, actor);
        return release;
    }

    // Drives the blocked Unfreeze button only; unfreeze itself never trusts this read.
    public Optional<UnfreezeRejection> unfreezeBlocker(Release release) {
        return release.isFrozen() ? repository.unfreezeBlocker(release.id()) : Optional.empty();
    }

    public void delete(UUID id) {
        // D-03-11: an OPEN release has no history anyone relies on; a FROZEN one is an audit
        // record and deletion must be refused.
        get(id).requireOpen();
        repository.delete(id);
    }

    public Release addManualItem(UUID releaseId, String title, String description,
                                 ManualItemCategory category) {
        get(releaseId).requireOpen();
        ManualItem item = ManualItem.create(title.trim(), nullToEmpty(description), category,
                Instant.now());
        return repository.addManualItem(releaseId, item);
    }

    public Release updateManualItem(UUID releaseId, UUID manualItemId, String title,
                                    String description, ManualItemCategory category) {
        get(releaseId).requireOpen();
        // addedAt is carried by the stored row and deliberately not overwritten here - editing an
        // item does not change when it joined the release. The adapter's updateManualItem only
        // copies title, description and category across.
        ManualItem item = new ManualItem(manualItemId, title.trim(), nullToEmpty(description),
                category, null);
        return repository.updateManualItem(releaseId, item);
    }

    public Release removeManualItem(UUID releaseId, UUID manualItemId) {
        get(releaseId).requireOpen();
        return repository.removeManualItem(releaseId, manualItemId);
    }

    // D-06-04: enrichment is best effort on these paths. It runs after the core write, so its failure (including a freeze
    // that landed during the fan-out, which saveEnrichment's lock rejects) can never undo or fail the add/refresh asked for.
    private void enrichQuietly(UUID releaseId, List<TicketDetail> tickets) {
        try {
            enrichmentService.enrich(releaseId, tickets);
        } catch (RuntimeException e) {
            log.warn("Code-link enrichment for release {} did not complete", releaseId, e);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private List<TicketDetail> fetchTickets(List<String> keys) {
        List<String> safe = keys.stream().filter(k -> k != null && TICKET_KEY.matcher(k).matches()).toList();
        if (safe.isEmpty()) {
            return List.of();
        }
        List<TicketDetail> all = new ArrayList<>();
        for (int start = 0; start < safe.size(); start += FETCH_BATCH_SIZE) {
            List<String> batch = safe.subList(start, Math.min(start + FETCH_BATCH_SIZE, safe.size()));
            String jql = "key in (" + String.join(", ", batch) + ") ORDER BY key ASC";
            all.addAll(ticketSearchService.search(TicketSearchCriteria.byJql(jql)));
        }
        return all;
    }

    private static List<String> distinct(List<String> keys) {
        return keys == null ? List.of() : keys.stream().filter(Objects::nonNull).distinct().toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
