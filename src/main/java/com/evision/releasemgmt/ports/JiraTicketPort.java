package com.evision.releasemgmt.ports;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketField;

import java.util.List;
import java.util.Map;

public interface JiraTicketPort {

    List<TicketDetail> searchAll(JiraAccessContext access, String jql, Map<TicketField, String> fieldIds);
}
