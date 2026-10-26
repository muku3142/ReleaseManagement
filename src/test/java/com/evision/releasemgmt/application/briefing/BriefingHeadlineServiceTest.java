package com.evision.releasemgmt.application.briefing;

import com.evision.releasemgmt.config.BriefingsProperties;
import com.evision.releasemgmt.domain.assistant.FailureReason;
import com.evision.releasemgmt.domain.assistant.TransformOutcome;
import com.evision.releasemgmt.domain.briefing.BriefingContent;
import com.evision.releasemgmt.domain.briefing.BriefingStatus;
import com.evision.releasemgmt.ports.RagTransformPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class BriefingHeadlineServiceTest {

    private static final String INSTRUCTION = "Write a headline.";
    private static final BriefingsProperties PROPS = new BriefingsProperties(null, null, null, null, null,
            new BriefingsProperties.Headline(Duration.ofMillis(200), INSTRUCTION));

    private static final BriefingContent CONTENT = new BriefingContent(BriefingStatus.ATTENTION, 2,
            "Rule headline", "Rule summary", List.of(
                    new BriefingContent.Section("Empty", List.of()),
                    new BriefingContent.Section("Blocked", List.of(
                            new BriefingContent.Item("CNV-1", "Payment export blocked"),
                            new BriefingContent.Item(null, "a line about nothing in particular"),
                            new BriefingContent.Item("CNV-2", "Ledger import stale"))),
                    new BriefingContent.Section("Due", List.of(
                            new BriefingContent.Item("CNV-3", "Release notes due"),
                            new BriefingContent.Item("CNV-4", "Not sent: beyond three")))));

    private final AtomicReference<String> sentText = new AtomicReference<>();
    private final AtomicReference<String> sentInstruction = new AtomicReference<>();

    private static ObjectProvider<RagTransformPort> provider(RagTransformPort port) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (port != null) {
            factory.addBean("transform", port);
        }
        return factory.getBeanProvider(RagTransformPort.class);
    }

    private RagTransformPort answering(String answer) {
        return (text, instruction, listener) -> {
            sentText.set(text);
            sentInstruction.set(instruction);
            listener.token(answer);
            return new TransformOutcome.Done("stop", false, 10, 10);
        };
    }

    private BriefingContent enrichWith(RagTransformPort port) {
        return new BriefingHeadlineService(provider(port), PROPS).enrich(CONTENT, "Conversion");
    }

    @Test
    void withoutTheModelTheContentIsUnchanged() {
        assertThat(new BriefingHeadlineService(provider(null), PROPS).enrich(CONTENT, "Conversion")).isSameAs(CONTENT);
    }

    @Test
    void aValidAnswerReplacesOnlyTheHeadlineAndSummary() {
        BriefingContent enriched = enrichWith(answering("On track: CNV-1 shipped\nLine two\n\nLine three\nLine four"));

        assertThat(enriched.headline()).isEqualTo("On track: CNV-1 shipped");
        assertThat(enriched.summary()).isEqualTo("Line two\nLine three");
        assertThat(enriched.status()).isEqualTo(CONTENT.status());
        assertThat(enriched.attention()).isEqualTo(CONTENT.attention());
        assertThat(enriched.sections()).isEqualTo(CONTENT.sections());
    }

    @Test
    void theFactsAreCompactAndTheInstructionComesFromTheProperties() {
        enrichWith(answering("On track"));

        assertThat(sentInstruction.get()).isEqualTo(INSTRUCTION);
        assertThat(sentText.get())
                .startsWith("Conversion: status attention, attention 2.")
                .contains("CNV-1 Payment export blocked", "CNV-2 Ledger import stale", "CNV-3 Release notes due")
                .doesNotContain("CNV-4", "a line about nothing")
                .hasSizeLessThanOrEqualTo(1500);
    }

    @Test
    void theFactsAreCappedAt1500Characters() {
        String longText = "x".repeat(2000);
        BriefingContent big = new BriefingContent(BriefingStatus.OK, 0, "h", "s", List.of(
                new BriefingContent.Section("A", List.of(new BriefingContent.Item("CNV-1", longText)))));

        new BriefingHeadlineService(provider(answering("ok")), PROPS).enrich(big, "Conversion");

        assertThat(sentText.get()).hasSize(1500);
    }

    @Test
    void aKeyNotInTheFactsKeepsTheRuleText() {
        assertThat(enrichWith(answering("CNV-999 is late"))).isSameAs(CONTENT);
    }

    @Test
    void aKeyPrefixOfAFactKeyIsNotAccepted() {
        assertThat(enrichWith(answering("CNV-10 is late"))).isSameAs(CONTENT);
    }

    @Test
    void anOverlongFirstLineKeepsTheRuleText() {
        assertThat(enrichWith(answering("a".repeat(141)))).isSameAs(CONTENT);
    }

    @Test
    void anEmptyAnswerKeepsTheRuleText() {
        assertThat(enrichWith(answering("  \n "))).isSameAs(CONTENT);
    }

    @Test
    void tooLargeKeepsTheRuleText() {
        assertThat(enrichWith((t, i, l) -> new TransformOutcome.TooLarge(100))).isSameAs(CONTENT);
    }

    @Test
    void busyKeepsTheRuleText() {
        assertThat(enrichWith((t, i, l) -> {
            l.token("On track");
            return new TransformOutcome.Failed(FailureReason.BUSY, 5);
        })).isSameAs(CONTENT);
    }

    @Test
    void aThrowingPortKeepsTheRuleText() {
        assertThat(enrichWith((t, i, l) -> {
            throw new IllegalStateException("boom");
        })).isSameAs(CONTENT);
    }

    @Test
    void aSlowModelIsCancelledAtTheDeadline() throws InterruptedException {
        CountDownLatch interrupted = new CountDownLatch(1);
        RagTransformPort slow = (t, i, l) -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                interrupted.countDown();
            }
            return new TransformOutcome.Done("stop", false, 1, 1);
        };

        long started = System.nanoTime();
        BriefingContent result = enrichWith(slow);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);

        assertThat(result).isSameAs(CONTENT);
        assertThat(elapsedMs).isLessThan(1000);
        assertThat(interrupted.await(1, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void onlyTheHeadlineIsReplacedWhenTheModelGivesOneLine() {
        BriefingContent enriched = enrichWith(answering("On track: CNV-2 moving"));

        assertThat(enriched.headline()).isEqualTo("On track: CNV-2 moving");
        assertThat(enriched.summary()).isEqualTo("Rule summary");
    }

    @Test
    void aDiscardedRoundIsNotKept() {
        BriefingContent enriched = enrichWith((t, i, l) -> {
            l.token("CNV-999 draft");
            l.roundDiscarded();
            l.token("On track");
            return new TransformOutcome.Done("stop", false, 1, 1);
        });

        assertThat(enriched.headline()).isEqualTo("On track");
    }
}
