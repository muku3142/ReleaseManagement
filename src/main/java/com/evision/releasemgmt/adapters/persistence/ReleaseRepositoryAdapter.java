package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.ReleaseEntity;
import com.evision.releasemgmt.adapters.persistence.entity.ReleaseManualItemEntity;
import com.evision.releasemgmt.adapters.persistence.entity.ReleaseTicketItemEntity;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseManualItemJpaRepository;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseSummaryRow;
import com.evision.releasemgmt.adapters.persistence.repository.ReleaseTicketItemJpaRepository;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class ReleaseRepositoryAdapter implements ReleaseRepository {

    private final ReleaseJpaRepository jpa;
    private final ReleaseTicketItemJpaRepository itemJpa;
    private final ReleaseManualItemJpaRepository manualJpa;

    public ReleaseRepositoryAdapter(ReleaseJpaRepository jpa,
                                     ReleaseTicketItemJpaRepository itemJpa,
                                     ReleaseManualItemJpaRepository manualJpa) {
        this.jpa = jpa;
        this.itemJpa = itemJpa;
        this.manualJpa = manualJpa;
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
        lockOpen(releaseId);
        // The lock means the key set cannot change beneath this read, so filtering here is what
        // stops two concurrent adds of the same key from surfacing
        // release_ticket_item_key_unique as a 500.
        Set<String> present = Set.copyOf(findTicketKeys(releaseId));
        insertItems(releaseId, items.stream().filter(i -> !present.contains(i.ticketKey())).toList());
        return load(releaseId);
    }

    @Override
    @Transactional
    public Release removeItem(UUID releaseId, String ticketKey) {
        lockOpen(releaseId);
        itemJpa.deleteByReleaseIdAndTicketKey(releaseId, ticketKey);
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
        jpa.save(release);
        return load(releaseId);
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
        lockOpen(releaseId);
        ReleaseManualItemEntity entity = new ReleaseManualItemEntity();
        entity.setId(item.id());
        entity.setReleaseId(releaseId);
        entity.setTitle(item.title());
        entity.setDescription(item.description());
        entity.setCategory(item.category());
        entity.setAddedAt(item.addedAt());
        manualJpa.save(entity);
        return load(releaseId);
    }

    @Override
    @Transactional
    public Release updateManualItem(UUID releaseId, ManualItem item) {
        lockOpen(releaseId);
        ReleaseManualItemEntity entity = manualJpa.findByReleaseIdAndId(releaseId, item.id())
                .orElseThrow(ManualItemNotFoundException::new);
        entity.setTitle(item.title());
        entity.setDescription(item.description());
        entity.setCategory(item.category());
        manualJpa.save(entity);
        return load(releaseId);
    }

    @Override
    @Transactional
    public Release removeManualItem(UUID releaseId, UUID manualItemId) {
        lockOpen(releaseId);
        manualJpa.findByReleaseIdAndId(releaseId, manualItemId).ifPresent(manualJpa::delete);
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

    private List<ReleaseItem> ticketItems(UUID releaseId) {
        return itemJpa.findByReleaseIdOrderByTicketKeyAsc(releaseId).stream()
                .map(ReleaseRepositoryAdapter::toDomain)
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
                items, manualItems);
    }

    private static ReleaseItem toDomain(ReleaseTicketItemEntity e) {
        TicketDetail ticket = new TicketDetail(e.getTicketKey(), e.getSummary(), e.getIssueType(), e.getStatus(),
                e.getResolution(), e.getAssignee(), e.getReporter(), e.getFixVersions(), e.getComponent(),
                e.getDescription(), e.getComments(), e.getCommentTotal());
        return new ReleaseItem(ticket, e.getAddedAt());
    }

    private static ManualItem toDomain(ReleaseManualItemEntity e) {
        return new ManualItem(e.getId(), e.getTitle(), e.getDescription(), e.getCategory(), e.getAddedAt());
    }

    private static ReleaseSummary toSummary(ReleaseSummaryRow row) {
        return new ReleaseSummary(row.getId(), row.getName(), row.getVersion(),
                ReleaseStatus.valueOf(row.getStatus()), row.getJiraProjectKey(), row.getCreatedAt(),
                row.getCreatedBy(), row.getFrozenAt(), row.getFrozenBy(), row.getLastRefreshedAt(),
                Math.toIntExact(row.getItemCount()));
    }
}
