package com.evision.releasemgmt.adapters.persistence;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.domain.template.TemplateNotFoundException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Testcontainers
class TemplateUserDefaultPersistenceIT {

    private static final String ACCOUNT_A = "557058:default-a";
    private static final String ACCOUNT_B = "557058:default-b";

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

    private Template newTemplate(TemplateDocumentType type) {
        return repository.create(Template.create("Template " + UUID.randomUUID(), type, ACCOUNT_A, Instant.now()));
    }

    private int rowCount(String account, String documentType) {
        return jdbcTemplate.queryForObject(
                "SELECT count(*) FROM template_user_default WHERE account_id = ? AND document_type = ?",
                Integer.class, account, documentType);
    }

    @Test
    void setThenFindReturnsTheTemplate() {
        Template x = newTemplate(TemplateDocumentType.RELEASE_NOTES);

        repository.setUserDefault(ACCOUNT_A, x.id());

        assertThat(repository.findUserDefault(ACCOUNT_A, TemplateDocumentType.RELEASE_NOTES))
                .map(Template::id).contains(x.id());
    }

    @Test
    void settingASecondDefaultOfTheSameTypeReplacesTheFirst() {
        Template x = newTemplate(TemplateDocumentType.RELEASE_NOTES);
        Template y = newTemplate(TemplateDocumentType.RELEASE_NOTES);

        repository.setUserDefault(ACCOUNT_A, x.id());
        repository.setUserDefault(ACCOUNT_A, y.id());

        assertThat(rowCount(ACCOUNT_A, "RELEASE_NOTES")).isEqualTo(1);
        assertThat(repository.findUserDefault(ACCOUNT_A, TemplateDocumentType.RELEASE_NOTES))
                .map(Template::id).contains(y.id());
    }

    @Test
    void storedDocumentTypeComesFromTheTemplateAndLeavesOtherTypesUntouched() {
        Template y = newTemplate(TemplateDocumentType.RELEASE_NOTES);
        Template r = newTemplate(TemplateDocumentType.RUNBOOK);
        repository.setUserDefault(ACCOUNT_A, y.id());

        repository.setUserDefault(ACCOUNT_A, r.id());

        String storedType = jdbcTemplate.queryForObject(
                "SELECT document_type FROM template_user_default WHERE account_id = ? AND template_id = ?",
                String.class, ACCOUNT_A, r.id());
        assertThat(storedType).isEqualTo("RUNBOOK");
        assertThat(repository.findUserDefault(ACCOUNT_A, TemplateDocumentType.RELEASE_NOTES))
                .map(Template::id).contains(y.id());
    }

    @Test
    void accountsAreIsolatedAndActiveIsNeverTouched() {
        Template x = newTemplate(TemplateDocumentType.RELEASE_NOTES);
        Template other = newTemplate(TemplateDocumentType.RUNBOOK);
        repository.setUserDefault(ACCOUNT_A, x.id());
        repository.setUserDefault(ACCOUNT_B, other.id());

        assertThat(repository.findUserDefaultTemplateIds(ACCOUNT_A)).containsExactly(x.id());
        assertThat(repository.findUserDefaultTemplateIds(ACCOUNT_B)).containsExactly(other.id());
        assertThat(repository.findUserDefault(ACCOUNT_B, TemplateDocumentType.RELEASE_NOTES)).isEmpty();
        assertThat(repository.findById(x.id())).map(Template::active).contains(false);
        assertThat(repository.findActive(TemplateDocumentType.RELEASE_NOTES)).isEmpty();
    }

    @Test
    void settingAnUnknownTemplateThrowsAndWritesNothing() {
        assertThatThrownBy(() -> repository.setUserDefault(ACCOUNT_A, UUID.randomUUID()))
                .isInstanceOf(TemplateNotFoundException.class);

        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM template_user_default", Integer.class))
                .isZero();
    }

    @Test
    void clearOnlyDeletesWhenTheTemplateIsTheCurrentDefaultAndIsIdempotent() {
        Template x = newTemplate(TemplateDocumentType.RELEASE_NOTES);
        Template y = newTemplate(TemplateDocumentType.RELEASE_NOTES);
        repository.setUserDefault(ACCOUNT_A, y.id());

        repository.clearUserDefault(ACCOUNT_A, x.id());
        assertThat(rowCount(ACCOUNT_A, "RELEASE_NOTES")).isEqualTo(1);

        repository.clearUserDefault(ACCOUNT_A, y.id());
        assertThat(rowCount(ACCOUNT_A, "RELEASE_NOTES")).isZero();

        repository.clearUserDefault(ACCOUNT_A, y.id());
        assertThat(rowCount(ACCOUNT_A, "RELEASE_NOTES")).isZero();
    }

    @Test
    void deletingTheTemplateCascadesToTheUserDefault() {
        Template y = newTemplate(TemplateDocumentType.RELEASE_NOTES);
        repository.setUserDefault(ACCOUNT_A, y.id());

        repository.delete(y.id());

        assertThat(repository.findUserDefault(ACCOUNT_A, TemplateDocumentType.RELEASE_NOTES)).isEmpty();
        assertThat(rowCount(ACCOUNT_A, "RELEASE_NOTES")).isZero();
    }

    @Test
    void existsByDocumentTypeReflectsAnyTemplateOfThatType() {
        assertThat(repository.existsByDocumentType(TemplateDocumentType.APPROVAL)).isFalse();

        newTemplate(TemplateDocumentType.APPROVAL);

        assertThat(repository.existsByDocumentType(TemplateDocumentType.APPROVAL)).isTrue();
    }
}
