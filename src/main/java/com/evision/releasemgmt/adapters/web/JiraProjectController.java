package com.evision.releasemgmt.adapters.web;

import com.evision.releasemgmt.adapters.web.dto.JiraProjectResponse;
import com.evision.releasemgmt.adapters.web.dto.JiraVersionResponse;
import com.evision.releasemgmt.application.JiraProjectService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/jira/projects")
public class JiraProjectController {

    private final JiraProjectService service;

    public JiraProjectController(JiraProjectService service) {
        this.service = service;
    }

    @GetMapping
    public List<JiraProjectResponse> list() {
        return service.listProjects().stream()
                .map(p -> new JiraProjectResponse(p.id(), p.key(), p.name()))
                .toList();
    }

    @GetMapping("/{projectId}/versions")
    public List<JiraVersionResponse> versions(@PathVariable String projectId) {
        return service.listVersions(projectId).stream()
                .map(v -> new JiraVersionResponse(v.id(), v.name()))
                .toList();
    }
}
