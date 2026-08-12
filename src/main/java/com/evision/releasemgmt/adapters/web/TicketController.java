package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.TicketResponse;
import com.evision.releasemgmt.adapters.web.dto.TicketSearchRequest;
import com.evision.releasemgmt.adapters.web.dto.TicketSearchResponse;
import com.evision.releasemgmt.application.TicketSearchService;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketSearchService service;

    public TicketController(TicketSearchService service) {
        this.service = service;
    }

    @PostMapping("/search")
    public TicketSearchResponse search(@Valid @RequestBody TicketSearchRequest request) {
        TicketSearchCriteria criteria = request.jql() != null && !request.jql().isBlank()
                ? TicketSearchCriteria.byJql(request.jql())
                : TicketSearchCriteria.byProject(request.projectId(), request.sprintId(),
                        request.statusCategories());
        return new TicketSearchResponse(
                service.search(criteria, request.releaseId()).stream().map(TicketController::toResponse).toList());
    }

    private static TicketResponse toResponse(TicketDetail t) {
        return new TicketResponse(t.key(), t.summary(), t.issueType(), t.status(), t.resolution(),
                t.assignee(), t.reporter(), t.fixVersions(), t.component(), t.description(), t.comments(),
                t.commentTotal());
    }
}
