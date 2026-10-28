package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.document.AiAssist;
import com.evision.releasemgmt.domain.document.AiMode;
import com.evision.releasemgmt.domain.document.DocumentNotFoundException;
import com.evision.releasemgmt.domain.document.DocumentSnapshot;
import com.evision.releasemgmt.domain.document.RevisionSupersededException;
import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.LogoImage;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import com.evision.releasemgmt.ports.DocumentSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRevisionServiceTest {

    private static final TemplateDocumentType TYPE = TemplateDocumentType.RUNBOOK;
    private static final Instant GENERATED_AT = Instant.parse("2026-08-01T00:00:00Z");

    private final DocumentSnapshotRepository repository = mock(DocumentSnapshotRepository.class);
    private final AiRevisionService service = new AiRevisionService(repository);
    private final UUID releaseId = UUID.randomUUID();

    @Test
    void savesRevisionNPlusOneCopiedFromTheLatestWithAiProvenanceAndTheSourceLogo() {
        ChromeFields chrome = new ChromeFields(true, "Cover", "Sub", "Header", "Footer", true, 40);
        DocumentSnapshot source = revision(3, chrome);
        LogoImage logo = new LogoImage(new byte[] {1}, null);
        when(repository.findLatest(releaseId, TYPE)).thenReturn(Optional.of(source));
        when(repository.nextRevision(releaseId, TYPE)).thenReturn(4);
        when(repository.findLogo(source.id())).thenReturn(Optional.of(logo));
        when(repository.create(any(), any())).thenAnswer(call -> call.getArgument(0));

        DocumentSnapshot saved = service.save(releaseId, TYPE, 3, AiMode.CLEAN_UP, "instr", "# Clean", "acct-1");

        ArgumentCaptor<DocumentSnapshot> created = ArgumentCaptor.forClass(DocumentSnapshot.class);
        ArgumentCaptor<LogoImage> createdLogo = ArgumentCaptor.forClass(LogoImage.class);
        verify(repository).create(created.capture(), createdLogo.capture());
        assertThat(created.getValue()).isSameAs(saved);
        assertThat(saved.revision()).isEqualTo(4);
        assertThat(saved.markdown()).isEqualTo("# Clean");
        assertThat(saved.chrome()).isEqualTo(chrome);
        assertThat(saved.templateId()).isEqualTo(source.templateId());
        assertThat(saved.templateName()).isEqualTo(source.templateName());
        assertThat(saved.templateBody()).isEqualTo(source.templateBody());
        assertThat(saved.generatedBy()).isEqualTo("acct-1");
        assertThat(saved.aiAssist()).isEqualTo(new AiAssist(AiMode.CLEAN_UP, "instr", 3, GENERATED_AT));
        assertThat(createdLogo.getValue()).isSameAs(logo);
    }

    @Test
    void savesWithoutALogoWhenTheSourceHasNone() {
        DocumentSnapshot source = revision(1, ChromeFields.empty());
        when(repository.findLatest(releaseId, TYPE)).thenReturn(Optional.of(source));
        when(repository.nextRevision(releaseId, TYPE)).thenReturn(2);
        when(repository.findLogo(source.id())).thenReturn(Optional.empty());
        when(repository.create(any(), any())).thenAnswer(call -> call.getArgument(0));

        service.save(releaseId, TYPE, 1, AiMode.CUSTOM, "shorten", "# Short", "acct-1");

        verify(repository).create(any(), org.mockito.ArgumentMatchers.isNull());
    }

    @Test
    void refusesWhenTheCallersSourceRevisionIsNotTheLatest() {
        when(repository.findLatest(releaseId, TYPE)).thenReturn(Optional.of(revision(4, ChromeFields.empty())));

        assertThatThrownBy(() -> service.save(releaseId, TYPE, 3, AiMode.CLEAN_UP, "instr", "# Clean", "acct-1"))
                .isInstanceOf(RevisionSupersededException.class);

        verify(repository, never()).create(any(), any());
    }

    @Test
    void refusesWhenThereIsNoDocumentYet() {
        when(repository.findLatest(releaseId, TYPE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(releaseId, TYPE, 1, AiMode.CLEAN_UP, "instr", "# Clean", "acct-1"))
                .isInstanceOf(DocumentNotFoundException.class);

        verify(repository, never()).create(any(), any());
    }

    private DocumentSnapshot revision(int revision, ChromeFields chrome) {
        return new DocumentSnapshot(UUID.randomUUID(), releaseId, TYPE, revision, "# Source", UUID.randomUUID(),
                "Template", "body", chrome, GENERATED_AT, "actor", null);
    }
}
