package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.CodeLinksResponse;
import com.evision.releasemgmt.adapters.web.dto.TicketResponse;
import com.evision.releasemgmt.adapters.web.dto.TicketSearchRequest;
import com.evision.releasemgmt.adapters.web.dto.TicketSearchResponse;
import com.evision.releasemgmt.application.EnrichmentService;
import com.evision.releasemgmt.application.TicketSearchService;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketNotFoundException;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketSearchService service;
    private final EnrichmentService enrichmentService;

    public TicketController(TicketSearchService service, EnrichmentService enrichmentService) {
        this.service = service;
        this.enrichmentService = enrichmentService;
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

    // Live, read-only lookup for the ticket browser; stores nothing (D-06-01 governs release reads, not this).
    @GetMapping("/{key}/code-links")
    public CodeLinksResponse codeLinks(@PathVariable @Pattern(regexp = "^[A-Z][A-Z0-9]*-[0-9]+$") String key) {
        TicketDetail ticket = service.findByKey(key).orElseThrow(TicketNotFoundException::new);
        return CodeLinksResponse.from(enrichmentService.lookup(ticket));
    }

    private static TicketResponse toResponse(TicketDetail t) {
        return new TicketResponse(t.key(), t.summary(), t.issueType(), t.status(), t.resolution(),
                t.assignee(), t.reporter(), t.fixVersions(), t.component(), t.description(), t.comments(),
                t.commentTotal());
    }
}
