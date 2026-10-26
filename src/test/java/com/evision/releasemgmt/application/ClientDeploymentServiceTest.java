package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientDeployment;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentDraft;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentHistory;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;
import com.evision.releasemgmt.domain.portfolio.ReleaseProduct;
import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.ports.ClientDeploymentRepository;
import com.evision.releasemgmt.ports.PromotionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ClientDeploymentServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-12T10:00:00Z");

    private final ClientDeploymentRepository deployments = mock(ClientDeploymentRepository.class);
    private final PromotionRepository promotions = mock(PromotionRepository.class);
    private final ClientDeploymentService service =
            new ClientDeploymentService(deployments, promotions, Clock.fixed(NOW, ZoneOffset.UTC));

    private final UUID releaseId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();
    private final Client client = new Client(UUID.randomUUID(), "Acme", null, Set.of(productId));
    private final ClientDeploymentHistory frozen = new ClientDeploymentHistory(releaseId, true,
            List.of(new ReleaseProduct(productId, "Core", false, false)), List.of(client), List.of());

    @SuppressWarnings("unchecked")
    private Function<ClientDeploymentHistory, List<ClientDeployment>> capturedDecision() {
        ArgumentCaptor<Function<ClientDeploymentHistory, List<ClientDeployment>>> captor =
                ArgumentCaptor.forClass(Function.class);
        verify(deployments).appendUnderLock(eq(releaseId), captor.capture());
        return captor.getValue();
    }

    @Test
    void recordStampsTheClockAndTheActorAndReturnsTheReloadedHistory() {
        when(deployments.load(releaseId)).thenReturn(frozen);

        ClientDeploymentHistory result = service.record(releaseId,
                new ClientDeploymentDraft(productId, ClientDeploymentOutcome.SUCCESS, null, null),
                List.of(client.id()), "acc-1");

        assertThat(result).isSameAs(frozen);
        List<ClientDeployment> rows = capturedDecision().apply(frozen);
        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.recordedAt()).isEqualTo(NOW);
            assertThat(row.installedAt()).isEqualTo(NOW);
            assertThat(row.recordedBy()).isEqualTo("acc-1");
            assertThat(row.clientId()).isEqualTo(client.id());
        });
    }

    @Test
    void correctWrapsTheSupersedingRowAndStampsTheActor() {
        ClientDeployment original = new ClientDeployment(UUID.randomUUID(), releaseId, productId, client.id(),
                ClientDeploymentOutcome.SUCCESS, NOW.minusSeconds(60), NOW.minusSeconds(30), "acc-0", null, null, null);
        ClientDeploymentHistory withOriginal = new ClientDeploymentHistory(releaseId, true, frozen.products(),
                frozen.clients(), List.of(original));
        when(deployments.load(releaseId)).thenReturn(withOriginal);

        service.correct(releaseId, original.id(), ClientDeploymentOutcome.VOIDED, NOW.minusSeconds(60), null,
                "wrong client", "acc-1");

        assertThat(capturedDecision().apply(withOriginal)).singleElement().satisfies(row -> {
            assertThat(row.supersedesId()).isEqualTo(original.id());
            assertThat(row.recordedBy()).isEqualTo("acc-1");
            assertThat(row.correctionReason()).isEqualTo("wrong client");
            assertThat(row.outcome()).isEqualTo(ClientDeploymentOutcome.VOIDED);
        });
    }

    @Test
    void reachedProductionReflectsAnEffectiveSuccessOnTheProductionStage() {
        Stage prod = new Stage(UUID.randomUUID(), "PROD", 1, true, null);
        Promotion success = new Promotion(UUID.randomUUID(), releaseId, prod.id(), PromotionOutcome.SUCCESS,
                NOW.minusSeconds(60), NOW.minusSeconds(30), "acc-1", null, null, null);
        when(promotions.load(releaseId)).thenReturn(
                new PromotionHistory(releaseId, true, NOW.minusSeconds(3600), List.of(prod), List.of(success)));
        assertThat(service.reachedProduction(releaseId)).isTrue();

        UUID other = UUID.randomUUID();
        when(promotions.load(other)).thenReturn(
                new PromotionHistory(other, true, NOW.minusSeconds(3600), List.of(prod), List.of()));
        assertThat(service.reachedProduction(other)).isFalse();
    }

    @Test
    void historyIsReadFromTheRepository() {
        when(deployments.load(releaseId)).thenReturn(frozen);
        assertThat(service.history(releaseId)).isSameAs(frozen);
    }
}
