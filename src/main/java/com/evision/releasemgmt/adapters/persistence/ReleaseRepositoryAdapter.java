package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.ReleaseEntity;
import com.evision.releasemgmt.adapters.persistence.entity.ReleaseItemCommitEntity;
import com.evision.releasemgmt.adapters.persistence.entity.ReleaseItemPullRequestEntity;
import com.evision.releasemgmt.adapters.persistence.entity.ReleaseItemStatusChangeEntity;
import com.evision.releasemgmt.adapters.persistence.entity.ReleaseManualItemEntity;
import com.evision.releasemgmt.adapters.persistence.entity.ReleaseTicketItemEntity;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseItemCommitJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseItemPullRequestJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseItemStatusChangeJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseManualItemJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseSummaryRow;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseTicketItemJpaRepository;
import com.evision.releasemgmt.domain.enrichment.CodeLinks;
import com.evision.releasemgmt.domain.enrichment.LinkedCommit;
import com.evision.releasemgmt.domain.enrichment.LinkedPullRequest;
import com.evision.releasemgmt.domain.enrichment.StatusChange;
import com.evision.releasemgmt.domain.enrichment.StatusHistory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.ManualItemNotFoundException;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseDuplicateException;
import com.evision.releasemgmt.domain.release.ReleaseFrozenException;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.release.ReleaseNotFoundException;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.domain.release.ReleaseSummary;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class ReleaseRepositoryAdapter implements ReleaseRepository {

    private final ReleaseJpaRepository jpa;
    private final ReleaseTicketItemJpaRepository itemJpa;
    private final ReleaseManualItemJpaRepository manualJpa;
    private final ReleaseItemPullRequestJpaRepository pullRequestJpa;
    private final ReleaseItemCommitJpaRepository commitJpa;
    private final ReleaseItemStatusChangeJpaRepository statusChangeJpa;

    public ReleaseRepositoryAdapter(ReleaseJpaRepository jpa,
                                     ReleaseTicketItemJpaRepository itemJpa,
                                     ReleaseManualItemJpaRepository manualJpa,
                                     ReleaseItemPullRequestJpaRepository pullRequestJpa,
                                     ReleaseItemCommitJpaRepository commitJpa,
                                     ReleaseItemStatusChangeJpaRepository statusChangeJpa) {
        this.jpa = jpa;
        this.itemJpa = itemJpa;
        this.manualJpa = manualJpa;
        this.pullRequestJpa = pullRequestJpa;
        this.commitJpa = commitJpa;
        this.statusChangeJpa = statusChangeJpa;
    }

    @Override
    @Transactional
    public Release create(Release release, List<ReleaseItem> items) {
        try {
            jpa.saveAndFlush(toEntity(release));
        } catch (DataIntegrityViolationException e) {
            // UNIQUE (name, version) - D-03-08. saveAndFlush forces the constraint to fire here
            // rather than at commit, where it would escape this catch.
            throw new ReleaseDuplicateException();
        }
        insertItems(release.id(), items);
        return load(release.id());
    }

    @Override
    public Optional<Release> findById(UUID id) {
        return jpa.findById(id).map(e -> toDomain(e, ticketItems(id), manualItems(id)));
    }

    @Override
    public List<ReleaseSummary> search(String query) {
        return jpa.search(escapeLikeWildcards(query)).stream().map(ReleaseRepositoryAdapter::toSummary).toList();
    }

    @Override
    public List<String> findTicketKeys(UUID releaseId) {
        return itemJpa.findByReleaseIdOrderByTicketKeyAsc(releaseId).stream()
                .map(ReleaseTicketItemEntity::getTicketKey)
                .toList();
    }

    @Override
    @Transactional
    public Release addItems(UUID releaseId, List<ReleaseItem> items) {
        ReleaseEntity entity = lockOpen(releaseId);
        // The lock means the key set cannot change beneath this read, so filtering here is what
        // stops two concurrent adds of the same key from surfacing
        // release_ticket_item_key_unique as a 500.
        Set<String> present = Set.copyOf(findTicketKeys(releaseId));
        List<ReleaseItem> toInsert = items.stream().filter(i -> !present.contains(i.ticketKey())).toList();
        // "Add tickets already on the release" is an explicitly supported idempotent no-op
        // (ReleaseAddResult.alreadyPresent) - it must not stamp items_changed_at, or it would
        // spuriously flip an up-to-date document to stale for content that never changed.
        if (!toInsert.isEmpty()) {
            insertItems(releaseId, toInsert);
            stampItemsChanged(entity);
            jpa.save(entity);
        }
        return load(releaseId);
    }

    @Override
    @Transactional
    public Release removeItem(UUID releaseId, String ticketKey) {
        ReleaseEntity entity = lockOpen(releaseId);
        // Same rule as addItems (WR-01): removing an absent ticket is a no-op and must not mark paperwork stale.
        if (itemJpa.deleteByReleaseIdAndTicketKey(releaseId, ticketKey) > 0) {
            stampItemsChanged(entity);
            jpa.save(entity);
        }
        return load(releaseId);
    }

    @Override
    @Transactional
    public Release replaceItemSnapshots(UUID releaseId, List<ReleaseItem> items, Instant refreshedAt) {
        ReleaseEntity release = lockOpen(releaseId);
        for (ReleaseItem item : items) {
            itemJpa.findByReleaseIdAndTicketKey(releaseId, item.ticketKey())
                    .ifPresent(entity -> overwriteSnapshot(entity, item.ticket()));
        }
        release.setLastRefreshedAt(refreshedAt);
        stampItemsChanged(release);
        jpa.save(release);
        return load(releaseId);
    }

    // FAILED results already carry the previous rows (CodeLinks.failed, D-06-12), so one replace path serves every
    // status. No HTTP happens here: callers fetch first and write once.
    @Override
    @Transactional
    public Release saveEnrichment(UUID releaseId, Map<String, CodeLinks> codeLinksByTicketKey) {
        ReleaseEntity release = lockOpen(releaseId);
        Map<String, CodeLinks> before = ticketItems(releaseId).stream()
                .collect(Collectors.toMap(ReleaseItem::ticketKey, ReleaseItem::codeLinks));
        boolean documentsChanged = false;
        for (ReleaseTicketItemEntity entity : itemJpa.findByReleaseIdOrderByTicketKeyAsc(releaseId)) {
            CodeLinks after = codeLinksByTicketKey.get(entity.getTicketKey());
            if (after == null) {
                continue;
            }
            documentsChanged |= !before.get(entity.getTicketKey()).sameInDocumentsAs(after);
            overwriteEnrichment(entity, after);
        }
        if (documentsChanged) {
            stampItemsChanged(release);
            jpa.save(release);
        }
        return load(releaseId);
    }

    private void overwriteEnrichment(ReleaseTicketItemEntity entity, CodeLinks links) {
        StatusHistory history = links.history();
        entity.setEnrichmentStatus(links.status());
        entity.setEnrichmentReason(links.reason());
        entity.setEnrichmentIncomplete(links.incomplete());
        entity.setEnrichmentAttemptedAt(links.attemptedAt());
        entity.setCommitsSource(links.commitsSource());
        entity.setBranches(links.branches());
        entity.setHistoryState(history.state());
        entity.setHistorySyncedUpdatedAt(history.syncedUpdatedAt());
        entity.setFirstDoneAt(history.firstDoneAt());
        entity.setReopenCount(history.reopenCount());
        itemJpa.save(entity);

        UUID itemId = entity.getId();
        pullRequestJpa.deleteByItemId(itemId);
        commitJpa.deleteByItemId(itemId);
        statusChangeJpa.deleteByItemId(itemId);
        pullRequestJpa.saveAll(links.pullRequests().stream().map(p -> toEntity(itemId, p)).toList());
        commitJpa.saveAll(links.commits().stream().map(c -> toEntity(itemId, c)).toList());
        statusChangeJpa.saveAll(history.changes().stream().map(c -> toEntity(itemId, c)).toList());
    }

    private static ReleaseItemPullRequestEntity toEntity(UUID itemId, LinkedPullRequest pr) {
        ReleaseItemPullRequestEntity entity = new ReleaseItemPullRequestEntity();
        entity.setId(UUID.randomUUID());
        entity.setItemId(itemId);
        entity.setRepositoryId(pr.repositoryId());
        entity.setPrId(pr.id());
        entity.setTitle(pr.title());
        entity.setStatus(pr.status());
        entity.setUrl(pr.url());
        entity.setSourceBranch(pr.sourceBranch());
        entity.setTargetBranch(pr.targetBranch());
        entity.setCountsTowardCommits(pr.countsTowardCommits());
        entity.setCommitsFetchedAt(pr.commitsFetchedAt());
        return entity;
    }

    private static ReleaseItemCommitEntity toEntity(UUID itemId, LinkedCommit commit) {
        ReleaseItemCommitEntity entity = new ReleaseItemCommitEntity();
        entity.setId(UUID.randomUUID());
        entity.setItemId(itemId);
        entity.setSha(commit.sha());
        entity.setMessage(commit.message());
        entity.setAuthorName(commit.author());
        entity.setAuthoredAt(commit.authoredAt());
        entity.setUrl(commit.url());
        return entity;
    }

    private static ReleaseItemStatusChangeEntity toEntity(UUID itemId, StatusChange change) {
        ReleaseItemStatusChangeEntity entity = new ReleaseItemStatusChangeEntity();
        entity.setId(UUID.randomUUID());
        entity.setItemId(itemId);
        entity.setChangedAt(change.at());
        entity.setFromStatusId(change.fromStatusId());
        entity.setToStatusId(change.toStatusId());
        entity.setFromCategory(change.fromCategory());
        entity.setToCategory(change.toCategory());
        return entity;
    }

    @Override
    @Transactional
    public Release freeze(UUID releaseId, String actor, Instant at) {
        ReleaseEntity entity = lockOpen(releaseId);
        entity.setStatus(ReleaseStatus.FROZEN);
        entity.setFrozenAt(at);
        entity.setFrozenBy(actor);
        jpa.save(entity);
        return load(releaseId);
    }

    @Override
    @Transactional
    public void delete(UUID releaseId) {
        lockOpen(releaseId);
        jpa.deleteById(releaseId);
    }

    @Override
    @Transactional
    public Release addManualItem(UUID releaseId, ManualItem item) {
        ReleaseEntity release = lockOpen(releaseId);
        ReleaseManualItemEntity entity = new ReleaseManualItemEntity();
        entity.setId(item.id());
        entity.setReleaseId(releaseId);
        entity.setTitle(item.title());
        entity.setDescription(item.description());
        entity.setCategory(item.category());
        entity.setAddedAt(item.addedAt());
        manualJpa.save(entity);
        stampItemsChanged(release);
        jpa.save(release);
        return load(releaseId);
    }

    @Override
    @Transactional
    public Release updateManualItem(UUID releaseId, ManualItem item) {
        ReleaseEntity release = lockOpen(releaseId);
        ReleaseManualItemEntity entity = manualJpa.findByReleaseIdAndId(releaseId, item.id())
                .orElseThrow(ManualItemNotFoundException::new);
        entity.setTitle(item.title());
        entity.setDescription(item.description());
        entity.setCategory(item.category());
        manualJpa.save(entity);
        stampItemsChanged(release);
        jpa.save(release);
        return load(releaseId);
    }

    @Override
    @Transactional
    public Release removeManualItem(UUID releaseId, UUID manualItemId) {
        ReleaseEntity release = lockOpen(releaseId);
        Optional<ReleaseManualItemEntity> item = manualJpa.findByReleaseIdAndId(releaseId, manualItemId);
        if (item.isPresent()) {
            manualJpa.delete(item.get());
            stampItemsChanged(release);
            jpa.save(release);
        }
        return load(releaseId);
    }

    // The service checks status too, but against a snapshot read in an earlier transaction with a
    // paginated Jira call in between. This is the check that actually holds: it runs inside the
    // mutating transaction and holds the row lock until commit, so a concurrent freeze either
    // lands before it (and is seen) or blocks until after it.
    private ReleaseEntity lockOpen(UUID releaseId) {
        ReleaseEntity entity = jpa.findByIdForUpdate(releaseId).orElseThrow(ReleaseNotFoundException::new);
        if (entity.getStatus() == ReleaseStatus.FROZEN) {
            throw new ReleaseFrozenException();
        }
        return entity;
    }

    private Release load(UUID id) {
        return findById(id).orElseThrow(ReleaseNotFoundException::new);
    }

    // D-05-09: this is what makes a REMOVAL visible to the staleness comparison. last_refreshed_at
    // covers refreshes and max(added_at) covers additions, but removing an item leaves no timestamp
    // anywhere - without this, deleting a ticket would leave its release notes reading "Up to date".
    private void stampItemsChanged(ReleaseEntity entity) {
        entity.setItemsChangedAt(Instant.now());
    }

    // The bound :query parameter can never alter SQL structure, but a literal '%' or '_' in it
    // still acts as an ILIKE wildcard once concatenated into the pattern. Escaping with Postgres'
    // default backslash escape character keeps the search box's own wildcard characters literal.
    private static String escapeLikeWildcards(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private void insertItems(UUID releaseId, List<ReleaseItem> items) {
        List<ReleaseTicketItemEntity> entities = items.stream()
                .map(item -> toEntity(releaseId, item))
                .toList();
        itemJpa.saveAll(entities);
    }

    // One query per child table for the whole release, grouped by item: never a query per item.
    private List<ReleaseItem> ticketItems(UUID releaseId) {
        List<ReleaseTicketItemEntity> entities = itemJpa.findByReleaseIdOrderByTicketKeyAsc(releaseId);
        if (entities.isEmpty()) {
            return List.of();
        }
        List<UUID> ids = entities.stream().map(ReleaseTicketItemEntity::getId).toList();
        Map<UUID, List<ReleaseItemPullRequestEntity>> prs = pullRequestJpa.findByItemIdIn(ids).stream()
                .collect(Collectors.groupingBy(ReleaseItemPullRequestEntity::getItemId));
        Map<UUID, List<ReleaseItemCommitEntity>> commits = commitJpa.findByItemIdInOrderByAuthoredAtAscShaAsc(ids)
                .stream().collect(Collectors.groupingBy(ReleaseItemCommitEntity::getItemId));
        Map<UUID, List<ReleaseItemStatusChangeEntity>> changes = statusChangeJpa
                .findByItemIdInOrderByChangedAtAsc(ids).stream()
                .collect(Collectors.groupingBy(ReleaseItemStatusChangeEntity::getItemId));
        return entities.stream()
                .map(e -> toDomain(e, toCodeLinks(e, prs.getOrDefault(e.getId(), List.of()),
                        commits.getOrDefault(e.getId(), List.of()), changes.getOrDefault(e.getId(), List.of()))))
                .toList();
    }

    private List<ManualItem> manualItems(UUID releaseId) {
        return manualJpa.findByReleaseIdOrderByTitleAsc(releaseId).stream()
                .map(ReleaseRepositoryAdapter::toDomain)
                .toList();
    }

    private static void overwriteSnapshot(ReleaseTicketItemEntity entity, TicketDetail ticket) {
        entity.setSummary(ticket.summary());
        entity.setIssueType(ticket.issueType());
        entity.setStatus(ticket.status());
        entity.setResolution(ticket.resolution());
        entity.setAssignee(ticket.assignee());
        entity.setReporter(ticket.reporter());
        entity.setFixVersions(ticket.fixVersions());
        entity.setComponent(ticket.component());
        entity.setDescription(ticket.description());
        entity.setComments(ticket.comments());
        entity.setCommentTotal(ticket.commentTotal());
        entity.setJiraIssueId(ticket.jiraIssueId());
        entity.setJiraUpdatedAt(ticket.updatedAt());
        entity.setJiraStatusCategory(ticket.statusCategory());
        entity.setStoryPoints(ticket.storyPoints());
        entity.setStoryPointsRead(ticket.storyPointsRead());
    }

    private static ReleaseEntity toEntity(Release release) {
        ReleaseEntity entity = new ReleaseEntity();
        entity.setId(release.id());
        entity.setName(release.name());
        entity.setVersion(release.version());
        entity.setStatus(release.status());
        entity.setJiraProjectKey(release.jiraProjectKey());
        entity.setCreatedAt(release.createdAt());
        entity.setCreatedBy(release.createdBy());
        entity.setFrozenAt(release.frozenAt());
        entity.setFrozenBy(release.frozenBy());
        entity.setLastRefreshedAt(release.lastRefreshedAt());
        // The initial item set is established at creation time, so it counts as "changed" here too -
        // this is the "set it at create" case D-05-09 calls out, distinct from the six mutation call
        // sites above that share the stampItemsChanged helper.
        entity.setItemsChangedAt(release.createdAt());
        return entity;
    }

    private static ReleaseTicketItemEntity toEntity(UUID releaseId, ReleaseItem item) {
        TicketDetail ticket = item.ticket();
        ReleaseTicketItemEntity entity = new ReleaseTicketItemEntity();
        entity.setId(UUID.randomUUID());
        entity.setReleaseId(releaseId);
        entity.setTicketKey(ticket.key());
        entity.setAddedAt(item.addedAt());
        overwriteSnapshot(entity, ticket);
        return entity;
    }

    private static Release toDomain(ReleaseEntity e, List<ReleaseItem> items, List<ManualItem> manualItems) {
        return new Release(e.getId(), e.getName(), e.getVersion(), e.getStatus(), e.getJiraProjectKey(),
                e.getCreatedAt(), e.getCreatedBy(), e.getFrozenAt(), e.getFrozenBy(), e.getLastRefreshedAt(),
                e.getItemsChangedAt(), items, manualItems);
    }

    private static ReleaseItem toDomain(ReleaseTicketItemEntity e, CodeLinks codeLinks) {
        TicketDetail ticket = new TicketDetail(e.getTicketKey(), e.getSummary(), e.getIssueType(), e.getStatus(),
                e.getResolution(), e.getAssignee(), e.getReporter(), e.getFixVersions(), e.getComponent(),
                e.getDescription(), e.getComments(), e.getCommentTotal(), e.getJiraIssueId(), e.getJiraUpdatedAt(),
                e.getJiraStatusCategory(), e.getStoryPoints(), e.isStoryPointsRead());
        return new ReleaseItem(ticket, e.getAddedAt(), codeLinks);
    }

    private static CodeLinks toCodeLinks(ReleaseTicketItemEntity e, List<ReleaseItemPullRequestEntity> prs,
                                         List<ReleaseItemCommitEntity> commits,
                                         List<ReleaseItemStatusChangeEntity> changes) {
        List<LinkedPullRequest> pullRequests = prs.stream()
                .map(p -> new LinkedPullRequest(p.getRepositoryId(), p.getPrId(), p.getTitle(), p.getStatus(),
                        p.getUrl(), p.getSourceBranch(), p.getTargetBranch(), p.isCountsTowardCommits(),
                        p.getCommitsFetchedAt()))
                .toList();
        List<LinkedCommit> linkedCommits = commits.stream()
                .map(c -> new LinkedCommit(c.getSha(), c.getMessage(), c.getAuthorName(), c.getAuthoredAt(),
                        c.getUrl()))
                .toList();
        List<StatusChange> statusChanges = changes.stream()
                .map(c -> new StatusChange(c.getChangedAt(), c.getFromStatusId(), c.getToStatusId(),
                        c.getFromCategory(), c.getToCategory()))
                .toList();
        StatusHistory history = new StatusHistory(e.getHistoryState(), e.getHistorySyncedUpdatedAt(),
                e.getFirstDoneAt(), e.getReopenCount(), statusChanges);
        return new CodeLinks(e.getEnrichmentStatus(), e.getEnrichmentReason(), e.isEnrichmentIncomplete(),
                e.getEnrichmentAttemptedAt(), e.getCommitsSource(), pullRequests, linkedCommits, e.getBranches(),
                history);
    }

    private static ManualItem toDomain(ReleaseManualItemEntity e) {
        return new ManualItem(e.getId(), e.getTitle(), e.getDescription(), e.getCategory(), e.getAddedAt());
    }

    private static ReleaseSummary toSummary(ReleaseSummaryRow row) {
        return new ReleaseSummary(row.getId(), row.getName(), row.getVersion(),
                ReleaseStatus.valueOf(row.getStatus()), row.getJiraProjectKey(), row.getCreatedAt(),
                row.getCreatedBy(), row.getFrozenAt(), row.getFrozenBy(), row.getLastRefreshedAt(),
                row.getItemsChangedAt(), Math.toIntExact(row.getItemCount()));
    }
}
