package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.adapters.web.dto.DocumentRevisionResponse;
import com.evision.releasemgmt.adapters.web.dto.TemplateSummaryResponse;
import com.evision.releasemgmt.application.DocumentGenerationService;
import com.evision.releasemgmt.application.TemplateService;
import com.evision.releasemgmt.domain.document.ChosenTemplateNotFoundException;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.document.TemplateTypeMismatchException;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.GroupingDimension;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// D-05.4-03/D-05.4-04 and CONTEXT <specifics>: one user's default must not reach another user's
// generation or picker, and deleting a default's template must fall back to the active one. The
// slice tests mock the template port, so only a real Postgres proves the keyed lookup and the
// ON DELETE CASCADE actually behave this way.
@SpringBootTest
@Testcontainers
class TemplateDefaultResolutionIT {

    private static final String ACCOUNT_A = "557058:resolution-a";
    private static final String ACCOUNT_B = "557058:resolution-b";
    private static final Principal PRINCIPAL_A = () -> ACCOUNT_A;
    private static final Principal PRINCIPAL_B = () -> ACCOUNT_B;

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private DocumentGenerationService generationService;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private TemplateController templateController;

    @Autowired
    private DocumentController documentController;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearAll() {
        jdbcTemplate.execute("TRUNCATE release CASCADE");
        jdbcTemplate.execute("DELETE FROM template WHERE created_by <> 'application-config-seed'");
        jdbcTemplate.execute("DELETE FROM template_user_default");
    }

    private Release newRelease() {
        return releaseRepository.create(
                Release.create("Resolution IT", "1.0", "CNV", ACCOUNT_A, Instant.now()), List.of());
    }

    private Template newTemplate(TemplateDocumentType type) {
        Template created = templateService.create("Resolution " + UUID.randomUUID(), type, ACCOUNT_A);
        return templateService.saveContent(created.id(), "# {{release.name}}", ChromeFields.empty(),
                GroupingDimension.ISSUE_TYPE, ACCOUNT_A);
    }

    private Template seededActive(TemplateDocumentType type) {
        return templateService.list().stream()
                .filter(t -> t.documentType() == type && t.active())
                .findFirst().orElseThrow();
    }

    private int snapshotCount(UUID releaseId) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM document_snapshot WHERE release_id = ?",
                Integer.class, releaseId);
    }

    private DocumentSnapshot generate(Release release, UUID templateId, String actor) {
        return generationService.generate(release.id(), TemplateDocumentType.RELEASE_NOTES, templateId, actor);
    }

    @Test
    void aDefaultIsPersonal() {
        Template active = seededActive(TemplateDocumentType.RELEASE_NOTES);
        Template x = newTemplate(TemplateDocumentType.RELEASE_NOTES);
        templateService.setMyDefault(x.id(), ACCOUNT_A);
        Release release = newRelease();

        assertThat(generate(release, null, ACCOUNT_A).templateId()).isEqualTo(x.id());
        assertThat(generate(release, null, ACCOUNT_B).templateId()).isEqualTo(active.id());

        assertThat(templateController.list(PRINCIPAL_B).templates())
                .noneMatch(TemplateSummaryResponse::myDefault);
        assertThat(templateController.list(PRINCIPAL_A).templates())
                .filteredOn(TemplateSummaryResponse::myDefault)
                .extracting(TemplateSummaryResponse::id).containsExactly(x.id());
        assertThat(templateService.get(x.id()).active()).isFalse();
        assertThat(templateService.get(active.id()).active()).isTrue();
    }

    @Test
    void deletingTheDefaultFallsBackToActive() {
        Template active = seededActive(TemplateDocumentType.RELEASE_NOTES);
        Template y = newTemplate(TemplateDocumentType.RELEASE_NOTES);
        templateService.setMyDefault(y.id(), ACCOUNT_A);

        templateService.delete(y.id());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT count(*) FROM template_user_default WHERE account_id = ?", Integer.class, ACCOUNT_A))
                .isZero();
        assertThat(generate(newRelease(), null, ACCOUNT_A).templateId()).isEqualTo(active.id());
    }

    @Test
    void snapshotRecordsTheTemplateActuallyUsed() {
        Template active = seededActive(TemplateDocumentType.RELEASE_NOTES);
        Template z = newTemplate(TemplateDocumentType.RELEASE_NOTES);
        Template x = newTemplate(TemplateDocumentType.RELEASE_NOTES);
        templateService.setMyDefault(x.id(), ACCOUNT_A);
        Release release = newRelease();

        DocumentSnapshot explicit = generate(release, z.id(), ACCOUNT_A);
        DocumentSnapshot personal = generate(release, null, ACCOUNT_A);
        DocumentSnapshot global = generate(release, null, ACCOUNT_B);

        assertThat(List.of(explicit, personal, global)).extracting(DocumentSnapshot::templateId)
                .containsExactly(z.id(), x.id(), active.id());
        assertThat(List.of(explicit, personal, global)).extracting(DocumentSnapshot::templateName)
                .containsExactly(z.name(), x.name(), active.name());

        List<DocumentRevisionResponse> revisions = documentController
                .revisions(release.id(), TemplateDocumentType.RELEASE_NOTES).revisions();
        // newest first
        assertThat(revisions).extracting(DocumentRevisionResponse::templateName)
                .containsExactly(active.name(), x.name(), z.name());
    }

    @Test
    void crossTypeIsRejectedAtGenerate() {
        Template runbook = seededActive(TemplateDocumentType.RUNBOOK);
        Release release = newRelease();
        int before = snapshotCount(release.id());

        assertThatThrownBy(() -> generate(release, runbook.id(), ACCOUNT_A))
                .isInstanceOf(TemplateTypeMismatchException.class);

        assertThat(snapshotCount(release.id())).isEqualTo(before);
    }

    @Test
    void unknownChosenTemplateIsRejected() {
        Release release = newRelease();

        assertThatThrownBy(() -> generate(release, UUID.randomUUID(), ACCOUNT_A))
                .isInstanceOf(ChosenTemplateNotFoundException.class);

        assertThat(snapshotCount(release.id())).isZero();
    }

    @Test
    void setDefaultDerivesTypeFromTemplate() {
        Template x = newTemplate(TemplateDocumentType.RELEASE_NOTES);
        Template runbook = seededActive(TemplateDocumentType.RUNBOOK);
        templateService.setMyDefault(x.id(), ACCOUNT_A);

        templateService.setMyDefault(runbook.id(), ACCOUNT_A);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT document_type FROM template_user_default WHERE account_id = ? AND template_id = ?",
                String.class, ACCOUNT_A, runbook.id())).isEqualTo("RUNBOOK");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT template_id FROM template_user_default WHERE account_id = ? "
                        + "AND document_type = 'RELEASE_NOTES'", UUID.class, ACCOUNT_A)).isEqualTo(x.id());
    }
}
