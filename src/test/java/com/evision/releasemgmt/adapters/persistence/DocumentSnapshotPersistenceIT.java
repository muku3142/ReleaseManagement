package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.LogoMetadata;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
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

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class DocumentSnapshotPersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private DocumentSnapshotRepository repository;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearAll() {
        jdbcTemplate.execute("TRUNCATE release CASCADE");
        jdbcTemplate.execute("TRUNCATE template CASCADE");
    }

    private static ChromeFields chrome() {
        return new ChromeFields(true, "Cover Title", "Cover Subtitle", "Header", "Footer", true, 55);
    }

    private Release release(String name) {
        Release release = Release.create(name, "1.0", "CNV", "rm@acme.com", Instant.now());
        return releaseRepository.create(release, List.of());
    }

    private static DocumentSnapshot snapshot(UUID releaseId, TemplateDocumentType type, int revision,
                                             UUID templateId) {
        return DocumentSnapshot.create(releaseId, type, revision, "# Body rev " + revision, templateId,
                "Release Notes A", "Body {{release.name}}", chrome(), "rm@acme.com", Instant.now());
    }

    private static LogoImage logo() {
        LogoMetadata metadata = new LogoMetadata("image/png", "logo.png", 4, 10, 10,
                Instant.now().truncatedTo(ChronoUnit.MICROS));
        return new LogoImage(new byte[] {1, 2, 3, 4}, metadata);
    }

    @Test
    void createStoresEveryCopiedFieldIncludingResolvedChrome() {
        Release release = release("Snapshot Fields");
        UUID templateId = UUID.randomUUID();

        DocumentSnapshot saved = repository.create(
                snapshot(release.id(), TemplateDocumentType.RELEASE_NOTES, 1, templateId), null);

        DocumentSnapshot loaded = repository.findByRevision(release.id(),
                TemplateDocumentType.RELEASE_NOTES, 1).orElseThrow();
        assertThat(loaded.id()).isEqualTo(saved.id());
        assertThat(loaded.releaseId()).isEqualTo(release.id());
        assertThat(loaded.documentType()).isEqualTo(TemplateDocumentType.RELEASE_NOTES);
        assertThat(loaded.revision()).isEqualTo(1);
        assertThat(loaded.markdown()).isEqualTo("# Body rev 1");
        assertThat(loaded.templateId()).isEqualTo(templateId);
        assertThat(loaded.templateName()).isEqualTo("Release Notes A");
        assertThat(loaded.templateBody()).isEqualTo("Body {{release.name}}");
        assertThat(loaded.chrome().coverEnabled()).isTrue();
        assertThat(loaded.chrome().coverTitle()).isEqualTo("Cover Title");
        assertThat(loaded.chrome().coverSubtitle()).isEqualTo("Cover Subtitle");
        assertThat(loaded.chrome().headerText()).isEqualTo("Header");
        assertThat(loaded.chrome().footerText()).isEqualTo("Footer");
        assertThat(loaded.chrome().pageNumbersEnabled()).isTrue();
        // 55, not 30: logo_render_height_mm carries a DEFAULT 30, so a fixture using 30 would still
        // pass with the mapper's setter missing entirely. Only a non-default value proves the
        // round-trip.
        assertThat(loaded.chrome().logoRenderHeightMm()).isEqualTo(55);
        assertThat(loaded.generatedBy()).isEqualTo("rm@acme.com");
        assertThat(loaded.generatedAt()).isNotNull();
    }

    @Test
    void regenerationAppendsRevisionTwoAndLeavesRevisionOneIntact() {
        Release release = release("Revisioning");
        repository.create(snapshot(release.id(), TemplateDocumentType.RELEASE_NOTES, 1, null), null);

        repository.create(snapshot(release.id(), TemplateDocumentType.RELEASE_NOTES, 2, null), null);

        DocumentSnapshot revisionOne = repository.findByRevision(release.id(),
                TemplateDocumentType.RELEASE_NOTES, 1).orElseThrow();
        assertThat(revisionOne.markdown()).isEqualTo("# Body rev 1");

        DocumentSnapshot latest = repository.findLatest(release.id(),
                TemplateDocumentType.RELEASE_NOTES).orElseThrow();
        assertThat(latest.revision()).isEqualTo(2);
        assertThat(latest.markdown()).isEqualTo("# Body rev 2");
    }

    @Test
    void nextRevisionStartsAtOneAndIncrementsPerDocumentType() {
        Release release = release("Next Revision");

        assertThat(repository.nextRevision(release.id(), TemplateDocumentType.RELEASE_NOTES)).isEqualTo(1);
        assertThat(repository.nextRevision(release.id(), TemplateDocumentType.RUNBOOK)).isEqualTo(1);

        repository.create(snapshot(release.id(), TemplateDocumentType.RELEASE_NOTES, 1, null), null);

        assertThat(repository.nextRevision(release.id(), TemplateDocumentType.RELEASE_NOTES)).isEqualTo(2);
        assertThat(repository.nextRevision(release.id(), TemplateDocumentType.RUNBOOK)).isEqualTo(1);
    }

    @Test
    void duplicateRevisionIsRejectedByTheDatabase() {
        Release release = release("Duplicate Revision");
        repository.create(snapshot(release.id(), TemplateDocumentType.RELEASE_NOTES, 1, null), null);

        assertThatThrownBy(() ->
                repository.create(snapshot(release.id(), TemplateDocumentType.RELEASE_NOTES, 1, null), null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void snapshotAndItsLogoSurviveDeletingTheSourceTemplate() {
        Release release = release("Survives Template Deletion");
        Template base = Template.create("With Logo", TemplateDocumentType.RELEASE_NOTES, "rm@acme.com",
                Instant.now());
        Template template = templateRepository.create(new Template(base.id(), base.name(),
                base.documentType(), "Body {{release.name}}", base.active(), chrome(),
                base.groupingDimension(), null, List.of(),
                base.createdAt(), base.createdBy(), base.updatedAt(), base.updatedBy()));
        templateRepository.saveLogo(template.id(), logo());

        DocumentSnapshot toSave = DocumentSnapshot.create(release.id(), TemplateDocumentType.RELEASE_NOTES,
                1, "# Body rev 1", template.id(), template.name(), template.body(), chrome(),
                "rm@acme.com", Instant.now());
        DocumentSnapshot saved = repository.create(toSave, templateRepository.findLogo(template.id())
                .orElseThrow());

        templateRepository.delete(template.id());

        DocumentSnapshot reloaded = repository.findByRevision(release.id(),
                TemplateDocumentType.RELEASE_NOTES, 1).orElseThrow();
        assertThat(reloaded.id()).isEqualTo(saved.id());
        assertThat(reloaded.markdown()).isEqualTo("# Body rev 1");
        assertThat(reloaded.templateName()).isEqualTo("With Logo");
        assertThat(reloaded.templateBody()).isEqualTo("Body {{release.name}}");

        LogoImage reloadedLogo = repository.findLogo(saved.id()).orElseThrow();
        assertThat(reloadedLogo.bytes()).containsExactly(1, 2, 3, 4);
        assertThat(reloadedLogo.metadata().width()).isEqualTo(10);
        assertThat(reloadedLogo.metadata().height()).isEqualTo(10);
    }

    @Test
    void deletingTheReleaseRemovesItsSnapshots() {
        Release release = release("Cascade Delete");
        repository.create(snapshot(release.id(), TemplateDocumentType.RELEASE_NOTES, 1, null), null);

        releaseRepository.delete(release.id());

        assertThat(repository.findAllRevisions(release.id(), TemplateDocumentType.RELEASE_NOTES)).isEmpty();
    }

    @Test
    void findAllRevisionsReturnsNewestFirst() {
        Release release = release("Revision History");
        repository.create(snapshot(release.id(), TemplateDocumentType.RELEASE_NOTES, 1, null), null);
        repository.create(snapshot(release.id(), TemplateDocumentType.RELEASE_NOTES, 2, null), null);
        repository.create(snapshot(release.id(), TemplateDocumentType.RELEASE_NOTES, 3, null), null);

        List<DocumentSnapshot> revisions = repository.findAllRevisions(release.id(),
                TemplateDocumentType.RELEASE_NOTES);

        assertThat(revisions).extracting(DocumentSnapshot::revision).containsExactly(3, 2, 1);
    }

    @Test
    void findLatestForAllReleasesReturnsOneRowPerReleaseAndType() {
        Release releaseA = release("Readiness A");
        Release releaseB = release("Readiness B");
        for (Release release : List.of(releaseA, releaseB)) {
            for (TemplateDocumentType type : List.of(TemplateDocumentType.RELEASE_NOTES,
                    TemplateDocumentType.RUNBOOK)) {
                repository.create(snapshot(release.id(), type, 1, null), null);
                repository.create(snapshot(release.id(), type, 2, null), null);
            }
        }

        List<DocumentSnapshot> latest = repository.findLatestForAllReleases();

        assertThat(latest).hasSize(4);
        assertThat(latest).allMatch(s -> s.revision() == 2);
    }
}
