package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraField;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.TicketField;
import com.evision.releasemgmt.ports.JiraFieldMetadataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class FieldMetadataService {

    private static final Logger log = LoggerFactory.getLogger(FieldMetadataService.class);

    private final JiraAccessContextService accessContextService;
    private final JiraFieldMetadataPort fieldMetadataPort;

    private volatile Map<String, List<String>> fieldIdsByLowerCasedName;

    public FieldMetadataService(JiraAccessContextService accessContextService, JiraFieldMetadataPort fieldMetadataPort) {
        this.accessContextService = accessContextService;
        this.fieldMetadataPort = fieldMetadataPort;
    }

    public Map<TicketField, String> resolve(Set<TicketField> fields) {
        Map<String, List<String>> cache = currentCache();
        Map<TicketField, String> resolved = new LinkedHashMap<>();
        for (TicketField field : fields) {
            String lookupKey = field.displayName().toLowerCase(Locale.ROOT).trim();
            List<String> ids = cache.get(lookupKey);
            if (ids == null) {
                cache = refreshCache();
                ids = cache.get(lookupKey);
            }
            if (ids == null) {
                log.warn("Jira field metadata has no field named '{}'", field.displayName());
                throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
            }
            // Jira does not enforce unique display names; guessing between colliding ids
            // would ship a silent wrong-field bug into Phase 5's custom-field usage.
            if (ids.size() > 1) {
                log.warn("Jira field name '{}' resolves to {} distinct field ids {} - refusing to guess",
                        field.displayName(), ids.size(), ids);
                throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
            }
            resolved.put(field, ids.get(0));
        }
        return resolved;
    }

    public void invalidate() {
        fieldIdsByLowerCasedName = null;
    }

    private Map<String, List<String>> currentCache() {
        Map<String, List<String>> cache = fieldIdsByLowerCasedName;
        if (cache == null) {
            cache = refreshCache();
        }
        return cache;
    }

    private Map<String, List<String>> refreshCache() {
        Map<String, List<String>> built = buildCache();
        fieldIdsByLowerCasedName = built;
        return built;
    }

    private Map<String, List<String>> buildCache() {
        Map<String, List<String>> byName = new LinkedHashMap<>();
        for (JiraField field : fieldMetadataPort.fetchAllFields(accessContextService.currentContext())) {
            String lookupKey = field.name().toLowerCase(Locale.ROOT).trim();
            byName.computeIfAbsent(lookupKey, key -> new ArrayList<>()).add(field.id());
        }
        return byName;
    }
}
