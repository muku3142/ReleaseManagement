package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketField;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import com.evision.releasemgmt.ports.JiraTicketPort;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class TicketSearchService {

    // TicketField is already exactly the selected field set, so enumerating them again here
    // would be a second place to keep in sync when Phase 5 adds component/epic/label.
    private static final Set<TicketField> FIELDS = EnumSet.allOf(TicketField.class);

    private final JiraAccessContextService accessContextService;
    private final FieldMetadataService fieldMetadataService;
    private final JiraTicketPort ticketPort;

    public TicketSearchService(JiraAccessContextService accessContextService,
                               FieldMetadataService fieldMetadataService,
                               JiraTicketPort ticketPort) {
        this.accessContextService = accessContextService;
        this.fieldMetadataService = fieldMetadataService;
        this.ticketPort = ticketPort;
    }

    public List<TicketDetail> search(TicketSearchCriteria criteria) {
        JiraAccessContext access = accessContextService.currentContext();
        Map<TicketField, String> fieldIds = fieldMetadataService.resolve(FIELDS);
        return ticketPort.searchAll(access, criteria.toJql(), fieldIds);
    }
}
