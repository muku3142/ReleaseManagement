package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.RoadmapDetailsRequest;
import com.evision.releasemgmt.adapters.web.dto.RoadmapHistoryResponse;
import com.evision.releasemgmt.adapters.web.dto.RoadmapInputsRequest;
import com.evision.releasemgmt.adapters.web.dto.RoadmapItemResponse;
import com.evision.releasemgmt.adapters.web.dto.RoadmapJiraAddRequest;
import com.evision.releasemgmt.adapters.web.dto.RoadmapListResponse;
import com.evision.releasemgmt.adapters.web.dto.RoadmapManualItemRequest;
import com.evision.releasemgmt.adapters.web.dto.RoadmapReleaseAddRequest;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.ProductService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.application.RoadmapImportService;
import com.evision.releasemgmt.application.RoadmapService;
import com.evision.releasemgmt.domain.portfolio.Product;
import com.evision.releasemgmt.domain.roadmap.InputChange;
import com.evision.releasemgmt.domain.roadmap.RankedItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapAddResult;
import com.evision.releasemgmt.domain.roadmap.RoadmapFilter;
import com.evision.releasemgmt.domain.roadmap.RoadmapItem;
import com.evision.releasemgmt.domain.roadmap.RoadmapItemDetails;
import com.evision.releasemgmt.domain.roadmap.RoadmapRefreshResult;
import com.evision.releasemgmt.domain.roadmap.RoadmapStatus;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

// The actor is always the principal: every /api/** path is authenticated, so principal is never null here.
@RestController
@RequestMapping("/api/roadmap")
public class RoadmapController {

    private static final String CSV_HEADER = "Rank,Jira key,Title,Score,Reach (clients),Impact,Confidence (%),"
            + "Effort (person-weeks),Product,Status,Target quarter,Owner,Jira status,Jira fetched at,"
            + "Unavailable in Jira,Archived";

    private final RoadmapService service;
    private final RoadmapImportService importService;
    private final ProductService productService;
    private final ActorResolutionService actorResolution;
    private final Clock clock;

    public RoadmapController(RoadmapService service, RoadmapImportService importService,
                             ProductService productService, ActorResolutionService actorResolution, Clock clock) {
        this.service = service;
        this.importService = importService;
        this.productService = productService;
        this.actorResolution = actorResolution;
        this.clock = clock;
    }

    @GetMapping("/items")
    public RoadmapListResponse list(@RequestParam(required = false) List<UUID> productIds,
                                    @RequestParam(defaultValue = "false") boolean done,
                                    @RequestParam(defaultValue = "false") boolean archived) {
        Map<UUID, Product> products = productsById();
        return new RoadmapListResponse(service.ranked(filter(productIds, done, archived)).stream()
                .map(ranked -> RoadmapItemResponse.of(ranked.item(), ranked.score(), ranked.rank(), products))
                .toList());
    }

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    public RoadmapItemResponse addManual(@Valid @RequestBody RoadmapManualItemRequest request,
                                         Principal principal) {
        return single(service.addManual(request.title(), request.productId(), principal.getName()));
    }

    @PostMapping("/items/jira")
    public RoadmapAddResult addFromJira(@Valid @RequestBody RoadmapJiraAddRequest request, Principal principal) {
        return importService.addFromJira(request.keys(), principal.getName());
    }

    @PostMapping("/items/from-release")
    public RoadmapAddResult addFromRelease(@Valid @RequestBody RoadmapReleaseAddRequest request,
                                           Principal principal) {
        return importService.addFromRelease(request.releaseId(), request.ticketKeys(), principal.getName());
    }

    @PutMapping("/items/{itemId}/inputs")
    public RoadmapItemResponse setInputs(@PathVariable UUID itemId, @RequestBody RoadmapInputsRequest request,
                                         Principal principal) {
        return single(service.setInputs(itemId, request.toInputs(), principal.getName()));
    }

