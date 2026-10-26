package com.evision.releasemgmt.archfixture;

import com.evision.releasemgmt.application.assistant.tools.AssistantTool;
import com.evision.releasemgmt.application.assistant.tools.ToolOutput;
import com.evision.releasemgmt.ports.JiraTicketPort;
import java.util.Map;

/** ArchUnit non-vacuity fixture: depends on a ticket-source port. Never wired into the application. */
public class JiraReachingTool implements AssistantTool {

    private JiraTicketPort jira;

    @Override
    public String name() {
        return "jira_reaching";
    }

    @Override
    public String description() {
        return "fixture";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of();
    }

    @Override
    public ToolOutput call(Map<String, Object> arguments) {
        return jira == null ? null : null;
    }
}
