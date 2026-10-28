package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.briefing.BriefingFieldIds;
import com.evision.releasemgmt.domain.briefing.BriefingIssue;
import com.evision.releasemgmt.domain.briefing.SprintCheckRules;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.roadmap.JiraSnapshot;
import com.evision.releasemgmt.domain.roadmap.RoadmapAddResult;
import com.evision.releasemgmt.domain.roadmap.RoadmapItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemNotFoundException;
import com.evision.releasemgmt.domain.roadmap.RoadmapRefreshResult;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejectedException;
import com.evision.releasemgmt.domain.roadmap.RoadmapRejection;
import com.evision.releasemgmt.ports.JiraBriefingPort;
import com.evision.releasemgmt.ports.ProductRepository;
import com.evision.releasemgmt.ports.RoadmapRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

// Not @Transactional: each items.add is its own transaction, so one duplicate cannot abort the rest.
// Only summary, status, key, issue id, updated time, priority name and a readiness count are stored (D-17-09)
// - never description or comments.
@Service
public class RoadmapImportService {

    static final int MAX_KEYS_PER_ADD = 100;
    static final int MAX_ITEMS_PER_REFRESH = 500;
    private static final int FETCH_BATCH_SIZE = 100;
    private static final Pattern TICKET_KEY = Pattern.compile("^[A-Z][A-Z0-9]*-[0-9]+$");

    private final RoadmapRepository items;
    private final TicketSearchService tickets;
    private final ReleaseService releases;
    private final ProductRepository products;
    private final JiraBriefingPort briefing;
    private final JiraAccessContextService access;
    private final FieldMetadataService fields;
    private final Clock clock;

    public RoadmapImportService(RoadmapRepository items, TicketSearchService tickets, ReleaseService releases,
                                ProductRepository products, JiraBriefingPort briefing,
                                JiraAccessContextService access, FieldMetadataService fields, Clock clock) {
        this.items = items;
        this.tickets = tickets;
        this.releases = releases;
        this.products = products;
        this.briefing = briefing;
        this.access = access;
        this.fields = fields;
        this.clock = clock;
    }

    private record JiraFacts(String priority, Integer readiness) {}

    public RoadmapAddResult addFromJira(List<String> keys, String actor) {
        List<String> requested = distinctWithinCap(keys);
        List<String> notFound = new ArrayList<>();
        List<String> valid = new ArrayList<>();
        for (String key : requested) {
            if (TICKET_KEY.matcher(key).matches()) {
                valid.add(key);
            } else {
                notFound.add(key);
            }
        }

        List<RoadmapAddResult.AlreadyPresent> alreadyPresent = new ArrayList<>();
        List<String> toFetch = new ArrayList<>();
        Map<String, UUID> onRoadmap = valid.isEmpty() ? Map.of() : items.findIdsByJiraKey(valid);
        for (String key : valid) {
            if (onRoadmap.containsKey(key)) {
                alreadyPresent.add(new RoadmapAddResult.AlreadyPresent(key, onRoadmap.get(key)));
            } else {
                toFetch.add(key);
            }
        }

        // Fetch phase: every Jira call finishes before the first insert.
        Map<String, TicketDetail> fetched = fetch(toFetch);

        List<String> moved = fetched.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getKey().equals(e.getValue().key()))
                .map(e -> e.getValue().key())
                .toList();
        Map<String, UUID> movedOnRoadmap = moved.isEmpty() ? Map.of() : items.findIdsByJiraKey(moved);
        Map<String, JiraFacts> facts = facts(fetched.values().stream()
                .filter(Objects::nonNull).map(TicketDetail::key).distinct().toList());

