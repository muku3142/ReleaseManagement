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

    private volatile Map<String, List<JiraField>> fieldsByLowerCasedName;

    public FieldMetadataService(JiraAccessContextService accessContextService, JiraFieldMetadataPort fieldMetadataPort) {
        this.accessContextService = accessContextService;
        this.fieldMetadataPort = fieldMetadataPort;
    }

    public Map<TicketField, String> resolve(Set<TicketField> fields) {
        Map<String, List<JiraField>> cache = currentCache();
        Map<TicketField, String> resolved = new LinkedHashMap<>();
        // Every unresolvable field is collected before throwing. Failing on the first one turns
        // diagnosing an unfamiliar Jira configuration into a fix-one-retry loop, where each
        // round trip reveals only the next problem.
        List<String> failures = new ArrayList<>();
        for (TicketField field : fields) {
            String lookupKey = field.displayName().toLowerCase(Locale.ROOT).trim();
            List<JiraField> candidates = cache.get(lookupKey);
            if (candidates == null) {
                cache = refreshCache();
                candidates = cache.get(lookupKey);
            }
            if (candidates == null) {
                failures.add("'" + field.displayName() + "': no field with that name");
                continue;
            }
            String id = disambiguate(field, candidates);
            if (id == null) {
                failures.add("'" + field.displayName() + "': ambiguous between "
                        + candidates.stream().map(JiraField::id).toList() + " with no system field");
                continue;
            }
            resolved.put(field, id);
        }
        if (!failures.isEmpty()) {
            log.warn("Could not resolve {} of {} Jira field name(s): {}",
                    failures.size(), fields.size(), String.join("; ", failures));
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
        }
        return resolved;
    }

    // Jira does not enforce unique display names, and real instances routinely carry custom
    // fields shadowing a system field's name (the eVision tenant has three "Resolution" fields).
    // A system field is never ambiguous with a custom one — its id is the canonical meaning of
    // the name — so it wins. Guessing between multiple *custom* fields is still refused: that is
    // where the silent-wrong-field risk actually lives, and what Phase 5 depends on.
    // Returns null when the choice is genuinely undecidable.
    private String disambiguate(TicketField field, List<JiraField> candidates) {
        List<JiraField> systemFields = candidates.stream().filter(candidate -> !candidate.custom()).toList();
        if (systemFields.size() == 1) {
            if (candidates.size() > 1) {
                log.debug("Jira field name '{}' is shadowed by {} custom field(s); using system field '{}'",
                        field.displayName(), candidates.size() - 1, systemFields.get(0).id());
            }
            return systemFields.get(0).id();
        }
        if (candidates.size() == 1) {
            return candidates.get(0).id();
        }
        return null;
    }

    public void invalidate() {
        fieldsByLowerCasedName = null;
    }

    private Map<String, List<JiraField>> currentCache() {
        Map<String, List<JiraField>> cache = fieldsByLowerCasedName;
        if (cache == null) {
            cache = refreshCache();
        }
        return cache;
    }

    private Map<String, List<JiraField>> refreshCache() {
        Map<String, List<JiraField>> built = buildCache();
        fieldsByLowerCasedName = built;
        return built;
    }

    private Map<String, List<JiraField>> buildCache() {
        Map<String, List<JiraField>> byName = new LinkedHashMap<>();
        for (JiraField field : fieldMetadataPort.fetchAllFields(accessContextService.currentContext())) {
            String lookupKey = field.name().toLowerCase(Locale.ROOT).trim();
            byName.computeIfAbsent(lookupKey, key -> new ArrayList<>()).add(field);
        }
        return byName;
    }
}
