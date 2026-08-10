package com.evision.releasemgmt.application;

import com.evision.releasemgmt.adapters.render.MustacheFlexmarkRenderAdapter;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.GroupingDimension;
import com.evision.releasemgmt.domain.template.Template;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.domain.template.TemplateMalformedException;
import com.evision.releasemgmt.ports.TemplateRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Uses a real RenderService wired to a real MustacheFlexmarkRenderAdapter throughout - the point of
// these tests is the validation behaviour, and mocking the renderer would test nothing.
class TemplateServiceTest {

    private final TemplateRepository repository = mock(TemplateRepository.class);
    private final RenderService renderService =
            Mockito.spy(new RenderService(new MustacheFlexmarkRenderAdapter(), Duration.ofSeconds(2), 500, 64));
    private final TemplateService service =
            new TemplateService(repository, renderService, new LogoValidator(2097152, 2000, 2000));

    @Test
    void createReturnsEmptyInactiveTemplateWithNoWarnings() {
        when(repository.create(any())).thenAnswer(inv -> inv.getArgument(0));

        Template created = service.create("Standard notes", TemplateDocumentType.RELEASE_NOTES, "actor-1");

        assertThat(created.body()).isEmpty();
        assertThat(created.chrome()).isEqualTo(ChromeFields.empty());
        assertThat(created.active()).isFalse();
        assertThat(created.warnings()).isEmpty();
    }

    @Test
    void newTemplateDefaultsToIssueType() {
        when(repository.create(any())).thenAnswer(inv -> inv.getArgument(0));

        Template created = service.create("Standard notes", TemplateDocumentType.RELEASE_NOTES, "actor-1");

        assertThat(created.groupingDimension()).isEqualTo(GroupingDimension.ISSUE_TYPE);
    }

    @Test
    void saveContentWithCleanBodyReturnsNoWarnings() {
        Template existing = existingTemplate();
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Template saved = service.saveContent(existing.id(), "Release {{release.name}} is ready.",
                ChromeFields.empty(), GroupingDimension.ISSUE_TYPE, "actor-2");

        assertThat(saved.warnings()).isEmpty();
    }

    @Test
    void saveContentPersistsTheGroupingDimension() {
        Template existing = existingTemplate();
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Template saved = service.saveContent(existing.id(), "Clean body", ChromeFields.empty(),
                GroupingDimension.COMPONENT, "actor-2");

        assertThat(saved.groupingDimension()).isEqualTo(GroupingDimension.COMPONENT);
    }

    @Test
    void saveContentWithUnknownBodyTokenWarnsWithoutBlockingTheSave() {
        Template existing = existingTemplate();
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Template saved = service.saveContent(existing.id(), "{{ticket.compnent}}", ChromeFields.empty(),
                GroupingDimension.ISSUE_TYPE, "actor-2");

        assertThat(saved.warnings()).containsExactly("ticket.compnent — Body");
    }

