package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.EnvironmentLadderResponse;
import com.evision.releasemgmt.adapters.web.dto.LadderStageResponse;
import com.evision.releasemgmt.adapters.web.dto.ProductionStageRequest;
import com.evision.releasemgmt.adapters.web.dto.StageNameRequest;
import com.evision.releasemgmt.adapters.web.dto.StageOrderRequest;
import com.evision.releasemgmt.application.EnvironmentLadderService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

// D-07-12: any signed-in user may edit the ladder, and no actor is stored for ladder edits.
@RestController
@RequestMapping("/api/environment-ladder")
public class EnvironmentLadderController {

    private final EnvironmentLadderService service;

    public EnvironmentLadderController(EnvironmentLadderService service) {
        this.service = service;
    }

    @GetMapping
    public EnvironmentLadderResponse ladder() {
        return respond();
    }

    @PostMapping("/stages")
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentLadderResponse add(@Valid @RequestBody StageNameRequest request) {
        service.add(request.name());
        return respond();
    }

    @PutMapping("/stages/{stageId}")
    public EnvironmentLadderResponse rename(@PathVariable UUID stageId, @Valid @RequestBody StageNameRequest request) {
        service.rename(stageId, request.name());
        return respond();
    }

    @PutMapping("/order")
    public EnvironmentLadderResponse reorder(@Valid @RequestBody StageOrderRequest request) {
        service.reorder(request.stageIds());
        return respond();
    }

    @PostMapping("/stages/{stageId}/retire")
    public EnvironmentLadderResponse retire(@PathVariable UUID stageId) {
        service.retire(stageId);
        return respond();
    }

    @PostMapping("/stages/{stageId}/restore")
    public EnvironmentLadderResponse restore(@PathVariable UUID stageId) {
        service.restore(stageId);
        return respond();
    }

    @PutMapping("/production-stage")
    public EnvironmentLadderResponse moveProduction(@Valid @RequestBody ProductionStageRequest request) {
        service.moveProduction(request.stageId());
        return respond();
    }

    @DeleteMapping("/stages/{stageId}")
    public EnvironmentLadderResponse delete(@PathVariable UUID stageId) {
        service.delete(stageId);
        return respond();
    }

    private EnvironmentLadderResponse respond() {
        Set<UUID> inUse = service.referencedStageIds();
        return new EnvironmentLadderResponse(service.ladder().stream()
                .map(s -> new LadderStageResponse(s.id(), s.name(), s.position(), s.production(), s.retired(),
                        inUse.contains(s.id())))
                .toList());
    }
}