        Instant now = clock.instant();
        List<RoadmapAddResult.Added> added = new ArrayList<>();
        List<RoadmapAddResult.Rejected> rejected = new ArrayList<>();
        Map<String, Owner> owners = new HashMap<>();
        Map<String, Boolean> handled = new HashMap<>();
        for (String key : toFetch) {
            TicketDetail ticket = fetched.get(key);
            if (ticket == null) {
                notFound.add(key);
            } else if (movedOnRoadmap.containsKey(ticket.key())) {
                alreadyPresent.add(new RoadmapAddResult.AlreadyPresent(ticket.key(), movedOnRoadmap.get(ticket.key())));
            } else if (handled.put(ticket.key(), Boolean.TRUE) == null) {
                insert(snapshot(ticket, now, facts.get(ticket.key())), actor, now, owners, added, alreadyPresent,
                        rejected);
            }
        }
        return new RoadmapAddResult(added, alreadyPresent, notFound, rejected);
    }

    public RoadmapAddResult addFromRelease(UUID releaseId, List<String> ticketKeys, String actor) {
        List<String> requested = distinctWithinCap(ticketKeys);
        Release release = releases.get(releaseId);
        Map<String, ReleaseItem> inRelease = new HashMap<>();
        release.items().forEach(item -> inRelease.put(item.ticketKey(), item));

        List<String> notFound = new ArrayList<>();
        List<String> found = new ArrayList<>();
        for (String key : requested) {
            (inRelease.containsKey(key) ? found : notFound).add(key);
        }

        List<RoadmapAddResult.AlreadyPresent> alreadyPresent = new ArrayList<>();
        Map<String, UUID> onRoadmap = found.isEmpty() ? Map.of() : items.findIdsByJiraKey(found);
        List<String> toInsert = new ArrayList<>();
        for (String key : found) {
            if (onRoadmap.containsKey(key)) {
                alreadyPresent.add(new RoadmapAddResult.AlreadyPresent(key, onRoadmap.get(key)));
            } else {
                toInsert.add(key);
            }
        }
        // Fetch phase: a Jira failure here fails the add before anything is written, like add-from-Jira.
        Map<String, JiraFacts> facts = facts(toInsert);

        Instant now = clock.instant();
        List<RoadmapAddResult.Added> added = new ArrayList<>();
        List<RoadmapAddResult.Rejected> rejected = new ArrayList<>();
        Map<String, Owner> owners = new HashMap<>();
        for (String key : toInsert) {
            ReleaseItem item = inRelease.get(key);
            Instant syncedAt = later(release.lastRefreshedAt(), item.addedAt());
            insert(snapshot(item.ticket(), syncedAt, facts.get(key)), actor, now, owners, added, alreadyPresent,
                    rejected);
        }
        return new RoadmapAddResult(added, alreadyPresent, notFound, rejected);
    }

    public RoadmapRefreshResult refreshAll() {
        List<RoadmapItem> targets = items.findAll().stream().filter(i -> i.isJira() && !i.archived()).toList();
        if (targets.size() > MAX_ITEMS_PER_REFRESH) {
            throw new RoadmapRejectedException(RoadmapRejection.ROADMAP_REFRESH_TOO_LARGE);
        }
        return targets.isEmpty() ? new RoadmapRefreshResult(0, List.of()) : refreshItems(targets);
    }

    public RoadmapRefreshResult refresh(UUID itemId) {
        RoadmapItem item = items.findById(itemId).orElseThrow(RoadmapItemNotFoundException::new);
        if (!item.isJira()) {
            throw new RoadmapRejectedException(RoadmapRejection.ROADMAP_ITEM_NOT_JIRA);
        }
        return refreshItems(List.of(item));
    }

    private RoadmapRefreshResult refreshItems(List<RoadmapItem> targets) {
        // FETCH (no writes). D-17-13: "gone" is only concluded from a call that succeeded; any
        // JiraTicketReadException propagates before anything is written, so a failed refresh changes nothing.
        Map<String, TicketDetail> byIssueId = new HashMap<>();
        Map<String, TicketDetail> byKey = new HashMap<>();
        for (int from = 0; from < targets.size(); from += FETCH_BATCH_SIZE) {
            List<String> batch = targets.subList(from, Math.min(from + FETCH_BATCH_SIZE, targets.size())).stream()
                    .map(RoadmapItem::jiraKey).toList();
            for (String key : batch) {
                if (!TICKET_KEY.matcher(key).matches()) {
                    throw new IllegalArgumentException("Invalid ticket key");
                }
            }
            for (TicketDetail t : tickets.search(TicketSearchCriteria.byJql("key in (" + String.join(", ", batch) + ")"))) {
                byIssueId.put(t.jiraIssueId(), t);
                byKey.put(t.key(), t);
            }
        }

        Map<RoadmapItem, TicketDetail> matched = new LinkedHashMap<>();
        List<RoadmapItem> gone = new ArrayList<>();
        for (RoadmapItem item : targets) {
            TicketDetail found = byIssueId.get(item.jiraIssueId());
            if (found == null) {
                found = byKey.get(item.jiraKey());
            }
            if (found == null) {
                found = tickets.findByKey(item.jiraKey()).orElse(null);
            }
            if (found == null) {
                gone.add(item);
            } else {
                matched.put(item, found);
            }
        }
        Map<String, JiraFacts> facts = facts(matched.values().stream().map(TicketDetail::key).distinct().toList());

        // APPLY
        Instant now = clock.instant();
        List<String> changedKeys = matched.entrySet().stream()
                .filter(e -> !e.getKey().jiraKey().equals(e.getValue().key()))
                .map(e -> e.getValue().key()).distinct().toList();
        Map<String, UUID> owners = changedKeys.isEmpty() ? Map.of() : items.findIdsByJiraKey(changedKeys);
        Map<String, UUID> claimed = new HashMap<>(owners);
        matched.forEach((item, ticket) -> {
            String key = ticket.key();
            UUID owner = claimed.get(key);
            if (owner != null && !owner.equals(item.id())) {
                key = item.jiraKey();
            } else {
                claimed.put(key, item.id());
            }
            // An issue missing from the readiness search keeps its stored priority and readiness.
            JiraFacts current = facts.getOrDefault(ticket.key(),
                    new JiraFacts(item.jiraPriority(), item.jiraReadiness()));
            items.applyJiraSnapshot(item.id(), new JiraSnapshot(key, ticket.jiraIssueId(), ticket.summary(),
                    ticket.status(), ticket.updatedAt(), now, current.priority(), current.readiness()));
        });
        List<RoadmapRefreshResult.Unavailable> unavailable = new ArrayList<>();
        for (RoadmapItem item : gone) {
            items.markJiraUnavailable(item.id(), now);
            unavailable.add(new RoadmapRefreshResult.Unavailable(item.id(), item.jiraKey()));
        }
        return new RoadmapRefreshResult(matched.size(), unavailable);
    }

    private List<String> distinctWithinCap(List<String> keys) {
        List<String> distinct = keys.stream().distinct().toList();
        if (distinct.size() > MAX_KEYS_PER_ADD) {
            throw new RoadmapRejectedException(RoadmapRejection.ROADMAP_TOO_MANY_ITEMS);
        }
        return distinct;
    }

    private record Owner(UUID productId, RoadmapRejection rejection) {}

    private Owner ownerOf(String jiraKey, Map<String, Owner> cache) {
        String project = jiraKey.substring(0, jiraKey.lastIndexOf('-'));
        return cache.computeIfAbsent(project, p -> products.productIdOwningJiraKey(p)
                .map(id -> products.findById(id).filter(product -> !product.archived()).isPresent()
                        ? new Owner(id, null)
                        : new Owner(null, RoadmapRejection.ROADMAP_PRODUCT_ARCHIVED))
                .orElse(new Owner(null, RoadmapRejection.ROADMAP_NO_PRODUCT_FOR_PROJECT)));
    }

    private void insert(JiraSnapshot snapshot, String actor, Instant now, Map<String, Owner> owners,
                        List<RoadmapAddResult.Added> added, List<RoadmapAddResult.AlreadyPresent> alreadyPresent,
                        List<RoadmapAddResult.Rejected> rejected) {
        Owner owner = ownerOf(snapshot.jiraKey(), owners);
        if (owner.rejection() != null) {
            rejected.add(new RoadmapAddResult.Rejected(snapshot.jiraKey(), owner.rejection()));
            return;
        }
        try {
            RoadmapItem item = RoadmapItem.fromJira(snapshot, owner.productId(), actor, now);
            items.add(item);
            added.add(new RoadmapAddResult.Added(item.id(), snapshot.jiraKey()));
        } catch (RoadmapRejectedException e) {
            if (e.reason() != RoadmapRejection.ROADMAP_JIRA_KEY_DUPLICATE) {
                throw e;
            }
            UUID existing = items.findIdsByJiraKey(List.of(snapshot.jiraKey())).get(snapshot.jiraKey());
            alreadyPresent.add(new RoadmapAddResult.AlreadyPresent(snapshot.jiraKey(), existing));
        }
    }

    // Result is keyed by the REQUESTED key; the value may carry a different key for a moved issue, null when gone.
    // A batch that comes back short (the adapter turns "An issue with key" into an empty list for the WHOLE batch)
    // is retried key by key, so one deleted key cannot hide the others.
    private Map<String, TicketDetail> fetch(List<String> keys) {
        Map<String, TicketDetail> result = new LinkedHashMap<>();
        for (int from = 0; from < keys.size(); from += FETCH_BATCH_SIZE) {
            List<String> batch = keys.subList(from, Math.min(from + FETCH_BATCH_SIZE, keys.size()));
            Map<String, TicketDetail> byKey = new HashMap<>();
            for (String key : batch) {
                if (!TICKET_KEY.matcher(key).matches()) {
                    throw new IllegalArgumentException("Invalid ticket key");
                }
            }
            tickets.search(TicketSearchCriteria.byJql("key in (" + String.join(", ", batch) + ")"))
                    .forEach(t -> byKey.put(t.key(), t));
            for (String key : batch) {
                TicketDetail ticket = byKey.get(key);
                if (ticket == null) {
                    Optional<TicketDetail> single = tickets.findByKey(key);
                    ticket = single.orElse(null);
                }
                result.put(key, ticket);
            }
        }
        return result;
    }

    // Priority and readiness signals per current key, read in batches; no keys means no Jira call.
    private Map<String, JiraFacts> facts(List<String> currentKeys) {
        Map<String, JiraFacts> result = new HashMap<>();
        if (currentKeys.isEmpty()) {
            return result;
        }
        JiraAccessContext context = access.currentContext();
        BriefingFieldIds ids = fields.briefingFieldIds(context);
        for (int from = 0; from < currentKeys.size(); from += FETCH_BATCH_SIZE) {
            List<String> batch = currentKeys.subList(from, Math.min(from + FETCH_BATCH_SIZE, currentKeys.size()));
            for (String key : batch) {
                if (!TICKET_KEY.matcher(key).matches()) {
                    throw new IllegalArgumentException("Invalid ticket key");
                }
            }
            for (BriefingIssue issue : briefing.search(context, "key in (" + String.join(", ", batch) + ")", ids,
                    batch.size())) {
                result.put(issue.key(),
                        new JiraFacts(issue.priority(), SprintCheckRules.readinessSignals(issue, ids)));
            }
        }
        return result;
    }

    private static JiraSnapshot snapshot(TicketDetail ticket, Instant syncedAt, JiraFacts facts) {
        return new JiraSnapshot(ticket.key(), ticket.jiraIssueId(), ticket.summary(), ticket.status(),
                ticket.updatedAt(), syncedAt, facts == null ? null : facts.priority(),
                facts == null ? null : facts.readiness());
    }

    private static Instant later(Instant a, Instant b) {
        if (a == null) {
            return b;
        }
        return b == null || a.isAfter(b) ? a : b;
    }
}
