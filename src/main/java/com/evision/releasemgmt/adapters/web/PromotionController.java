package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.CorrectPromotionRequest;
import com.evision.releasemgmt.adapters.web.dto.PromotionEntryResponse;
import com.evision.releasemgmt.adapters.web.dto.PromotionHistoryResponse;
import com.evision.releasemgmt.adapters.web.dto.RecordPromotionRequest;
import com.evision.releasemgmt.adapters.web.dto.StageResponse;
import com.evision.releasemgmt.application.ActorResolutionService;
import com.evision.releasemgmt.application.PromotionService;
import com.evision.releasemgmt.application.ResolvedActors;
import com.evision.releasemgmt.domain.promotion.Promotion;
import com.evision.releasemgmt.domain.promotion.PromotionDraft;
import com.evision.releasemgmt.domain.promotion.PromotionHistory;
import com.evision.releasemgmt.domain.promotion.Stage;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/releases/{releaseId}/promotions")
public class PromotionController {

    private final PromotionService service;
    private final ActorResolutionService actorResolution;

    public PromotionController(PromotionService service, ActorResolutionService actorResolution) {
        this.service = service;
        this.actorResolution = actorResolution;
    }

    @GetMapping
    public PromotionHistoryResponse history(@PathVariable UUID releaseId) {
        return respond(service.history(releaseId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionHistoryResponse record(@PathVariable UUID releaseId,
                                           @Valid @RequestBody RecordPromotionRequest request,
                                           Principal principal) {
        // Every /api/** path is authenticated() in SecurityConfig, so a principal is guaranteed. The actor
        // always comes from the principal, never from the body (D-07-12).
        PromotionDraft draft = new PromotionDraft(request.stageId(), request.outcome(), request.deployedAt(),
                request.note());
        return respond(service.record(releaseId, draft, principal.getName()));
    }

    @PostMapping("/{promotionId}/corrections")
    @ResponseStatus(HttpStatus.CREATED)
    public PromotionHistoryResponse correct(@PathVariable UUID releaseId,
                                            @PathVariable UUID promotionId,
                                            @Valid @RequestBody CorrectPromotionRequest request,
                                            Principal principal) {
        PromotionDraft draft = new PromotionDraft(request.stageId(), request.outcome(), request.deployedAt(),
                request.note());
        return respond(service.correct(releaseId, promotionId, draft, request.reason(), principal.getName()));
    }

    private PromotionHistoryResponse respond(PromotionHistory history) {
        // D-05.3-05 / D-07-12: resolved once per response, never per row.
        ResolvedActors actors = actorResolution.resolve(
                history.entries().stream().map(Promotion::recordedBy).toArray(String[]::new));
        return toResponse(history, actors);
    }

    static PromotionHistoryResponse toResponse(PromotionHistory history, ResolvedActors actors) {
        // Looked up by id at read time, so renaming a stage relabels its history (D-07-03).
        Map<UUID, String> stageNames = history.stages().stream()
                .collect(Collectors.toMap(Stage::id, Stage::name));
        return new PromotionHistoryResponse(
                history.stages().stream()
                        .map(s -> new StageResponse(s.id(), s.name(), s.position(), s.production(), s.retired()))
                        .toList(),
                history.currentStage().map(Stage::id).orElse(null),
                history.nextStage().map(Stage::id).orElse(null),
                history.entries().stream()
                        .sorted(PromotionHistory.LEDGER_ORDER.reversed())
                        .map(e -> toEntry(history, stageNames, actors, e))
                        .toList());
    }

    private static PromotionEntryResponse toEntry(PromotionHistory history, Map<UUID, String> stageNames,
                                                  ResolvedActors actors, Promotion e) {
        return new PromotionEntryResponse(e.id(), e.stageId(), stageNames.get(e.stageId()), e.outcome(),
                e.deployedAt(), e.recordedAt(), actors.display(e.recordedBy()), e.note(), e.supersedesId(),
                history.supersededBy(e.id()).orElse(null), e.correctionReason(), history.isEffective(e));
    }
}
