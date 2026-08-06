package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import com.evision.releasemgmt.domain.release.AddTicketsResult;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemCategory;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseRefreshIncompleteException;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
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

    private final ReleaseRepository repository;
    private final TicketSearchService ticketSearchService;

    public ReleaseService(ReleaseRepository repository, TicketSearchService ticketSearchService) {
        this.repository = repository;
        this.ticketSearchService = ticketSearchService;
    }

    public AddTicketsResult createWithTickets(String name, String version, String jiraProjectKey,
                                              List<String> ticketKeys, String actor) {
        Instant now = Instant.now();
        List<String> requested = distinct(ticketKeys);
        List<ReleaseItem> items = fetchTickets(requested).stream()
                .map(ticket -> new ReleaseItem(ticket, now))
                .toList();
        Release release = Release.create(name.trim(), version.trim(), blankToNull(jiraProjectKey),
                actor, now);
        Release created = repository.create(release, items);
        return new AddTicketsResult(created, items.size(), 0, requested.size() - items.size());
    }

    public Release get(UUID id) {
        return repository.findById(id).orElseThrow(ReleaseNotFoundException::new);
    }

    public List<ReleaseSummary> search(String query) {
        return repository.search(query == null ? "" : query.trim());
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
        List<ReleaseItem> items = fetched.stream()
                .filter(ticket -> !present.contains(ticket.key()))
                .map(ticket -> new ReleaseItem(ticket, now))
                .toList();
        Release updated = items.isEmpty() ? release : repository.addItems(releaseId, items);
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
        List<ReleaseItem> refreshed = fetchTickets(keys).stream()
                .map(ticket -> new ReleaseItem(ticket, now))
                .toList();
        if (refreshed.size() != keys.size()) {
            // replaceItemSnapshots only overwrites the items Jira returned but stamps the whole
            // release, so a partial response would leave stale rows under a fresh timestamp.
            throw new ReleaseRefreshIncompleteException();
        }
        return repository.replaceItemSnapshots(releaseId, refreshed, now);
    }

    public Release freeze(UUID id, String actor) {
        // D-03-10: freeze is a one-way door, so freezing an already-frozen release is a
        // conflict, not a no-op - silently succeeding would hide a stale UI.
        get(id).requireOpen();
        return repository.freeze(id, actor, Instant.now());
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
