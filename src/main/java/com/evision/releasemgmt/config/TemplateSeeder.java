package com.evision.releasemgmt.config;

import com.evision.releasemgmt.application.TemplateService;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.GroupingDimension;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

// D-04-13/D-04-14: ships three realistic starters, one active per document type, on first start.
// Follows the JiraSiteConfigSeeder shape - ApplicationRunner + an idempotency guard - rather than a
// Flyway INSERT, because that is the only seeding mechanism this codebase has established.
@Component
public class TemplateSeeder implements ApplicationRunner {

    // Same seed-actor value JiraSiteConfigService uses, so a seeded template's created_by/updated_by
    // is consistent across every seeder in this codebase and distinguishable from a hand-authored one.
    private static final String SEED_ACTOR = "application-config-seed";

    private final TemplateService templateService;

    public TemplateSeeder(TemplateService templateService) {
        this.templateService = templateService;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfAbsent(TemplateDocumentType.RELEASE_NOTES, "Standard release notes",
                "/templates/starter-release-notes.md", releaseNotesChrome());
        seedIfAbsent(TemplateDocumentType.RUNBOOK, "Standard deployment runbook",
                "/templates/starter-runbook.md", runbookChrome());
        seedIfAbsent(TemplateDocumentType.APPROVAL, "Standard change approval",
                "/templates/starter-approval.md", approvalChrome());
    }

    // Deleting every template of a type does bring its starter back on the next start. That is the
    // only route back to a working starter without re-authoring one by hand, and it cannot surprise
    // anyone who still has a template of that type.
    private void seedIfAbsent(TemplateDocumentType type, String name, String resourcePath, ChromeFields chrome) {
        if (templateService.list().stream().anyMatch(t -> t.documentType() == type)) {
            return;
        }
        String body = readResource(resourcePath);
        // Goes through the normal create/saveContent/activate path so the starter is validated by the
        // exact same TMPL-04 render every hand-authored template gets - if a starter ever stops
        // validating, startup fails loudly rather than shipping a broken artefact.
        Template created = templateService.create(name, type, SEED_ACTOR);
        // D-05-04: starters keep the ISSUE_TYPE default - none of the three seeded starters names a
        // component-shaped section, so there is nothing here for COMPONENT grouping to improve on.
        templateService.saveContent(created.id(), body, chrome, GroupingDimension.ISSUE_TYPE, SEED_ACTOR);
        templateService.activate(created.id(), SEED_ACTOR);
    }

    private String readResource(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IllegalStateException("Missing starter template resource: " + resourcePath);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read starter template resource: " + resourcePath, e);
        }
    }

    private static ChromeFields releaseNotesChrome() {
        return new ChromeFields(true, "{{release.name}} release notes", "Version {{release.version}}",
                "{{release.name}} — {{release.version}}", "Release notes · {{release.version}}", true);
    }

    private static ChromeFields runbookChrome() {
        return new ChromeFields(true, "Deployment runbook", "{{release.name}} — {{release.version}}",
                "Runbook · {{release.name}}", "Deployment runbook · {{release.version}}", true);
    }

    private static ChromeFields approvalChrome() {
        return new ChromeFields(true, "Change approval", "{{release.name}} — {{release.version}}",
                "Change approval · {{release.name}}", "Approval document · {{release.version}}", true);
    }
}
