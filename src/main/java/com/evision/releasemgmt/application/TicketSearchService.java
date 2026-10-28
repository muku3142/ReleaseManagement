package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketField;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.jira.TicketSearchCriteria;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejectedException;
import com.evision.releasemgmt.domain.portfolio.PortfolioEditRejection;
import com.evision.releasemgmt.domain.portfolio.ProductNotFoundException;
import com.evision.releasemgmt.ports.JiraTicketPort;
import com.evision.releasemgmt.ports.ProductRepository;
import com.evision.releasemgmt.ports.ReleaseRepository;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
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
    private final ProductRepository productRepository;

    public TicketSearchService(JiraAccessContextService accessContextService,
                               FieldMetadataService fieldMetadataService,
                               JiraTicketPort ticketPort,
                               ReleaseRepository releaseRepository,
                               ProductRepository productRepository) {
        this.accessContextService = accessContextService;
        this.fieldMetadataService = fieldMetadataService;
        this.ticketPort = ticketPort;
        this.releaseRepository = releaseRepository;
        this.productRepository = productRepository;
    }

    public List<TicketDetail> search(TicketSearchCriteria criteria) {
        return search(criteria, null, false);
    }

    // Keys are read from the product mapping, never the request, and are not validated against
    // Jira here: the UI already warns about unknown keys, and an extra call per search buys nothing.
    public List<TicketDetail> searchProduct(UUID productId, Set<StatusCategory> statusCategories,
                                            UUID releaseScope, boolean hideAlreadyInRelease) {
        productRepository.findById(productId).orElseThrow(ProductNotFoundException::new);
        List<String> keys = productRepository.jiraProjectKeys().getOrDefault(productId, List.of());
        if (keys.isEmpty()) {
            throw new PortfolioEditRejectedException(PortfolioEditRejection.PRODUCT_NO_JIRA_PROJECT);
        }
        return search(TicketSearchCriteria.byProjectKeys(keys, statusCategories), releaseScope, hideAlreadyInRelease);
    }

    // The key is validated by the caller against ^[A-Z][A-Z0-9]*-[0-9]+$, so it cannot carry JQL.
    public Optional<TicketDetail> findByKey(String key) {
        return search(TicketSearchCriteria.byJql("key = " + key)).stream().findFirst();
    }

    // D-05.2-02: the release scope is a parameter, never a field on the criteria record, so the
    // query builder is structurally incapable of putting our release membership into a Jira query -
    // Jira knows nothing about releases and no clause could express it.
    public List<TicketDetail> search(TicketSearchCriteria criteria, UUID releaseScope, boolean hideAlreadyInRelease) {
        JiraAccessContext access = accessContextService.currentContext();
        Map<TicketField, String> fieldIds = new HashMap<>(fieldMetadataService.resolve(FIELDS));
        fieldMetadataService.storyPointsFieldId().ifPresent(id -> fieldIds.put(TicketField.STORY_POINTS, id));
        List<TicketDetail> found = ticketPort.searchAll(access, criteria.toJql(), fieldIds);
        if (releaseScope == null && !hideAlreadyInRelease) {
            return found;
        }
        Set<String> hidden = new HashSet<>();
        if (releaseScope != null) {
            hidden.addAll(releaseRepository.findTicketKeys(releaseScope));
        }
        if (hideAlreadyInRelease) {
            hidden.addAll(releaseRepository.ticketKeysInAnyRelease(found.stream().map(TicketDetail::key).toList()));
        }
        return found.stream().filter(ticket -> !hidden.contains(ticket.key())).toList();
    }
}