    @PutMapping("/items/{itemId}/details")
    public RoadmapItemResponse updateDetails(@PathVariable UUID itemId,
                                             @Valid @RequestBody RoadmapDetailsRequest request) {
        String title = request.title() == null || request.title().isBlank() ? null : request.title();
        return single(service.updateDetails(itemId, new RoadmapItemDetails(title, request.status(),
                request.productId(), request.targetQuarter(), request.owner(), request.notes())));
    }

    @PostMapping("/items/{itemId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID itemId) {
        service.archive(itemId);
    }

    @PostMapping("/items/{itemId}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restore(@PathVariable UUID itemId) {
        service.restore(itemId);
    }

    @GetMapping("/items/{itemId}/history")
    public RoadmapHistoryResponse history(@PathVariable UUID itemId) {
        List<InputChange> changes = service.history(itemId);
        ResolvedActors actors = actorResolution.resolve(
                changes.stream().map(InputChange::changedBy).distinct().toArray(String[]::new));
        return new RoadmapHistoryResponse(changes.stream()
                .map(c -> new RoadmapHistoryResponse.Entry(c.field(), c.oldValue(), c.newValue(), c.changedAt(),
                        actors.display(c.changedBy())))
                .toList());
    }

    @PostMapping("/refresh")
    public RoadmapRefreshResult refreshAll() {
        return importService.refreshAll();
    }

    @PostMapping("/items/{itemId}/refresh")
    public RoadmapRefreshResult refresh(@PathVariable UUID itemId) {
        return importService.refresh(itemId);
    }

    @GetMapping("/export.csv")
    public ResponseEntity<byte[]> exportCsv(@RequestParam(required = false) List<UUID> productIds,
                                            @RequestParam(defaultValue = "false") boolean done,
                                            @RequestParam(defaultValue = "false") boolean archived) {
        Map<UUID, Product> products = productsById();
        List<String> rows = new ArrayList<>();
        rows.add(CSV_HEADER);
        for (RankedItem ranked : service.ranked(filter(productIds, done, archived))) {
            rows.add(csvRow(RoadmapItemResponse.of(ranked.item(), ranked.score(), ranked.rank(), products)));
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=utf-8")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ExportFilename.attachment("roadmap-" + LocalDate.now(clock) + ".csv"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(CsvWriter.document(rows));
    }

    private static String csvRow(RoadmapItemResponse item) {
        return CsvWriter.row(List.of(
                item.rank() == null ? "" : item.rank().toString(),
                CsvWriter.text(item.jiraKey()),
                CsvWriter.text(item.title()),
                CsvWriter.text(item.scoreDisplay()),
                item.reach() == null ? "" : item.reach().toString(),
                CsvWriter.number(item.impact()),
                item.confidence() == null ? "" : item.confidence().toString(),
                CsvWriter.number(item.effort()),
                CsvWriter.text(item.product() == null ? null : item.product().name()),
                statusLabel(item.status()),
                CsvWriter.text(item.targetQuarter()),
                CsvWriter.text(item.owner()),
                CsvWriter.text(item.jiraStatus()),
                item.jiraSyncedAt() == null ? "" : item.jiraSyncedAt().toString(),
                yesNo(item.jiraUnavailable()),
                yesNo(item.archived())));
    }

    private static String statusLabel(RoadmapStatus status) {
        return switch (status) {
            case IDEA -> "Idea";
            case PLANNED -> "Planned";
            case IN_PROGRESS -> "In progress";
            case DONE -> "Done";
        };
    }

    private static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    private static RoadmapFilter filter(List<UUID> productIds, boolean done, boolean archived) {
        return new RoadmapFilter(DashboardController.productScope(productIds), done, archived);
    }

    private RoadmapItemResponse single(RoadmapItem item) {
        return RoadmapItemResponse.unranked(item, productsById());
    }

    private Map<UUID, Product> productsById() {
        return productService.list().stream().collect(Collectors.toMap(Product::id, Function.identity()));
    }
}
