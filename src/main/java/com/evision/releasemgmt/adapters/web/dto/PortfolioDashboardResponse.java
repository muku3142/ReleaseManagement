package com.evision.releasemgmt.adapters.web.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Field names are the contract the frontend types against (plan 16-04); they mirror PortfolioDashboardService.
public final class PortfolioDashboardResponse {

    private PortfolioDashboardResponse() {
    }

    public record ReleaseBrief(UUID releaseId, String name, String version, Instant frozenAt) {}

    public record RunBrief(UUID releaseId, String name, String version, Instant installedAt, boolean inProduction) {}

    public record StatusCounts(int open, int frozen, int inProduction) {}

    public record ProductRow(UUID id, String name, boolean archived, StatusCounts releases, ReleaseBrief newestFrozen,
                             int linkedClients, int clientsOnNewest) {}

    public record Tiles(int clientsBehind, int notInstalled, int activeProducts, int activeClients,
                        int installsLast30Days) {}

    public record Summary(Tiles tiles, List<ProductRow> products, StatusCounts totals, StatusCounts unassigned) {}

    public record BehindRow(UUID clientId, String clientName, boolean clientArchived, UUID productId, String productName,
                            RunBrief runs, int releasesBehind, ReleaseBrief newest) {}

    public record NotInstalledRow(UUID clientId, String clientName, boolean clientArchived, UUID productId,
                                  String productName, ReleaseBrief newest) {}

    public record Behind(List<BehindRow> behind, List<NotInstalledRow> notInstalled) {}

    public record Column(UUID id, String name, boolean archived) {}

    public record Cell(UUID clientId, UUID productId, RunBrief run, Integer releasesBehind, ReleaseBrief newest,
                       boolean upToDate) {}

    public record Matrix(List<Column> products, List<Column> clients, List<Cell> cells) {}
}
