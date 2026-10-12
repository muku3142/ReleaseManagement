package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.enrichment.DevStatusLinks;

public interface JiraDevStatusPort {

    /**
     * Discovers a ticket's Azure Repos links from Jira dev-status using the caller's own Jira credential.
     *
     * @throws com.evision.releasemgmt.domain.enrichment.DevStatusUnrecognisedException summary and detail disagree
     * @throws com.evision.releasemgmt.domain.jira.JiraTicketReadException any HTTP failure, with its reason
     */
    DevStatusLinks fetchLinks(JiraAccessContext access, String issueId, String ticketKey);
}
