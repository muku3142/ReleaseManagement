package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.jira.JiraProjectSummary;
import com.evision.releasemgmt.domain.jira.JiraSprint;

import java.util.List;

public interface JiraProjectPort {

    List<JiraProjectSummary> listProjects(JiraAccessContext access);

    List<JiraSprint> listSprints(JiraAccessContext access, String projectId);
}
