package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.domain.template.TemplateNotFoundException;
import com.evision.releasemgmt.domain.template.view.ReleaseView;
import com.evision.releasemgmt.domain.template.view.SampleReleaseView;
import com.evision.releasemgmt.ports.TemplateRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// D-04-13: freely editable and freely deletable - there is no requireOpen()-style lifecycle guard
// anywhere in this service, unlike ReleaseService.
@Service
public class TemplateService {

    private final TemplateRepository repository;
    private final RenderService renderService;

    public TemplateService(TemplateRepository repository, RenderService renderService) {
        this.repository = repository;
        this.renderService = renderService;
    }

    public List<Template> list() {
        return repository.findAll();
    }

    public Template get(UUID id) {
        return repository.findById(id).orElseThrow(TemplateNotFoundException::new);
    }

    public Template create(String name, TemplateDocumentType documentType, String actor) {
        // New templates are never auto-activated, including when the type currently has none - the
        // release manager marks one active explicitly, and a type with zero active templates is a
        // supported state (D-04-13, UI-SPEC planning input #3). Do not "fix" this into auto-activate.
        return repository.create(Template.create(name.trim(), documentType, actor, Instant.now()));
    }

    public Template rename(UUID id, String name, String actor) {
        Template existing = get(id);
        Instant now = Instant.now();
        Template renamed = new Template(existing.id(), name.trim(), existing.documentType(),
                existing.body(), existing.active(), existing.chrome(), existing.logo(),
                existing.warnings(), existing.createdAt(), existing.createdBy(), now, actor);
        return repository.update(renamed);
    }

    public Template saveContent(UUID id, String body, ChromeFields chrome, String actor) {
        Template existing = get(id);
        ReleaseView sample = SampleReleaseView.instance();
        List<String> warnings = new ArrayList<>();
        collect(warnings, body, sample, "Body");
        collect(warnings, chrome.coverTitle(), sample, "Cover title");
        collect(warnings, chrome.coverSubtitle(), sample, "Cover subtitle");
        collect(warnings, chrome.headerText(), sample, "Header text");
        collect(warnings, chrome.footerText(), sample, "Footer text");

        Template updated = new Template(existing.id(), existing.name(), existing.documentType(),
                body, existing.active(), chrome, existing.logo(), List.copyOf(warnings),
                existing.createdAt(), existing.createdBy(), Instant.now(), actor);
        return repository.update(updated);
    }

    public Template activate(UUID id, String actor) {
        // Activation is not an edit of the template's content, so updatedBy is deliberately left
        // untouched here - actor is still required at the controller so the audit story is uniform.
        return repository.setActive(id);
    }

    public Template duplicate(UUID id, String actor) {
        Template source = get(id);
        String base = source.name() + " copy";
        Set<String> names = repository.findByDocumentType(source.documentType()).stream()
                .map(Template::name).collect(Collectors.toSet());
        String name = base;
        for (int n = 2; names.contains(name) && n <= 50; n++) {
            name = base + " " + n;
        }

        // The logo lives in its own table and copying binary content is not worth the code, so the
        // copy never carries one - the release manager re-uploads it if the duplicate needs one.
        Instant now = Instant.now();
        Template copy = new Template(UUID.randomUUID(), name, source.documentType(), source.body(),
                false, source.chrome(), null, source.warnings(), now, actor, now, actor);
        return repository.create(copy);
    }

    public void delete(UUID id) {
        repository.delete(id);
    }

    // D-04-07: unknown placeholders warn, they do not block. This is a deliberate, recorded deviation
    // from TMPL-04's "reports unknown placeholders as errors" - the requirement's actual intent is
    // preserved by D-04-08's [[unknown: token]] marker, which makes the defect loud at every render
    // instead of only at save time.
    private void collect(List<String> warnings, String source, ReleaseView sample, String field) {
        if (source == null || source.isBlank()) {
            return;
        }
        RenderResult result = renderService.render(source, sample, field);
        for (String token : result.unknownTokens()) {
            warnings.add(token + " — " + field);
        }
    }
}
