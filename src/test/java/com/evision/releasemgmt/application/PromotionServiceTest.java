package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionDraft;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.promotion.PromotionRejectedException;
import com.evision.releasemgmt.domain.promotion.PromotionRejection;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.ports.PromotionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PromotionServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    private final PromotionRepository repository = mock(PromotionRepository.class);
    private final PromotionService service =
            new PromotionService(repository, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID releaseId = UUID.randomUUID();
    private final Stage dev = new Stage(UUID.randomUUID(), "DEV", 1, false, null);
    private final PromotionHistory frozen = new PromotionHistory(releaseId, true, NOW.minusSeconds(3600),
            List.of(dev), List.of());

    @SuppressWarnings("unchecked")
    private Function<PromotionHistory, Promotion> capturedDecision(PromotionDraft draft) {
        service.record(releaseId, draft, "acc-1");
        ArgumentCaptor<Function<PromotionHistory, Promotion>> captor = ArgumentCaptor.forClass(Function.class);
        verify(repository).appendUnderLock(eq(releaseId), captor.capture());
        return captor.getValue();
    }

    @Test
    void decisionStampsTheClockAndTheActor() {
        Promotion p = capturedDecision(new PromotionDraft(dev.id(), PromotionOutcome.SUCCESS, null, null))
                .apply(frozen);

        assertThat(p.recordedAt()).isEqualTo(NOW);
        assertThat(p.deployedAt()).isEqualTo(NOW);
        assertThat(p.recordedBy()).isEqualTo("acc-1");
    }

    @Test
    void decisionRejectsADeploymentTimeBeyondTheFutureTolerance() {
        Function<PromotionHistory, Promotion> decision = capturedDecision(
                new PromotionDraft(dev.id(), PromotionOutcome.SUCCESS, NOW.plusSeconds(600), null));

        assertThatThrownBy(() -> decision.apply(frozen))
                .isInstanceOfSatisfying(PromotionRejectedException.class,
                        e -> assertThat(e.reason()).isEqualTo(PromotionRejection.PROMOTION_DEPLOYED_AT_INVALID));
    }

    @Test
    void recordReturnsTheHistoryLoadedAfterTheInsert() {
        when(repository.load(releaseId)).thenReturn(frozen);

        PromotionHistory result = service.record(releaseId,
                new PromotionDraft(dev.id(), PromotionOutcome.SUCCESS, null, null), "acc-1");

        assertThat(result).isSameAs(frozen);
        verify(repository).appendUnderLock(eq(releaseId), any());
    }

    @Test
    void historyDelegatesToLoad() {
        when(repository.load(releaseId)).thenReturn(frozen);

        assertThat(service.history(releaseId)).isSameAs(frozen);
    }
}
