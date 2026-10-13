package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.application.TemplateService;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.GroupingDimension;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.domain.template.TemplateMalformedException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// D-04-13: proves the full backend template lifecycle - create, warn-not-block save, blocked save,
// activate, delete-the-active, duplicate - against a real PostgreSQL 18. Driven through TemplateService
// directly rather than an authenticated MockMvc client: the point of this plan is the round trip
// through the service/persistence layers, not the HTTP transport, which TemplateControllerTest (plan
// 04-04) already covers as a slice test.
@SpringBootTest
@Testcontainers
class TemplateLifecycleIT {

    private static final String ACTOR = "rm@acme.com";

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private TemplateService templateService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void createSaveActivateDeleteAndDuplicateRoundTripAgainstRealPostgres() {
        // 1. Create "Draft notes" for RELEASE_NOTES - inactive, empty body.
        Template draft = templateService.create("Draft notes", TemplateDocumentType.RELEASE_NOTES, ACTOR);
        assertThat(draft.active()).isFalse();
        assertThat(draft.body()).isEmpty();

        // 2. A save with an unknown placeholder warns but does not block.
        String typoBody = "# {{release.name}}\n{{ticket.compnent}}";
        Template saved = templateService.saveContent(draft.id(), typoBody, ChromeFields.empty(),
                GroupingDimension.ISSUE_TYPE, ACTOR);
        assertThat(saved.body()).isEqualTo(typoBody);
        assertThat(saved.warnings()).containsExactly("ticket.compnent — Body");

        // 3. A save with a disallowed construct is blocked and changes nothing already stored.
        assertThatThrownBy(() -> templateService.saveContent(draft.id(), "{{>shared}}", ChromeFields.empty(),
                GroupingDimension.ISSUE_TYPE, ACTOR))
                .isInstanceOf(TemplateMalformedException.class);
        Template afterBlockedSave = templateService.get(draft.id());
        assertThat(afterBlockedSave.body()).isEqualTo(typoBody);

        // 4. Activating "Draft notes" leaves exactly one active RELEASE_NOTES template, and the
        // seeded starter is no longer active.
        Template starter = templateService.list().stream()
                .filter(t -> t.documentType() == TemplateDocumentType.RELEASE_NOTES && !t.id().equals(draft.id()))
                .findFirst().orElseThrow();
        templateService.activate(draft.id(), ACTOR);
        assertThat(activeReleaseNotesCount()).isEqualTo(1);
        assertThat(templateService.get(draft.id()).active()).isTrue();
        assertThat(templateService.get(starter.id()).active()).isFalse();

        // 5. Deleting the now-active "Draft notes" is unguarded (D-04-13): list() still succeeds, the
        // remaining RELEASE_NOTES template is present, and zero-active is a legal, tolerated state.
        templateService.delete(draft.id());
        List<Template> remaining = templateService.list();
        assertThat(remaining).extracting(Template::id).doesNotContain(draft.id());
        assertThat(remaining).extracting(Template::id).contains(starter.id());
        assertThat(activeReleaseNotesCount()).isEqualTo(0);

        // 6. Duplicating the remaining RELEASE_NOTES template twice produces "… copy" then "… copy 2",
        // both inactive.
        Template firstCopy = templateService.duplicate(starter.id(), ACTOR);
        Template secondCopy = templateService.duplicate(starter.id(), ACTOR);
        assertThat(firstCopy.name()).isEqualTo(starter.name() + " copy");
        assertThat(secondCopy.name()).isEqualTo(starter.name() + " copy 2");
        assertThat(firstCopy.active()).isFalse();
        assertThat(secondCopy.active()).isFalse();
    }

    private int activeReleaseNotesCount() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM template WHERE document_type = 'RELEASE_NOTES' AND active", Integer.class);
        return count == null ? 0 : count;
    }
}
