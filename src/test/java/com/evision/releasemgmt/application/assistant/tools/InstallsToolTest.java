package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.PortfolioDashboardService;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.domain.dashboard.DashboardLedger;
import com.evision.releasemgmt.domain.dashboard.DashboardRelease;
import com.evision.releasemgmt.domain.portfolio.Client;
import com.evision.releasemgmt.domain.portfolio.ClientCurrentRun;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionOutcome;
import com.evision.releasemgmt.domain.promotion.Stage;
import com.evision.releasemgmt.domain.release.Release;
import com.evision.releasemgmt.domain.release.ReleaseStatus;
import com.evision.releasemgmt.ports.ClientRepository;
import com.evision.releasemgmt.ports.DashboardReadRepository;
import com.evision.releasemgmt.ports.PortfolioReadRepository;
import com.evision.releasemgmt.ports.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InstallsToolTest {

    private static final Instant NOW = Instant.parse("2026-10-22T10:00:00Z");
    private static final Instant T1 = NOW.minus(Duration.ofDays(30));
    private static final Instant T2 = NOW.minus(Duration.ofDays(20));
    private static final Instant T3 = NOW.minus(Duration.ofDays(10));

    private final DashboardReadRepository dashboard = mock(DashboardReadRepository.class);
    private final PortfolioReadRepository portfolioRead = mock(PortfolioReadRepository.class);
    private final ProductRepository productRepository = mock(ProductRepository.class);
    private final ClientRepository clientRepository = mock(ClientRepository.class);
    private final PortfolioDashboardService portfolio = new PortfolioDashboardService(dashboard, portfolioRead,
            productRepository, clientRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    private final ReleaseService releaseService = mock(ReleaseService.class);
    private final ReleaseRef releaseRef = mock(ReleaseRef.class);
    private final InstallsTool tool = new InstallsTool(portfolio, releaseService, releaseRef);
    private final JsonMapper mapper = JsonMapper.builder().build();

    private final Stage prod = new Stage(UUID.randomUUID(), "PROD", 1, true, null);
    private final Product alpha = new Product(UUID.randomUUID(), "Alpha", null);
    private final Product mobile = new Product(UUID.randomUUID(), "Mobile", null);
    private final DashboardRelease older = release("Old", T1);
    private final DashboardRelease newer = release("New", T2);
    private final Client acme = new Client(UUID.randomUUID(), "Acme", null, Set.of(alpha.id()));
    private final Client beta = new Client(UUID.randomUUID(), "Beta", null, Set.of(alpha.id(), mobile.id()));
    private final Client gone = new Client(UUID.randomUUID(), "Gone", T1, Set.of(alpha.id()));

    private final List<Client> clients = new ArrayList<>(List.of(acme, beta, gone));
    private final List<ClientCurrentRun> runs = new ArrayList<>();

    private DashboardRelease release(String name, Instant frozenAt) {
        return new DashboardRelease(UUID.randomUUID(), name, "1." + name, ReleaseStatus.FROZEN, T1.minus(Duration.ofDays(1)),
                frozenAt, 0);
    }

    @BeforeEach
    void fixture() {
        Promotion promoted = new Promotion(UUID.randomUUID(), older.id(), prod.id(), PromotionOutcome.SUCCESS, T3, T3,
                "acc", null, null, null);
        when(dashboard.loadLedger()).thenReturn(new DashboardLedger(List.of(older, newer), List.of(prod), List.of(promoted)));
        when(portfolioRead.productsByRelease()).thenReturn(Map.of(older.id(), Set.of(alpha.id()),
                newer.id(), Set.of(alpha.id())));
        when(productRepository.findAll()).thenReturn(List.of(alpha, mobile));
        when(clientRepository.findAll()).thenAnswer(i -> clients);
        when(portfolioRead.currentRuns()).thenAnswer(i -> runs);
        when(portfolioRead.successfulInstallsSince(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        runs.add(new ClientCurrentRun(acme.id(), alpha.id(), older.id(), T3));
        runs.add(new ClientCurrentRun(beta.id(), alpha.id(), older.id(), T2));
    }

    private void resolvesTo(DashboardRelease r) {
        when(releaseRef.resolve("R")).thenReturn(r.id());
        when(releaseService.get(r.id())).thenReturn(new Release(r.id(), r.name(), r.version(), ReleaseStatus.FROZEN, "CNV",
                T1, "a", r.frozenAt(), "a", null, null, List.of(), List.of(), null));
    }

    private JsonNode call(Map<String, Object> arguments) {
        return mapper.valueToTree(tool.call(arguments).result());
    }

    @Test
    void releaseListsItsCurrentRunnersSortedByClientName() {
        resolvesTo(older);

        JsonNode json = call(Map.of("release", "R"));

        assertThat(json.get("release").asText()).isEqualTo("Old");
        assertThat(json.get("inProduction").asBoolean()).isTrue();
        assertThat(json.get("total").asInt()).isEqualTo(2);
        assertThat(json.get("truncated").asBoolean()).isFalse();
        assertThat(json.get("clients").get(0).get("client").asText()).isEqualTo("Acme");
        assertThat(json.get("clients").get(1).get("client").asText()).isEqualTo("Beta");
        PortfolioDashboardService.Cell cell = portfolio.matrix(Set.of(), true).cells().stream()
                .filter(c -> c.clientId().equals(acme.id())).findFirst().orElseThrow();
        assertThat(json.get("clients").get(0).get("installedAt").asText()).isEqualTo(cell.run().installedAt().toString());
    }

    @Test
    void aClientWhoseCurrentReleaseIsNewerIsNotListed() {
        resolvesTo(older);
        runs.set(0, new ClientCurrentRun(acme.id(), alpha.id(), newer.id(), T3));

        JsonNode json = call(Map.of("release", "R"));

        assertThat(json.get("total").asInt()).isEqualTo(1);
        assertThat(json.get("clients").get(0).get("client").asText()).isEqualTo("Beta");
    }

    @Test
    void noRunnersGivesEmptyListAndNullInProduction() {
        resolvesTo(newer);

        JsonNode json = call(Map.of("release", "R"));

        assertThat(json.get("total").asInt()).isZero();
        assertThat(json.get("clients")).isEmpty();
        assertThat(json.get("inProduction").isNull()).isTrue();
    }

    @Test
    void manyRunnersAreCappedWithTotalAndTruncated() {
        resolvesTo(older);
        runs.clear();
        IntStream.range(0, 30).forEach(i -> {
            Client c = new Client(UUID.randomUUID(), String.format("Client%02d", i), null, Set.of(alpha.id()));
            clients.add(c);
            runs.add(new ClientCurrentRun(c.id(), alpha.id(), older.id(), T3));
        });

        JsonNode json = call(Map.of("release", "R"));

        assertThat(json.get("total").asInt()).isEqualTo(30);
        assertThat(json.get("clients")).hasSize(25);
        assertThat(json.get("truncated").asBoolean()).isTrue();
    }

    @Test
    void clientShowsRunsAndNotInstalledPerLinkedProductCaseInsensitively() {
        JsonNode json = call(Map.of("client", "bETa"));

        assertThat(json.get("client").asText()).isEqualTo("Beta");
        assertThat(json.get("archived").asBoolean()).isFalse();
        JsonNode first = json.get("products").get(0);
        assertThat(first.get("product").asText()).isEqualTo("Alpha");
        assertThat(first.get("state").asText()).isEqualTo("RUNS");
        assertThat(first.get("release").asText()).isEqualTo("Old");
        assertThat(first.get("releasesBehind").asInt()).isEqualTo(1);
        assertThat(first.get("newestRelease").asText()).isEqualTo("New 1.New");
        assertThat(first.get("inProduction").asBoolean()).isTrue();
        JsonNode second = json.get("products").get(1);
        assertThat(second.get("product").asText()).isEqualTo("Mobile");
        assertThat(second.get("state").asText()).isEqualTo("NOT_INSTALLED");
        assertThat(second.get("release").isNull()).isTrue();
    }

    @Test
    void productNotLinkedToTheClientIsReportedAsNotLinked() {
        JsonNode json = call(Map.of("client", "Acme", "product", "Mobile"));

        assertThat(json.get("products")).hasSize(1);
        assertThat(json.get("products").get(0).get("product").asText()).isEqualTo("Mobile");
        assertThat(json.get("products").get(0).get("state").asText()).isEqualTo("NOT_LINKED");
        assertThat(json.get("products").get(0).get("release").isNull()).isTrue();
    }

    @Test
    void archivedClientIsFlagged() {
        assertThat(call(Map.of("client", "Gone")).get("archived").asBoolean()).isTrue();
    }

    @Test
    void unknownClientAndProductListKnownNames() {
        assertThatThrownBy(() -> tool.call(Map.of("client", "Nope")))
                .isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("unknown client 'Nope'").hasMessageContaining("Acme");
        assertThatThrownBy(() -> tool.call(Map.of("client", "Acme", "product", "Nope")))
                .isInstanceOf(ToolArgumentException.class)
                .hasMessageContaining("unknown product").hasMessageContaining("Alpha");
    }

    @Test
    void ambiguousReleaseIsPropagatedUnchanged() {
        when(releaseRef.resolve("R")).thenThrow(new ToolArgumentException("'R' is ambiguous"));

        assertThatThrownBy(() -> tool.call(Map.of("release", "R")))
                .isInstanceOf(ToolArgumentException.class).hasMessage("'R' is ambiguous");
    }

    @Test
    void missingOrIncompleteArgumentsAreRejected() {
        assertThatThrownBy(() -> tool.call(Map.of())).isInstanceOf(ToolArgumentException.class)
                .hasMessage("give `release`, or `client` with an optional `product`");
        assertThatThrownBy(() -> tool.call(Map.of("product", "Alpha"))).isInstanceOf(ToolArgumentException.class)
                .hasMessage("give `release`, or `client` with an optional `product`");
    }

    @Test
    void overLongNamesAreRejected() {
        assertThatThrownBy(() -> tool.call(Map.of("client", "x".repeat(101))))
                .isInstanceOf(ToolArgumentException.class).hasMessage("client must be 1 to 100 characters");
        assertThatThrownBy(() -> tool.call(Map.of("client", "Acme", "product", " ")))
                .isInstanceOf(ToolArgumentException.class).hasMessage("product must be 1 to 100 characters");
    }

    @Test
    void scopedChatPassesOnlyTheReleaseArgument() {
        resolvesTo(older);
        when(releaseRef.resolve(older.id().toString())).thenReturn(older.id());

        JsonNode json = call(Map.of("release", older.id().toString()));

        assertThat(json.get("total").asInt()).isEqualTo(2);
    }

    @Test
    void resultFieldNamesAreExactlyTheRecordComponents() {
        resolvesTo(older);

        assertThat(call(Map.of("release", "R")).propertyNames())
                .containsExactlyInAnyOrder("release", "version", "inProduction", "total", "clients", "truncated");
        assertThat(call(Map.of("client", "Acme")).get("products").get(0).propertyNames())
                .containsExactlyInAnyOrder("product", "state", "release", "version", "installedAt", "inProduction",
                        "releasesBehind", "newestRelease");
    }
}
