package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketField;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import com.evision.releasemgmt.ports.JiraTicketPort;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketSearchService {

    // TicketField is already exactly the selected field set, so enumerating them again here
    // would be a second place to keep in sync when Phase 5 adds component/epic/label.
    private static final Set<TicketField> FIELDS = EnumSet.allOf(TicketField.class);

    private final JiraAccessContextService accessContextService;
    private final FieldMetadataService fieldMetadataService;
    private final JiraTicketPort ticketPort;
    private final ReleaseRepository releaseRepository;

    public TicketSearchService(JiraAccessContextService accessContextService,
                               FieldMetadataService fieldMetadataService,
                               JiraTicketPort ticketPort,
                               ReleaseRepository releaseRepository) {
        this.accessContextService = accessContextService;
        this.fieldMetadataService = fieldMetadataService;
        this.ticketPort = ticketPort;
        this.releaseRepository = releaseRepository;
    }

    public List<TicketDetail> search(TicketSearchCriteria criteria) {
        return search(criteria, null);
    }

    // D-05.2-02: the release scope is a parameter, never a field on the criteria record, so the
    // query builder is structurally incapable of putting our release membership into a Jira query -
    // Jira knows nothing about releases and no clause could express it.
    public List<TicketDetail> search(TicketSearchCriteria criteria, UUID releaseScope) {
        JiraAccessContext access = accessContextService.currentContext();
        Map<TicketField, String> fieldIds = fieldMetadataService.resolve(FIELDS);
        List<TicketDetail> found = ticketPort.searchAll(access, criteria.toJql(), fieldIds);
        if (releaseScope == null) {
            return found;
        }
        Set<String> present = Set.copyOf(releaseRepository.findTicketKeys(releaseScope));
        return found.stream().filter(ticket -> !present.contains(ticket.key())).toList();
    }
}
