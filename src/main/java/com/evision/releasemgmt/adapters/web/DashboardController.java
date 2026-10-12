package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.DashboardResponse;
import com.evision.releasemgmt.adapters.web.dto.DeploymentPageResponse;
import com.evision.releasemgmt.adapters.web.dto.StageResponse;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.DashboardService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.domain.dashboard.DeploymentRow;
import com.evision.releasemgmt.domain.dashboard.ReleaseChurn;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    // Must equal MAX_HISTORY_SPAN_DAYS in frontend/src/api/dashboard.ts (DashboardSpanContractTest keeps them in step).
    static final int MAX_HISTORY_SPAN_DAYS = 366;
    static final int MAX_PAGE_SIZE = 100;
    // The browser sends local-midnight bounds, so one daylight-saving change inside the range adds an hour.
    private static final Duration DST_SLACK = Duration.ofHours(1);

    private final DashboardService service;
    private final ActorResolutionService actorResolution;

    public DashboardController(DashboardService service, ActorResolutionService actorResolution) {
        this.service = service;
        this.actorResolution = actorResolution;
    }

    @GetMapping("/releases")
    public DashboardResponse releases(@RequestParam(defaultValue = "active") String scope) {
        boolean all = switch (scope) {
            case "active" -> false;
            case "all" -> true;
            default -> throw invalid("scope must be 'active' or 'all'");
        };
        return toResponse(service.releases(all));
    }

    @GetMapping("/deployments")
    public DeploymentPageResponse deployments(@RequestParam(required = false) UUID stageId,
                                              @RequestParam Instant from, @RequestParam Instant to,
                                              @RequestParam(defaultValue = "50") int limit,
                                              @RequestParam(defaultValue = "0") int offset) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw invalid("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (offset < 0) {
            throw invalid("offset must not be negative");
        }
        if (!from.isBefore(to)) {
            throw invalid("from must be before to");
        }
        if (Duration.between(from, to).compareTo(Duration.ofDays(MAX_HISTORY_SPAN_DAYS).plus(DST_SLACK)) > 0) {
            throw invalid("the range must not exceed " + MAX_HISTORY_SPAN_DAYS + " days");
        }
        DashboardService.DeploymentPage page = service.deployments(stageId, from, to, limit, offset);
        // D-05.3-05: resolved once per response, never per row.
        ResolvedActors actors = actorResolution.resolve(
                page.entries().stream().map(DeploymentRow::recordedBy).distinct().toArray(String[]::new));
        return new DeploymentPageResponse(
                page.entries().stream().map(row -> toEntry(row, actors)).toList(), page.hasMore());
    }

    private static ErrorResponseException invalid(String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Request validation failed");
        problem.setProperty("reason", "VALIDATION_FAILED");
        return new ErrorResponseException(HttpStatus.BAD_REQUEST, problem, null);
    }

    private static DeploymentPageResponse.Entry toEntry(DeploymentRow row, ResolvedActors actors) {
        return new DeploymentPageResponse.Entry(row.promotionId(), row.releaseId(), row.releaseName(),
                row.releaseVersion(), row.stageId(), row.stageName(), row.stageRetired(), row.outcome(),
                row.deployedAt(), actors.display(row.recordedBy()), row.note(), row.corrected());
    }

    private static DashboardResponse toResponse(DashboardService.DashboardView view) {
        DashboardService.Summary s = view.summary();
        DashboardResponse.Summary summary = new DashboardResponse.Summary(s.visibleReleases(), s.totalReleases(),
                s.medianCreatedToFrozenSeconds(), s.createdToFrozenCount(),
                s.medianFrozenToProductionSeconds(), s.frozenToProductionCount(),
                s.releasesPerStage().entrySet().stream()
                        .map(e -> new DashboardResponse.StageCount(e.getKey(), e.getValue())).toList(),
                s.notPromoted());
        return new DashboardResponse(summary,
                view.stages().stream()
                        .map(st -> new StageResponse(st.id(), st.name(), st.position(), st.production(), st.retired()))
                        .toList(),
                view.releases().stream().map(DashboardController::toRow).toList());
    }

    private static DashboardResponse.ReleaseRow toRow(DashboardService.ReleaseMetrics m) {
        var r = m.release();
        ReleaseChurn c = m.churn();
        return new DashboardResponse.ReleaseRow(r.id(), r.name(), r.version(), r.status().name(), r.createdAt(),
                r.frozenAt(), m.currentStageId(), m.shippedAt(), m.lastFailedStageId(),
                m.createdToFrozenSeconds(), m.frozenToProductionSeconds(),
                new DashboardResponse.Composition(c.tickets(), r.manualCount(), c.linked(), c.noLink(), c.failed(),
                        c.noData()),
                new DashboardResponse.Churn(c.medianCommitsToDone(), c.commitNoData(), c.reopens(), c.reopenNoData(),
                        c.postDoneShare(), c.outliers(), c.keyTagged()));
    }
}
