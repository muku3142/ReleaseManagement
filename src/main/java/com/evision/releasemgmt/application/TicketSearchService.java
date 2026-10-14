package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketField;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import com.evision.releasemgmt.ports.JiraTicketPort;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class TicketSearchService {

    // TicketField is already exactly the selected field set, so enumerating them again here
    // would be a second place to keep in sync when Phase 5 adds component/epic/label.
    // Story points are optional and resolved separately, so a site without the field can still search.
    private static final Set<TicketField> FIELDS = EnumSet.complementOf(EnumSet.of(TicketField.STORY_POINTS));

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

    // The key is validated by the caller against ^[A-Z][A-Z0-9]*-[0-9]+$, so it cannot carry JQL.
    public Optional<TicketDetail> findByKey(String key) {
        return search(TicketSearchCriteria.byJql("key = " + key)).stream().findFirst();
    }

    // D-05.2-02: the release scope is a parameter, never a field on the criteria record, so the
    // query builder is structurally incapable of putting our release membership into a Jira query -
    // Jira knows nothing about releases and no clause could express it.
    public List<TicketDetail> search(TicketSearchCriteria criteria, UUID releaseScope) {
        JiraAccessContext access = accessContextService.currentContext();
        Map<TicketField, String> fieldIds = new HashMap<>(fieldMetadataService.resolve(FIELDS));
        fieldMetadataService.storyPointsFieldId().ifPresent(id -> fieldIds.put(TicketField.STORY_POINTS, id));
        List<TicketDetail> found = ticketPort.searchAll(access, criteria.toJql(), fieldIds);
        if (releaseScope == null) {
            return found;
        }
        Set<String> present = Set.copyOf(releaseRepository.findTicketKeys(releaseScope));
        return found.stream().filter(ticket -> !present.contains(ticket.key())).toList();
    }
}
