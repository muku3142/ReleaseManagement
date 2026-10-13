package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.PgVectorImage;
import com.evision.releasemgmt.adapters.web.dto.DocumentRevisionListResponse;
import com.evision.releasemgmt.adapters.web.dto.ReleaseSearchResponse;
import com.evision.releasemgmt.adapters.web.dto.ReleaseSummaryResponse;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import com.evision.releasemgmt.ports.JiraCredentialRepository;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// The seam plan 05.3-01 and 05.3-02 cannot cover between them. 05.3-01 proves the projection query
// survives an undecryptable row; 05.3-02 proves the controllers call the resolution service. Neither
// proves that the display path as actually wired avoids JiraCredentialEntity - a controller that had
// used findByAccountId would leave both green and still take the whole document/release/template UI
// down on the next RM_CREDENTIAL_KEY rotation, which has already happened once in this project.
@SpringBootTest
@Testcontainers
class ActorDisplaySeamIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(PgVectorImage.IMAGE);

    @Autowired
    private ReleaseController releaseController;

    @Autowired
    private DocumentController documentController;

    @Autowired
    private JiraCredentialRepository credentialRepository;

    @Autowired
    private ReleaseRepository releaseRepository;

    @Autowired
    private DocumentSnapshotRepository documentSnapshotRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @AfterEach
    void clearAll() {
        jdbcTemplate.execute("TRUNCATE release CASCADE");
        jdbcTemplate.execute("DELETE FROM jira_credential");
    }

    private void undecryptable(String accountId) {
        byte[] undecryptable = new byte[1 + 12 + 32];
        undecryptable[0] = 1;
        jdbcTemplate.update("UPDATE jira_credential SET encrypted_token = ? WHERE jira_account_id = ?",
                Base64.getEncoder().encodeToString(undecryptable), accountId);
    }

    private static ChromeFields chrome() {
        return ChromeFields.empty();
    }

    @Test
    void releaseListRendersTheEmailForARowWhoseTokenCannotBeDecrypted() {
        credentialRepository.upsert("557058:seam-a", "seam-a@acme.com", "ATATT3xFfGF0-seam-secret");
        releaseRepository.create(Release.create("Seam Release", "1.0", "CNV", "557058:seam-a", Instant.now()),
                List.of());
        undecryptable("557058:seam-a");

        // Deliberately broad. What matters is that the row genuinely cannot be read through the entity;
        // pinning Hibernate's wrapper type would make this fail on an upgrade for no reason.
        assertThatThrownBy(() -> credentialRepository.findByAccountId("557058:seam-a"))
                .isInstanceOf(RuntimeException.class);

        ReleaseSearchResponse response = releaseController.list("");

        assertThat(response.releases()).hasSize(1);
        assertThat(response.releases().get(0).createdBy()).isEqualTo("seam-a@acme.com");
    }

    @Test
    void documentRevisionHistoryRendersTheEmailForARowWhoseTokenCannotBeDecrypted() {
        credentialRepository.upsert("557058:seam-b", "seam-b@acme.com", "ATATT3xFfGF0-seam-secret");
        Release release = releaseRepository.create(
                Release.create("Seam Release B", "1.0", "CNV", "557058:seam-b", Instant.now()), List.of());
        undecryptable("557058:seam-b");

        assertThatThrownBy(() -> credentialRepository.findByAccountId("557058:seam-b"))
                .isInstanceOf(RuntimeException.class);

        // Criterion 2: these snapshots were written with the account id already stored, exactly as
        // pre-phase rows were, and are corrected at read time with nothing rewritten.
        documentSnapshotRepository.create(DocumentSnapshot.create(release.id(),
                TemplateDocumentType.RELEASE_NOTES, 1, "# Body", null, "Release Notes A",
                "Body {{release.name}}", chrome(), "557058:seam-b", Instant.now()), null);
        documentSnapshotRepository.create(DocumentSnapshot.create(release.id(),
                TemplateDocumentType.RELEASE_NOTES, 2, "# Body 2", null, "Release Notes A",
                "Body {{release.name}}", chrome(), "557058:seam-b", Instant.now()), null);

        DocumentRevisionListResponse response = documentController.revisions(release.id(),
                TemplateDocumentType.RELEASE_NOTES);

        assertThat(response.revisions()).hasSize(2);
        assertThat(response.revisions()).extracting(r -> r.generatedBy())
                .containsExactly("seam-b@acme.com", "seam-b@acme.com");
    }

    @Test
    void anActorWithNoCredentialRowRendersAsTheAccountId() {
        releaseRepository.create(
                Release.create("Seam Release No Row", "1.0", "CNV", "557058:no-such-row", Instant.now()),
                List.of());

        ReleaseSearchResponse response = releaseController.list("");

        assertThat(response.releases()).hasSize(1);
        String createdBy = response.releases().get(0).createdBy();
        assertThat(createdBy).isEqualTo("557058:no-such-row").isNotNull().isNotBlank();
    }

    @Test
    void noResponseCarriesTheStoredToken() {
        credentialRepository.upsert("557058:seam-c", "seam-c@acme.com", "ATATT3xFfGF0-seam-secret");
        Release release = releaseRepository.create(
                Release.create("Seam Release C", "1.0", "CNV", "557058:seam-c", Instant.now()), List.of());
        documentSnapshotRepository.create(DocumentSnapshot.create(release.id(),
                TemplateDocumentType.RELEASE_NOTES, 1, "# Body", null, "Release Notes A",
                "Body {{release.name}}", chrome(), "557058:seam-c", Instant.now()), null);

        String releasesJson = objectMapper.writeValueAsString(releaseController.list(""));
        String revisionsJson = objectMapper.writeValueAsString(
                documentController.revisions(release.id(), TemplateDocumentType.RELEASE_NOTES));
        String ciphertext = jdbcTemplate.queryForObject(
                "SELECT encrypted_token FROM jira_credential WHERE jira_account_id = ?",
                String.class, "557058:seam-c");

        // AUTH-03 / D-13 / D-05.3-06: the token is never returned to the client in any form - not the
        // plaintext, not the stored ciphertext, and no field named for it.
        assertThat(releasesJson).doesNotContain("ATATT3xFfGF0-seam-secret")
                .doesNotContain(ciphertext).doesNotContain("token");
        assertThat(revisionsJson).doesNotContain("ATATT3xFfGF0-seam-secret")
                .doesNotContain(ciphertext).doesNotContain("token");
    }

    @Test
    void oneUnreadableCredentialDoesNotBreakADifferentUsersRow() {
        credentialRepository.upsert("557058:seam-d", "d@acme.com", "ATATT3xFfGF0-seam-secret-d");
        undecryptable("557058:seam-d");
        credentialRepository.upsert("557058:seam-e", "e@acme.com", "ATATT3xFfGF0-seam-secret-e");
        releaseRepository.create(Release.create("Seam Release D", "1.0", "CNV", "557058:seam-d", Instant.now()),
                List.of());
        releaseRepository.create(Release.create("Seam Release E", "1.0", "CNV", "557058:seam-e", Instant.now()),
                List.of());

        ReleaseSearchResponse response = releaseController.list("");

        assertThat(response.releases()).hasSize(2);
        assertThat(response.releases()).extracting(ReleaseSummaryResponse::createdBy)
                .containsExactlyInAnyOrder("d@acme.com", "e@acme.com");
    }
}
