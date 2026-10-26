package com.evision.releasemgmt.domain.document;

import com.evision.releasemgmt.domain.template.ChromeFields;
import com.evision.releasemgmt.domain.template.TemplateDocumentType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentSnapshotTest {

    private static final Instant T0 = Instant.parse("2026-08-01T09:00:00Z");
    private static final Instant T1 = Instant.parse("2026-08-02T09:00:00Z");
    private static final Instant T2 = Instant.parse("2026-08-03T09:00:00Z");

    private static DocumentSnapshot generated() {
        return DocumentSnapshot.create(UUID.randomUUID(), TemplateDocumentType.RELEASE_NOTES, 3, "# Old",
                UUID.randomUUID(), "Standard", "Body {{x}}", ChromeFields.empty(), "acct-0", T1);
    }

    @Test
    void generatedRevisionIsNotAiAssistedAndBasisIsGeneratedAt() {
        DocumentSnapshot s = generated();

        assertThat(s.aiAssist()).isNull();
        assertThat(s.aiAssisted()).isFalse();
        assertThat(s.stalenessBasis()).isEqualTo(T1);
    }

    @Test
    void aiRevisionCopiesTemplateFieldsAndTakesSourceBasis() {
        DocumentSnapshot source = generated();

        DocumentSnapshot ai = DocumentSnapshot.createAiRevision(source, 4, "# New", AiMode.CLEAN_UP,
                "instr", "acct-1", T2);

        assertThat(ai.id()).isNotEqualTo(source.id());
        assertThat(ai.releaseId()).isEqualTo(source.releaseId());
        assertThat(ai.documentType()).isEqualTo(source.documentType());
        assertThat(ai.templateId()).isEqualTo(source.templateId());
        assertThat(ai.templateName()).isEqualTo(source.templateName());
        assertThat(ai.templateBody()).isEqualTo(source.templateBody());
        assertThat(ai.chrome()).isEqualTo(source.chrome());
        assertThat(ai.revision()).isEqualTo(4);
        assertThat(ai.markdown()).isEqualTo("# New");
        assertThat(ai.generatedAt()).isEqualTo(T2);
        assertThat(ai.generatedBy()).isEqualTo("acct-1");
        assertThat(ai.aiAssist()).isEqualTo(new AiAssist(AiMode.CLEAN_UP, "instr", 3, T1));
        assertThat(ai.aiAssisted()).isTrue();
        assertThat(ai.stalenessBasis()).isEqualTo(T1);
    }

    @Test
    void chainedAiRevisionKeepsTheOriginalBasis() {
        DocumentSnapshot first = DocumentSnapshot.createAiRevision(generated(), 4, "a", AiMode.CUSTOM,
                "i1", "u", T2);
        DocumentSnapshot second = DocumentSnapshot.createAiRevision(first, 5, "b", AiMode.CUSTOM, "i2", "u", T2);

        assertThat(second.aiAssist().sourceRevision()).isEqualTo(4);
        assertThat(second.stalenessBasis()).isEqualTo(T1);
    }

    @Test
    void chainedBasisIsTheSourcesBasisNotItsGeneratedAt() {
        DocumentSnapshot source = new DocumentSnapshot(UUID.randomUUID(), UUID.randomUUID(),
                TemplateDocumentType.RUNBOOK, 2, "m", null, "t", "b", ChromeFields.empty(), T2, "u",
                new AiAssist(AiMode.CLEAN_UP, "i", 1, T0));

        DocumentSnapshot ai = DocumentSnapshot.createAiRevision(source, 3, "n", AiMode.CUSTOM, "i", "u", T2);

        assertThat(ai.stalenessBasis()).isEqualTo(T0);
    }

    @Test
    void aiAssistRejectsInvalidValues() {
        assertThatThrownBy(() -> new AiAssist(null, "i", 1, T0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiAssist(AiMode.CUSTOM, " ", 1, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiAssist(AiMode.CUSTOM, null, 1, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiAssist(AiMode.CUSTOM, "i", 0, T0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AiAssist(AiMode.CUSTOM, "i", 1, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void toStringNeverPrintsInstructionOrMarkdown() {
        DocumentSnapshot ai = DocumentSnapshot.createAiRevision(generated(), 4, "SECRET-MARKDOWN",
                AiMode.CUSTOM, "SECRET-INSTRUCTION", "u", T2);

        assertThat(ai.toString()).doesNotContain("SECRET-MARKDOWN", "SECRET-INSTRUCTION", "Body {{x}}");
        assertThat(ai.aiAssist().toString()).doesNotContain("SECRET-INSTRUCTION").contains("instructionChars=18");
    }
}
