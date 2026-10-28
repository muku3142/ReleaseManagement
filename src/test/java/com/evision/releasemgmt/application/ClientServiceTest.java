package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientDeployment;
import com.evision.releasemgmt.domain.portfolio.ClientDeploymentOutcome;
import com.evision.releasemgmt.domain.portfolio.ClientInstall;
import com.evision.releasemgmt.domain.portfolio.ClientNotFoundException;
import com.evision.releasemgmt.domain.portfolio.CurrentRun;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.ports.ClientDeploymentRepository;
import com.evision.releasemgmt.ports.ClientRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import com.evision.releasemgmt.ports.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClientServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");

    @Mock
    private ClientRepository clients;
    @Mock
    private ProductRepository products;
    @Mock
    private ClientDeploymentRepository deployments;
    @Mock
    private PromotionRepository promotions;

    private ClientService service;

    private final Product alpha = new Product(UUID.randomUUID(), "alpha", null);
    private final Product beta = new Product(UUID.randomUUID(), "Beta", Instant.parse("2026-01-01T00:00:00Z"));
    private final UUID clientId = UUID.randomUUID();
    private final UUID releaseId = UUID.randomUUID();

    @BeforeEach
    void service() {
        service = new ClientService(clients, products, deployments, promotions, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ClientDeployment entry(UUID id, UUID productId, UUID supersedesId) {
        return new ClientDeployment(id, releaseId, productId, clientId, ClientDeploymentOutcome.SUCCESS, NOW, NOW,
                "acc", null, supersedesId, supersedesId == null ? null : "typo");
    }

    private void reachesProduction(boolean reached) {
        PromotionHistory history = mock(PromotionHistory.class);
        when(history.firstProductionSuccess()).thenReturn(reached ? Optional.of(NOW) : Optional.empty());
        when(promotions.load(releaseId)).thenReturn(history);
    }

    @Test
    void runsAreOrderedByProductNameAndAProductWithoutInstallsIsNotInstalled() {
        Client client = new Client(clientId, "Acme", null, Set.of(beta.id(), alpha.id()));
        when(clients.findById(clientId)).thenReturn(Optional.of(client));
        when(products.findAll()).thenReturn(List.of(beta, alpha));
        ClientDeployment first = entry(UUID.randomUUID(), alpha.id(), null);
        when(deployments.historyOfClient(clientId)).thenReturn(List.of(new ClientInstall(first, "R1", "1.0")));
        when(deployments.currentRunsOfClient(clientId))
                .thenReturn(List.of(new CurrentRun(alpha.id(), releaseId, "R1", "1.0", NOW)));
        reachesProduction(true);

        ClientDetail detail = service.detail(clientId);

        assertThat(detail.runs()).extracting(r -> r.product().name()).containsExactly("alpha", "Beta");
        assertThat(detail.runs().get(0).hasInstalls()).isTrue();
        assertThat(detail.runs().get(0).current().releaseId()).isEqualTo(releaseId);
        assertThat(detail.runs().get(0).currentInProduction()).isTrue();
        assertThat(detail.runs().get(1).hasInstalls()).isFalse();
        assertThat(detail.runs().get(1).current()).isNull();
        assertThat(detail.runs().get(1).currentInProduction()).isFalse();
        assertThat(detail.runs().get(1).product().archived()).isTrue();
    }

    @Test
    void promotionHistoryIsLoadedOncePerDistinctRelease() {
        Client client = new Client(clientId, "Acme", null, Set.of(alpha.id()));
        when(clients.findById(clientId)).thenReturn(Optional.of(client));
        when(products.findAll()).thenReturn(List.of(alpha));
        when(deployments.historyOfClient(clientId)).thenReturn(List.of(
                new ClientInstall(entry(UUID.randomUUID(), alpha.id(), null), "R1", "1.0"),
                new ClientInstall(entry(UUID.randomUUID(), alpha.id(), null), "R1", "1.0")));
        when(deployments.currentRunsOfClient(clientId)).thenReturn(List.of());
        reachesProduction(false);

        ClientDetail detail = service.detail(clientId);

        assertThat(detail.history()).allMatch(h -> !h.inProduction());
        verify(promotions, org.mockito.Mockito.times(1)).load(releaseId);
    }

    @Test
    void aSupersededRowPointsAtItsCorrection() {
        Client client = new Client(clientId, "Acme", null, Set.of(alpha.id()));
        when(clients.findById(clientId)).thenReturn(Optional.of(client));
        when(products.findAll()).thenReturn(List.of(alpha));
        UUID originalId = UUID.randomUUID();
        UUID correctionId = UUID.randomUUID();
        when(deployments.historyOfClient(clientId)).thenReturn(List.of(
                new ClientInstall(entry(correctionId, alpha.id(), originalId), "R1", "1.0"),
                new ClientInstall(entry(originalId, alpha.id(), null), "R1", "1.0")));
        when(deployments.currentRunsOfClient(clientId)).thenReturn(List.of());
        reachesProduction(false);

        ClientDetail detail = service.detail(clientId);

        assertThat(detail.history().get(0).effective()).isTrue();
        assertThat(detail.history().get(1).effective()).isFalse();
        assertThat(detail.history().get(1).supersededById()).isEqualTo(correctionId);
    }

    @Test
    void anArchivedProductStillHasARunForItsClient() {
        Client client = new Client(clientId, "Acme", null, Set.of(beta.id()));
        when(clients.findById(clientId)).thenReturn(Optional.of(client));
        when(products.findAll()).thenReturn(List.of(beta));
        when(deployments.historyOfClient(clientId)).thenReturn(List.of());
        when(deployments.currentRunsOfClient(clientId)).thenReturn(List.of());

        assertThat(service.detail(clientId).runs()).hasSize(1);
    }

    @Test
    void detailOfAnUnknownClientIsNotFound() {
        when(clients.findById(clientId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.detail(clientId)).isInstanceOf(ClientNotFoundException.class);
    }

    @Test
    void archivingAnArchivedClientDoesNothing() {
        when(clients.findById(clientId)).thenReturn(Optional.of(new Client(clientId, "Acme", NOW, Set.of())));

        service.archive(clientId);

        verify(clients, never()).setArchivedAt(any(), any());
    }

    @Test
    void unarchivingAnActiveClientDoesNothing() {
        when(clients.findById(clientId)).thenReturn(Optional.of(new Client(clientId, "Acme", null, Set.of())));

        service.unarchive(clientId);

        verify(clients, never()).setArchivedAt(any(), any());
    }

    @Test
    void archivingStampsTheClockAndNamesAreStripped() {
        when(clients.findById(clientId)).thenReturn(Optional.of(new Client(clientId, "Acme", null, Set.of())));

        service.archive(clientId);
        service.rename(clientId, "  New  ");

        verify(clients).setArchivedAt(clientId, NOW);
        verify(clients).rename(clientId, "New");
        verify(clients, never()).add(anyString());
    }
}
