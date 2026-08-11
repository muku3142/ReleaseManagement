package com.evision.releasemgmt.config;

import com.evision.releasemgmt.application.RenderResult;
import com.evision.releasemgmt.application.RenderService;
import com.evision.releasemgmt.application.TemplateService;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.domain.template.view.SampleReleaseView;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

// The seeder runs once, via ApplicationRunner, before any @Test method - tests 1-5 (@Order 1-5) rely
// on that startup state and must not mutate it. @Order 6-9 mutate the table and re-invoke
// seeder.run(null) directly, so they are ordered after the read-only assertions rather than
// truncated back to a known state, per the plan's "do not truncate in @BeforeEach" instruction.
@SpringBootTest
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TemplateSeederIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @Autowired
    private TemplateService templateService;

    @Autowired
    private RenderService renderService;

    @Autowired
    private TemplateSeeder seeder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    void startupSeedsExactlyThreeTemplatesOnePerDocumentTypeWithTheExpectedNames() {
        List<Template> templates = templateService.list();

        assertThat(templates).hasSize(3);
        assertThat(templates).extracting(Template::documentType)
                .containsExactlyInAnyOrder(TemplateDocumentType.RELEASE_NOTES, TemplateDocumentType.RUNBOOK,
                        TemplateDocumentType.APPROVAL);
        assertThat(templates).extracting(Template::name)
                .containsExactlyInAnyOrder("Standard release notes", "Standard deployment runbook",
                        "Standard change approval");
    }

    @Test
    @Order(2)
    void exactlyOneTemplatePerDocumentTypeIsActive() {
        Integer activeCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM template WHERE active", Integer.class);

        assertThat(activeCount).isEqualTo(3);
    }

    @Test
    @Order(3)
    void everySeededTemplateValidatesCleanAgainstTheCurrentCatalog() {
        for (Template template : templateService.list()) {
            assertThat(template.warnings()).as("warnings for %s", template.name()).isEmpty();

            assertRendersWithNoUnknownTokens(template.body(), "Body");
            assertRendersWithNoUnknownTokens(template.chrome().coverTitle(), "Cover title");
            assertRendersWithNoUnknownTokens(template.chrome().coverSubtitle(), "Cover subtitle");
            assertRendersWithNoUnknownTokens(template.chrome().headerText(), "Header text");
            assertRendersWithNoUnknownTokens(template.chrome().footerText(), "Footer text");
        }
    }

    @Test
    @Order(4)
    void releaseNotesStarterRendersRealContentFromTheSampleContext() {
        Template releaseNotes = templateOfType(TemplateDocumentType.RELEASE_NOTES);

        String html = renderService.render(releaseNotes.body(), SampleReleaseView.instance(), "Body").output();

        assertThat(html).contains("Fix duplicate invoice lines", "CNV-2896", "Run migration V4", "Database script");
    }

    @Test
    @Order(5)
    void runbookStarterRendersTheNoLinkedCodeInvertedSection() {
        Template runbook = templateOfType(TemplateDocumentType.RUNBOOK);

        String html = renderService.render(runbook.body(), SampleReleaseView.instance(), "Body").output();

        assertThat(html).contains("No linked code for this item.");
    }

    @Test
    @Order(6)
    void reRunningTheSeederASecondTimeChangesNothing() {
        seeder.run(null);

        assertThat(templateService.list()).hasSize(3);
    }

    @Test
    @Order(7)
    void truncatingAndReseedingRecreatesAllThreeTemplates() {
        jdbcTemplate.execute("TRUNCATE template CASCADE");
        assertThat(templateService.list()).isEmpty();

        seeder.run(null);

        assertThat(templateService.list()).hasSize(3);
        assertThat(templateService.list()).extracting(Template::documentType)
                .containsExactlyInAnyOrder(TemplateDocumentType.RELEASE_NOTES, TemplateDocumentType.RUNBOOK,
                        TemplateDocumentType.APPROVAL);
    }

    @Test
    @Order(8)
    void deletingOnlyTheRunbookStarterAndReseedingRecreatesOnlyThatType() {
        Template runbook = templateOfType(TemplateDocumentType.RUNBOOK);
        templateService.delete(runbook.id());
        assertThat(templateService.list()).extracting(Template::documentType)
                .doesNotContain(TemplateDocumentType.RUNBOOK);

        seeder.run(null);

        List<Template> runbooks = templateService.list().stream()
                .filter(t -> t.documentType() == TemplateDocumentType.RUNBOOK)
                .toList();
        assertThat(runbooks).hasSize(1);
        assertThat(runbooks.get(0).name()).isEqualTo("Standard deployment runbook");
    }

    @Test
    @Order(9)
    void anExtraTemplateOfATypePreventsThatTypeFromBeingReseeded() {
        templateService.create("Custom release notes", TemplateDocumentType.RELEASE_NOTES, "test-actor");
        int before = countOfType(TemplateDocumentType.RELEASE_NOTES);

        seeder.run(null);

        assertThat(countOfType(TemplateDocumentType.RELEASE_NOTES)).isEqualTo(before);
    }

    private void assertRendersWithNoUnknownTokens(String source, String field) {
        if (source == null || source.isBlank()) {
            return;
        }
        assertThatCode(() -> {
            RenderResult result = renderService.render(source, SampleReleaseView.instance(), field);
            assertThat(result.unknownTokens()).as("unknown tokens for %s", field).isEmpty();
        }).doesNotThrowAnyException();
    }

    private Template templateOfType(TemplateDocumentType type) {
        return templateService.list().stream()
                .filter(t -> t.documentType() == type)
                .findFirst()
                .orElseThrow();
    }

    private int countOfType(TemplateDocumentType type) {
        return (int) templateService.list().stream().filter(t -> t.documentType() == type).count();
    }
}
