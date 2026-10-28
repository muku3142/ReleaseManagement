package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.adapters.persistence.entity.BriefingRunEntity;
import com.evision.releasemgmt.adapters.persistence.repository.BriefingRunJpaRepository;
import com.evision.releasemgmt.domain.briefing.AppBriefingRun;
import com.evision.releasemgmt.domain.briefing.BriefingContent;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.domain.briefing.BriefingTask;
import com.evision.releasemgmt.domain.briefing.BriefingTrigger;
import com.evision.releasemgmt.ports.BriefingRunRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class BriefingRunRepositoryAdapter implements BriefingRunRepository {

    private static final TypeReference<List<StoredSection>> SECTIONS = new TypeReference<>() { };

    private record StoredItem(String key, String text) { }

    private record StoredSection(String title, List<StoredItem> items) { }

    private final BriefingRunJpaRepository jpa;
    private final ObjectMapper mapper;

    public BriefingRunRepositoryAdapter(BriefingRunJpaRepository jpa, ObjectMapper mapper) {
        this.jpa = jpa;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public boolean insert(AppBriefingRun run) {
        return jpa.insertIfAbsent(run.id(), run.task().id(), run.productId(), run.slotAt(), run.trigger().name(),
                run.status().wire(), run.reason(), run.attention(), run.headline(), run.summary(),
                toJson(run.sections()), run.startedAt(), run.finishedAt()) == 1;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsForSlot(BriefingTask task, UUID productId, Instant slotAt) {
        return jpa.existsByTaskAndProductIdAndSlotAt(task.id(), productId, slotAt);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Instant> lastSuccessfulFinishedAt(BriefingTask task, UUID productId) {
        return jpa.lastSuccessfulFinishedAt(task.id(), productId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AppBriefingRun> findByTask(BriefingTask task) {
        return jpa.findByTaskOrderByFinishedAtDesc(task.id()).stream().map(this::toDomain).toList();
    }

    @Override
    @Transactional
    public void prune(BriefingTask task, UUID productId, int keepRuns) {
        jpa.prune(task.id(), productId, keepRuns);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<BriefingStatus> latestStatus() {
        return jpa.latestStatus().flatMap(BriefingStatus::fromWire);
    }

    private String toJson(List<BriefingContent.Section> sections) {
        return mapper.writeValueAsString(sections.stream()
                .map(s -> new StoredSection(s.title(),
                        s.items().stream().map(i -> new StoredItem(i.issueKey(), i.text())).toList()))
                .toList());
    }

    private AppBriefingRun toDomain(BriefingRunEntity e) {
        List<BriefingContent.Section> sections = mapper.readValue(e.getBody(), SECTIONS).stream()
                .map(s -> new BriefingContent.Section(s.title(),
                        s.items().stream().map(i -> new BriefingContent.Item(i.key(), i.text())).toList()))
                .toList();
        return new AppBriefingRun(e.getId(), BriefingTask.fromId(e.getTask()).orElseThrow(), e.getProductId(),
                e.getSlotAt(), BriefingTrigger.valueOf(e.getRunTrigger()),
                BriefingStatus.fromWire(e.getStatus()).orElseThrow(), e.getReason(), e.getAttention(),
                e.getHeadline(), e.getSummary(), sections, e.getStartedAt(), e.getFinishedAt());
    }
}
