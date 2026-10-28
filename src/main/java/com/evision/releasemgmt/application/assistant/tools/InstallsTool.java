package com.evision.releasemgmt.application.assistant.tools;

import com.evision.releasemgmt.application.PortfolioDashboardService;
import com.evision.releasemgmt.application.PortfolioDashboardService.Cell;
import com.evision.releasemgmt.application.PortfolioDashboardService.Column;
import com.evision.releasemgmt.application.PortfolioDashboardService.Matrix;
import com.evision.releasemgmt.application.PortfolioDashboardService.RunBrief;
import com.evision.releasemgmt.application.ReleaseService;
import com.evision.releasemgmt.domain.release.Release;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@ConditionalOnProperty(prefix = "app.assistant", name = "enabled", havingValue = "true")
public class InstallsTool implements AssistantTool {

    static final int NAME_MAX_LENGTH = 100;
    static final int MAX_ROWS = 25;
    private static final int MAX_CANDIDATES = 10;

    public enum RunState { RUNS, NOT_INSTALLED, NOT_LINKED }

    public record ProductRun(String product, RunState state, String release, String version, Instant installedAt,
                             Boolean inProduction, Integer releasesBehind, String newestRelease) {
    }

    public record ClientRuns(String client, boolean archived, List<ProductRun> products) {
    }

    public record Runner(String client, boolean archived, String product, Instant installedAt) {
    }

    public record ReleaseRunners(String release, String version, Boolean inProduction, int total, List<Runner> clients,
                                 boolean truncated) {
    }

    private final PortfolioDashboardService portfolio;
    private final ReleaseService releaseService;
    private final ReleaseRef releaseRef;

    public InstallsTool(PortfolioDashboardService portfolio, ReleaseService releaseService, ReleaseRef releaseRef) {
        this.portfolio = portfolio;
        this.releaseService = releaseService;
        this.releaseRef = releaseRef;
    }

    @Override
    public String name() {
        return "installs";
    }

    @Override
    public String description() {
        return "Installs: clients currently running `release`, or what `client` runs (optionally for one `product`).";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object",
                "properties", Map.of(
                        "release", Map.of("type", "string", "maxLength", ReleaseRef.MAX_LENGTH),
                        "client", Map.of("type", "string", "maxLength", NAME_MAX_LENGTH),
                        "product", Map.of("type", "string", "maxLength", NAME_MAX_LENGTH)),
                "additionalProperties", false);
    }

    @Override
    public ToolOutput call(Map<String, Object> arguments) {
        Object release = arguments.get("release");
        Object client = arguments.get("client");
        if (release == null && client == null) {
            throw new ToolArgumentException("give `release`, or `client` with an optional `product`");
        }
        Matrix matrix = portfolio.matrix(Set.of(), true);
        if (release != null) {
            UUID id = releaseRef.resolve(release);
            return new ToolOutput(runners(matrix, releaseService.get(id)), "installs:" + id,
                    "ReleaseManagement: client installs");
        }
        Column asked = find(matrix.clients(), name(client, "client"), "client");
        Object product = arguments.get("product");
        Column askedProduct = product == null ? null : find(matrix.products(), name(product, "product"), "product");
        return new ToolOutput(clientRuns(matrix, asked, askedProduct), "installs:client:" + asked.id(),
                "ReleaseManagement: client installs");
    }

    private static ReleaseRunners runners(Matrix matrix, Release release) {
        Map<UUID, Column> clients = matrix.clients().stream().collect(Collectors.toMap(Column::id, c -> c));
        Map<UUID, Column> products = matrix.products().stream().collect(Collectors.toMap(Column::id, p -> p));
        List<Cell> running = matrix.cells().stream()
                .filter(c -> c.run() != null && c.run().releaseId().equals(release.id())).toList();
        List<Runner> runners = running.stream()
                .map(c -> new Runner(clients.get(c.clientId()).name(), clients.get(c.clientId()).archived(),
                        products.get(c.productId()).name(), c.run().installedAt()))
                .sorted(Comparator.comparing(Runner::client, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Runner::product, String.CASE_INSENSITIVE_ORDER))
                .toList();
        Boolean inProduction = running.isEmpty() ? null : running.getFirst().run().inProduction();
        return new ReleaseRunners(release.name(), release.version(), inProduction, runners.size(),
                runners.stream().limit(MAX_ROWS).toList(), runners.size() > MAX_ROWS);
    }

    private static ClientRuns clientRuns(Matrix matrix, Column client, Column askedProduct) {
        Map<UUID, Column> products = matrix.products().stream().collect(Collectors.toMap(Column::id, p -> p));
        List<Cell> cells = matrix.cells().stream().filter(c -> c.clientId().equals(client.id())).toList();
        if (askedProduct != null) {
            ProductRun run = cells.stream().filter(c -> c.productId().equals(askedProduct.id())).findFirst()
                    .map(c -> productRun(askedProduct.name(), c))
                    .orElseGet(() -> new ProductRun(askedProduct.name(), RunState.NOT_LINKED, null, null, null, null,
                            null, null));
            return new ClientRuns(client.name(), client.archived(), List.of(run));
        }
        return new ClientRuns(client.name(), client.archived(),
                cells.stream().map(c -> productRun(products.get(c.productId()).name(), c)).toList());
    }

    private static ProductRun productRun(String product, Cell cell) {
        RunBrief run = cell.run();
        if (run == null) {
            return new ProductRun(product, RunState.NOT_INSTALLED, null, null, null, null, null, null);
        }
        String newest = cell.newest() == null ? null : cell.newest().name() + " " + cell.newest().version();
        return new ProductRun(product, RunState.RUNS, run.name(), run.version(), run.installedAt(), run.inProduction(),
                cell.releasesBehind(), newest);
    }

    private static String name(Object value, String argument) {
        String text = value.toString().trim();
        if (text.isEmpty() || text.length() > NAME_MAX_LENGTH) {
            throw new ToolArgumentException(argument + " must be 1 to " + NAME_MAX_LENGTH + " characters");
        }
        return text;
    }

    private static Column find(List<Column> columns, String name, String kind) {
        return columns.stream().filter(c -> c.name().toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT)))
                .findFirst()
                .orElseThrow(() -> new ToolArgumentException("unknown " + kind + " '" + name + "', known " + kind
                        + "s: " + columns.stream().limit(MAX_CANDIDATES).map(Column::name)
                        .collect(Collectors.joining(", "))));
    }
}
