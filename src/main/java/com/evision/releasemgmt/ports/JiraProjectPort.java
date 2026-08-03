package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.jira.JiraProjectSummary;
import com.evision.releasemgmt.domain.jira.JiraVersion;

import java.util.List;

public interface JiraProjectPort {

    List<JiraProjectSummary> listProjects(JiraAccessContext access);

    List<JiraVersion> listVersions(JiraAccessContext access, String projectId);
}
