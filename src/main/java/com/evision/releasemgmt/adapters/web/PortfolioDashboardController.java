package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.PortfolioDashboardResponse;
import com.evision.releasemgmt.application.PortfolioDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard/portfolio")
public class PortfolioDashboardController {

    private final PortfolioDashboardService service;

    public PortfolioDashboardController(PortfolioDashboardService service) {
        this.service = service;
    }

    @GetMapping("/summary")
    public PortfolioDashboardResponse.Summary summary(@RequestParam(required = false) List<UUID> productIds,
                                                      @RequestParam(defaultValue = "false") boolean includeArchived) {
        PortfolioDashboardService.Summary s = service.summary(DashboardController.productScope(productIds), includeArchived);
        PortfolioDashboardService.Tiles t = s.tiles();
        return new PortfolioDashboardResponse.Summary(
                new PortfolioDashboardResponse.Tiles(t.clientsBehind(), t.notInstalled(), t.activeProducts(),
                        t.activeClients(), t.installsLast30Days()),
                s.products().stream().map(PortfolioDashboardController::toRow).toList(),
                toCounts(s.totals()), toCounts(s.unassigned()));
    }

    @GetMapping("/behind")
    public PortfolioDashboardResponse.Behind behind(@RequestParam(required = false) List<UUID> productIds,
                                                    @RequestParam(defaultValue = "false") boolean includeArchived) {
        PortfolioDashboardService.Behind b = service.behind(DashboardController.productScope(productIds), includeArchived);
        return new PortfolioDashboardResponse.Behind(
                b.behind().stream().map(r -> new PortfolioDashboardResponse.BehindRow(r.clientId(), r.clientName(),
                        r.clientArchived(), r.productId(), r.productName(), toRun(r.runs()), r.releasesBehind(),
                        toBrief(r.newest()))).toList(),
                b.notInstalled().stream().map(r -> new PortfolioDashboardResponse.NotInstalledRow(r.clientId(),
                        r.clientName(), r.clientArchived(), r.productId(), r.productName(), toBrief(r.newest()))).toList());
    }

    @GetMapping("/matrix")
    public PortfolioDashboardResponse.Matrix matrix(@RequestParam(required = false) List<UUID> productIds,
                                                    @RequestParam(defaultValue = "false") boolean includeArchived) {
        PortfolioDashboardService.Matrix m = service.matrix(DashboardController.productScope(productIds), includeArchived);
        return new PortfolioDashboardResponse.Matrix(
                m.products().stream().map(PortfolioDashboardController::toColumn).toList(),
                m.clients().stream().map(PortfolioDashboardController::toColumn).toList(),
                m.cells().stream().map(c -> new PortfolioDashboardResponse.Cell(c.clientId(), c.productId(),
                        toRun(c.run()), c.releasesBehind(), toBrief(c.newest()), c.upToDate())).toList());
    }

    private static PortfolioDashboardResponse.ProductRow toRow(PortfolioDashboardService.ProductRow p) {
        return new PortfolioDashboardResponse.ProductRow(p.id(), p.name(), p.archived(), toCounts(p.releases()),
                toBrief(p.newestFrozen()), p.linkedClients(), p.clientsOnNewest());
    }

    private static PortfolioDashboardResponse.Column toColumn(PortfolioDashboardService.Column c) {
        return new PortfolioDashboardResponse.Column(c.id(), c.name(), c.archived());
    }

    private static PortfolioDashboardResponse.StatusCounts toCounts(PortfolioDashboardService.StatusCounts c) {
        return c == null ? null : new PortfolioDashboardResponse.StatusCounts(c.open(), c.frozen(), c.inProduction());
    }

    private static PortfolioDashboardResponse.ReleaseBrief toBrief(PortfolioDashboardService.ReleaseBrief b) {
        return b == null ? null
                : new PortfolioDashboardResponse.ReleaseBrief(b.releaseId(), b.name(), b.version(), b.frozenAt());
    }

    private static PortfolioDashboardResponse.RunBrief toRun(PortfolioDashboardService.RunBrief r) {
        return r == null ? null : new PortfolioDashboardResponse.RunBrief(r.releaseId(), r.name(), r.version(),
                r.installedAt(), r.inProduction());
    }
}
