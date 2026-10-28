package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.GroupingDimension;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.LogoMetadata;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.domain.template.TemplateDuplicateException;
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
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class TemplatePersistenceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private TemplateRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearTemplates() {
        jdbcTemplate.execute("TRUNCATE template CASCADE");
    }

    private static ChromeFields chrome() {
        return new ChromeFields(true, "Cover Title", "Cover Subtitle", "Header", "Footer", true, 55);
    }

    private static Template template(String name, TemplateDocumentType type) {
        Template base = Template.create(name, type, "rm@acme.com", Instant.now());
        return new Template(base.id(), base.name(), base.documentType(), "Body {{release.name}}",
                base.active(), chrome(), base.groupingDimension(), null, List.of("unknown.field"),
                base.createdAt(), base.createdBy(), base.updatedAt(), base.updatedBy());
    }

    private static LogoImage logo() {
        LogoMetadata metadata = new LogoMetadata("image/png", "logo.png", 4,
                10, 10, Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        return new LogoImage(new byte[] {1, 2, 3, 4}, metadata);
    }

    @Test
    void createThenFindByIdRoundTripsAllFields() {
        Template saved = repository.create(template("Release Notes A", TemplateDocumentType.RELEASE_NOTES));

        Template loaded = repository.findById(saved.id()).orElseThrow();
        assertThat(loaded.name()).isEqualTo("Release Notes A");
        assertThat(loaded.documentType()).isEqualTo(TemplateDocumentType.RELEASE_NOTES);
        assertThat(loaded.body()).isEqualTo("Body {{release.name}}");
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
        assertThat(loaded.warnings()).containsExactly("unknown.field");
        assertThat(loaded.groupingDimension()).isEqualTo(GroupingDimension.ISSUE_TYPE);
    }

    @Test
    void updateRoundTripsAComponentGroupingDimension() {
        Template saved = repository.create(template("Component Grouped", TemplateDocumentType.RUNBOOK));
        Template withComponentDimension = new Template(saved.id(), saved.name(), saved.documentType(),
                saved.body(), saved.active(), saved.chrome(), GroupingDimension.COMPONENT, saved.logo(),
                saved.warnings(), saved.createdAt(), saved.createdBy(), saved.updatedAt(), saved.updatedBy());

        repository.update(withComponentDimension);

        Template loaded = repository.findById(saved.id()).orElseThrow();
        assertThat(loaded.groupingDimension()).isEqualTo(GroupingDimension.COMPONENT);
    }

    // D-17.2-07: proves V24's widened CHECK accepts BUNDLE.
    @Test
    void updateRoundTripsABundleGroupingDimension() {
        Template saved = repository.create(template("Bundle Grouped", TemplateDocumentType.RUNBOOK));
        Template withBundleDimension = new Template(saved.id(), saved.name(), saved.documentType(),
                saved.body(), saved.active(), saved.chrome(), GroupingDimension.BUNDLE, saved.logo(),
                saved.warnings(), saved.createdAt(), saved.createdBy(), saved.updatedAt(), saved.updatedBy());

        repository.update(withBundleDimension);

        Template loaded = repository.findById(saved.id()).orElseThrow();
        assertThat(loaded.groupingDimension()).isEqualTo(GroupingDimension.BUNDLE);
    }

    @Test
    void duplicateNameWithinDocumentTypeIsRejectedButSameNameUnderDifferentTypeSucceeds() {
        repository.create(template("Standard", TemplateDocumentType.RELEASE_NOTES));

        assertThatThrownBy(() -> repository.create(template("Standard", TemplateDocumentType.RELEASE_NOTES)))
                .isInstanceOf(TemplateDuplicateException.class);

        Template other = repository.create(template("Standard", TemplateDocumentType.RUNBOOK));
        assertThat(other.name()).isEqualTo("Standard");
    }

    @Test
    void setActiveAfterSetActiveLeavesExactlyOneActiveRowForThatType() {
        Template a = repository.create(template("Runbook A", TemplateDocumentType.RUNBOOK));
        Template b = repository.create(template("Runbook B", TemplateDocumentType.RUNBOOK));

        repository.setActive(a.id());
        repository.setActive(b.id());

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM template WHERE document_type = 'RUNBOOK' AND active", Integer.class);
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    void settingARunbookActiveDoesNotDeactivateAnActiveReleaseNotesTemplate() {
        Template releaseNotes = repository.create(template("RN", TemplateDocumentType.RELEASE_NOTES));
        Template runbook = repository.create(template("RB", TemplateDocumentType.RUNBOOK));
        repository.setActive(releaseNotes.id());

        repository.setActive(runbook.id());

        Template reloaded = repository.findById(releaseNotes.id()).orElseThrow();
        assertThat(reloaded.active()).isTrue();
    }

    // D-04-12/T-04-06: proves the invariant is enforced by Postgres, not merely by adapter logic.
    @Test
    void directSqlInsertOfASecondActiveRowForTheSameTypeIsRejectedByTheDatabase() {
        Template a = repository.create(template("Approval A", TemplateDocumentType.APPROVAL));
        Template b = repository.create(template("Approval B", TemplateDocumentType.APPROVAL));
        repository.setActive(a.id());

        assertThatThrownBy(() -> jdbcTemplate.update("UPDATE template SET active = true WHERE id = ?", b.id()))
                .isInstanceOf(Exception.class);
    }

    @Test
    void deleteOnTheActiveTemplateSucceedsAndLeavesZeroActiveRowsForThatType() {
        Template a = repository.create(template("Runbook Delete A", TemplateDocumentType.RUNBOOK));
        Template b = repository.create(template("Runbook Delete B", TemplateDocumentType.RUNBOOK));
        repository.setActive(a.id());

        repository.delete(a.id());

        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM template WHERE document_type = 'RUNBOOK' AND active", Integer.class);
        assertThat(activeCount).isEqualTo(0);
        assertThat(repository.findByDocumentType(TemplateDocumentType.RUNBOOK))
                .extracting(Template::id)
                .containsExactly(b.id());
    }

    @Test
    void saveLogoThenFindLogoRoundTripsBytesAndFindByIdReturnsMetadataWithoutBytes() {
        Template t = repository.create(template("With Logo", TemplateDocumentType.RELEASE_NOTES));

        repository.saveLogo(t.id(), logo());

        LogoImage found = repository.findLogo(t.id()).orElseThrow();
        assertThat(found.bytes()).containsExactly(1, 2, 3, 4);
        assertThat(found.metadata().width()).isEqualTo(10);
        assertThat(found.metadata().height()).isEqualTo(10);
        assertThat(found.metadata().byteSize()).isEqualTo(4);

        Template reloaded = repository.findById(t.id()).orElseThrow();
        assertThat(reloaded.logo()).isNotNull();
        assertThat(reloaded.logo().width()).isEqualTo(10);
        assertThat(reloaded.logo().height()).isEqualTo(10);
        assertThat(reloaded.logo().byteSize()).isEqualTo(4);

        repository.deleteLogo(t.id());
        assertThat(repository.findLogo(t.id())).isEmpty();
    }

    @Test
    void deletingATemplateWithALogoRemovesTheTemplateLogoRowViaCascade() {
        Template t = repository.create(template("Cascade Logo", TemplateDocumentType.RELEASE_NOTES));
        repository.saveLogo(t.id(), logo());

        repository.delete(t.id());

        Integer logoCount = jdbcTemplate.queryForObject("SELECT count(*) FROM template_logo", Integer.class);
        assertThat(logoCount).isEqualTo(0);
    }
}
