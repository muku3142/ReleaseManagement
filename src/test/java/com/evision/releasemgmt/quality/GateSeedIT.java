package com.evision.releasemgmt.quality;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.release.ManualItem;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseItem;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.PromotionRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import com.evision.releasemgmt.ports.TemplateRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class GateSeedIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private ReleaseRepository releases;

    @Autowired
    private PromotionRepository promotions;

    @Autowired
    private DocumentSnapshotRepository snapshots;

    @Autowired
    private TemplateRepository templates;

    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void clear() {
        jdbc.execute("TRUNCATE release CASCADE");
        jdbc.execute("TRUNCATE template CASCADE");
    }

    private GateSeed.Seeded seed() {
        return GateSeed.seed(releases, promotions, snapshots, templates);
    }

    /** Everything the contract fixes, with the generated ids left out. */
    private List<String> fingerprint(GateSeed.Seeded seeded) {
        List<String> lines = new ArrayList<>();
        seeded.ids().keySet().stream().sorted().forEach(name -> {
            UUID id = seeded.releaseId(name);
            Release release = releases.findById(id).orElseThrow();
            lines.add(name + " " + release.status() + " " + release.createdAt() + " " + release.frozenAt());
            release.items().forEach(i -> lines.add("  ticket " + i.ticketKey() + " " + i.addedAt() + " "
                    + i.codeLinks().status() + " " + i.codeLinks().commits().size()));
            release.manualItems().forEach(m -> lines.add("  manual " + m.title() + " " + m.addedAt()));
            PromotionHistory history = promotions.load(id);
            history.entries().forEach(e -> lines.add("  promotion "
                    + history.stages().stream().filter(s -> s.id().equals(e.stageId())).findFirst().orElseThrow().name()
                    + " " + e.outcome() + " " + e.deployedAt() + " " + e.recordedAt()));
            for (TemplateDocumentType type : TemplateDocumentType.values()) {
                snapshots.findAllRevisions(id, type).forEach(s -> lines.add("  document " + type + " rev "
                        + s.revision() + " " + s.generatedAt()));
            }
        });
        return lines;
    }

    @Test
    void seedingTwiceIntoEmptyDatabasesGivesTheSameDataset() {
        List<String> first = fingerprint(seed());
        clear();

        List<String> second = fingerprint(seed());

        assertThat(second).isEqualTo(first).isNotEmpty();
    }

    @Test
    void releaseIdResolvesEverySeededReleaseAndRejectsUnknownNames() {
        GateSeed.Seeded seeded = seed();

        assertThat(GateSeed.RELEASE_NAMES).allSatisfy(name ->
                assertThat(releases.findById(seeded.releaseId(name))).isPresent());
        assertThatThrownBy(() -> seeded.releaseId("Atlas 9.9.9")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theSeedHoldsTheFixedTicketSetAndTheFourCanaries() {
        GateSeed.Seeded seeded = seed();
        List<Release> all = GateSeed.RELEASE_NAMES.stream()
                .map(name -> releases.findById(seeded.releaseId(name)).orElseThrow()).toList();

        List<ReleaseItem> items = all.stream().flatMap(r -> r.items().stream()).toList();
        assertThat(items.stream().map(ReleaseItem::ticketKey).collect(Collectors.toSet()))
                .isEqualTo(GateSeed.TICKET_KEYS);
        assertThat(all.stream().flatMap(r -> r.manualItems().stream())).hasSize(3);
        assertThat(items.stream().filter(i -> i.ticket().description().contains("PINEAPPLE-7"))).hasSize(1);
        assertThat(items.stream().filter(i -> i.ticket().comments().stream().anyMatch(c -> c.contains("MANGO-3"))))
                .hasSize(1);
        assertThat(items.stream().filter(i -> i.ticket().summary().contains("PAPAYA-5"))).hasSize(1);
        assertThat(all.stream().flatMap(r -> r.manualItems().stream())
                .filter((ManualItem m) -> m.description().contains("KIWI-9"))).hasSize(1);
    }

    @Test
    void ticketAtl105BelongsToBothAtlasReleases() {
        GateSeed.Seeded seeded = seed();

        assertThat(releases.releasesContaining("ATL-105")).extracting(r -> r.id())
                .containsExactlyInAnyOrder(seeded.releaseId(GateSeed.ATLAS_42), seeded.releaseId(GateSeed.ATLAS_43));
    }

    @Test
    void promotionAndDocumentStatesMatchTheCaseLabels() {
        GateSeed.Seeded seeded = seed();

        assertThat(currentStage(seeded, GateSeed.ATLAS_42)).isEqualTo("PROD");
        assertThat(currentStage(seeded, GateSeed.ATLAS_43)).isEqualTo("UAT");
        assertThat(currentStage(seeded, GateSeed.BOREALIS)).isEqualTo("UAT");
        assertThat(currentStage(seeded, GateSeed.DRACO)).isEqualTo("TEST");
        assertThat(promotions.load(seeded.releaseId(GateSeed.CYGNUS)).entries()).isEmpty();
        assertThat(snapshots.findAllRevisions(seeded.releaseId(GateSeed.ATLAS_42), TemplateDocumentType.RELEASE_NOTES))
                .extracting(DocumentSnapshot::revision).containsExactly(2, 1);
        assertThat(snapshots.findAllRevisions(seeded.releaseId(GateSeed.BOREALIS), TemplateDocumentType.RUNBOOK))
                .isEmpty();
        assertThat(releases.findById(seeded.releaseId(GateSeed.CYGNUS)).orElseThrow().itemsChangedAt())
                .isAfter(snapshots.findLatest(seeded.releaseId(GateSeed.CYGNUS), TemplateDocumentType.RELEASE_NOTES)
                        .orElseThrow().generatedAt());
    }

    @Test
    void aNonEmptyDatabaseIsRefused() {
        seed();

        assertThatThrownBy(this::seed).isInstanceOf(IllegalStateException.class);
    }

    private String currentStage(GateSeed.Seeded seeded, String release) {
        return promotions.load(seeded.releaseId(release)).currentStage().orElseThrow().name();
    }
}
