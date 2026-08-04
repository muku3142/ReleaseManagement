package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.jira.JiraProjectSummary;
import com.evision.releasemgmt.domain.jira.JiraVersion;
import com.evision.releasemgmt.ports.JiraProjectPort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class JiraProjectService {

    private final JiraAccessContextService accessContextService;
    private final JiraProjectPort projectPort;

    public JiraProjectService(JiraAccessContextService accessContextService, JiraProjectPort projectPort) {
        this.accessContextService = accessContextService;
        this.projectPort = projectPort;
    }

    public List<JiraProjectSummary> listProjects() {
        return projectPort.listProjects(accessContextService.currentContext());
    }

    public List<JiraVersion> listVersions(String projectId) {
        return projectPort.listVersions(accessContextService.currentContext(), projectId);
    }
}