    @Test
    void saveContentWithUnknownCoverTitleTokenWarns() {
        Template existing = existingTemplate();
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));
        ChromeFields chrome = new ChromeFields(true, "{{nope}}", "", "", "", false);

        Template saved = service.saveContent(existing.id(), "", chrome, GroupingDimension.ISSUE_TYPE, "actor-2");

        assertThat(saved.warnings()).containsExactly("nope — Cover title");
    }

    @Test
    void saveContentWithAPartialInBodyThrowsMalformedForBodyAndPersistsNothing() {
        Template existing = existingTemplate();
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.saveContent(existing.id(), "{{>partial}}", ChromeFields.empty(),
                GroupingDimension.ISSUE_TYPE, "actor-2"))
                .isInstanceOf(TemplateMalformedException.class)
                .satisfies(ex -> assertThat(((TemplateMalformedException) ex).field()).isEqualTo("Body"));

        verify(repository, never()).update(any());
    }

    @Test
    void saveContentWithAMalformedHeaderTextThrowsMalformedForHeaderText() {
        Template existing = existingTemplate();
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));
        ChromeFields chrome = new ChromeFields(false, "", "", "{{#items}}", "", false);

        assertThatThrownBy(() -> service.saveContent(existing.id(), "", chrome, GroupingDimension.ISSUE_TYPE,
                "actor-2"))
                .isInstanceOf(TemplateMalformedException.class)
                .satisfies(ex -> assertThat(((TemplateMalformedException) ex).field()).isEqualTo("Header text"));

        verify(repository, never()).update(any());
    }

    @Test
    void blankChromeFieldsAreSkippedEntirelyWithNoValidationCall() {
        Template existing = existingTemplate();
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(repository.update(any())).thenAnswer(inv -> inv.getArgument(0));

        Template saved = service.saveContent(existing.id(), "Clean body", ChromeFields.empty(),
                GroupingDimension.ISSUE_TYPE, "actor-2");

        assertThat(saved.warnings()).isEmpty();
        verify(renderService).render(eq("Clean body"), any(), eq("Body"));
        verify(renderService, never()).render(anyString(), any(), eq("Cover title"));
        verify(renderService, never()).render(anyString(), any(), eq("Cover subtitle"));
        verify(renderService, never()).render(anyString(), any(), eq("Header text"));
        verify(renderService, never()).render(anyString(), any(), eq("Footer text"));
    }

    @Test
    void duplicateGeneratesIncrementingCopyNamesAndIsNeverActive() {
        Template source = new Template(UUID.randomUUID(), "Standard notes",
                TemplateDocumentType.RELEASE_NOTES, "Body", true, ChromeFields.empty(),
                GroupingDimension.ISSUE_TYPE, null, List.of(), Instant.now(), "actor-1", Instant.now(), "actor-1");
        when(repository.findById(source.id())).thenReturn(Optional.of(source));
        when(repository.findByDocumentType(TemplateDocumentType.RELEASE_NOTES)).thenReturn(List.of(source));
        ArgumentCaptor<Template> captor = ArgumentCaptor.forClass(Template.class);
        when(repository.create(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        Template copy1 = service.duplicate(source.id(), "actor-2");
        assertThat(copy1.name()).isEqualTo("Standard notes copy");
        assertThat(copy1.active()).isFalse();

        when(repository.findByDocumentType(TemplateDocumentType.RELEASE_NOTES))
                .thenReturn(List.of(source, copy1));
        Template copy2 = service.duplicate(source.id(), "actor-2");
        assertThat(copy2.name()).isEqualTo("Standard notes copy 2");
        assertThat(copy2.active()).isFalse();
    }

    @Test
    void duplicateCarriesTheGroupingDimension() {
        Template source = new Template(UUID.randomUUID(), "Standard notes",
                TemplateDocumentType.RELEASE_NOTES, "Body", true, ChromeFields.empty(),
                GroupingDimension.COMPONENT, null, List.of(), Instant.now(), "actor-1", Instant.now(), "actor-1");
        when(repository.findById(source.id())).thenReturn(Optional.of(source));
        when(repository.findByDocumentType(TemplateDocumentType.RELEASE_NOTES)).thenReturn(List.of(source));
        when(repository.create(any())).thenAnswer(inv -> inv.getArgument(0));

        Template copy = service.duplicate(source.id(), "actor-2");

        assertThat(copy.groupingDimension()).isEqualTo(GroupingDimension.COMPONENT);
    }

    @Test
    void deleteOfTheActiveTemplateLeavesNoActiveTemplateForItsType() {
        Template active = new Template(UUID.randomUUID(), "Standard notes",
                TemplateDocumentType.RELEASE_NOTES, "Body", true, ChromeFields.empty(),
                GroupingDimension.ISSUE_TYPE, null, List.of(), Instant.now(), "actor-1", Instant.now(), "actor-1");
        when(repository.findByDocumentType(TemplateDocumentType.RELEASE_NOTES)).thenReturn(List.of());

        service.delete(active.id());

        verify(repository).delete(active.id());
        assertThat(repository.findByDocumentType(TemplateDocumentType.RELEASE_NOTES)).isEmpty();
    }

    // WR-04: a real template with no logo must come back as an empty Optional, not collapse into
    // the same TemplateNotFoundException a genuinely missing template throws.
    @Test
    void getLogoOnARealTemplateWithNoLogoReturnsEmptyRatherThanTemplateNotFound() {
        Template existing = existingTemplate();
        when(repository.findById(existing.id())).thenReturn(Optional.of(existing));
        when(repository.findLogo(existing.id())).thenReturn(Optional.empty());

        assertThat(service.getLogo(existing.id())).isEmpty();
    }

    @Test
    void getLogoOnAMissingTemplateThrowsTemplateNotFound() {
        UUID missingId = UUID.randomUUID();
        when(repository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getLogo(missingId))
                .isInstanceOf(com.evision.releasemgmt.domain.template.TemplateNotFoundException.class);
    }

    private static Template existingTemplate() {
        return Template.create("Standard notes", TemplateDocumentType.RELEASE_NOTES, "actor-1", Instant.now());
    }
}
