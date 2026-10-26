package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraField;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.TicketField;
import com.evision.releasemgmt.ports.JiraFieldMetadataPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class FieldMetadataService {

    private static final Logger log = LoggerFactory.getLogger(FieldMetadataService.class);

    public static final List<String> BRIEFING_FIELD_NAMES = List.of("Story Points", "Flagged", "Sprint", "Acceptance Criteria");

    private final JiraAccessContextService accessContextService;
    private final JiraFieldMetadataPort fieldMetadataPort;

    private volatile Map<String, List<JiraField>> fieldsByLowerCasedName;

    private final String storyPointsFieldIdOverride;

    public FieldMetadataService(JiraAccessContextService accessContextService, JiraFieldMetadataPort fieldMetadataPort,
                                @Value("${app.jira.story-points.field-id}") String storyPointsFieldIdOverride) {
        this.accessContextService = accessContextService;
        this.fieldMetadataPort = fieldMetadataPort;
        this.storyPointsFieldIdOverride = storyPointsFieldIdOverride.trim();
    }

    public Optional<String> storyPointsFieldId() {
        if (!storyPointsFieldIdOverride.isEmpty()) {
            return Optional.of(storyPointsFieldIdOverride);
        }
        // No cache refresh on a miss: a site without the field would otherwise re-fetch metadata on every search.
        List<JiraField> candidates = currentCache()
                .get(TicketField.STORY_POINTS.displayName().toLowerCase(Locale.ROOT).trim());
        String id = candidates == null ? null : disambiguate(TicketField.STORY_POINTS.displayName(), candidates);
        if (id == null) {
            log.warn("Story points field not resolved (candidates: {}); set RM_JIRA_STORY_POINTS_FIELD_ID to pin one",
                    candidates == null ? List.of() : candidates.stream().map(JiraField::id).toList());
        }
        return Optional.ofNullable(id);
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
            String id = disambiguate(field.displayName(), candidates);
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
    private String disambiguate(String displayName, List<JiraField> candidates) {
        List<JiraField> systemFields = candidates.stream().filter(candidate -> !candidate.custom()).toList();
        if (systemFields.size() == 1) {
            if (candidates.size() > 1) {
                log.debug("Jira field name '{}' is shadowed by {} custom field(s); using system field '{}'",
                        displayName, candidates.size() - 1, systemFields.get(0).id());
            }
            return systemFields.get(0).id();
        }
        if (candidates.size() == 1) {
            return candidates.get(0).id();
        }
        return null;
    }

    /** Name -> id for the names this site has (system field wins, ambiguous customs omitted); one /field call, no shared cache. */
    public Map<String, String> resolveOptional(JiraAccessContext access, Collection<String> names) {
        Map<String, List<JiraField>> byName = new LinkedHashMap<>();
        for (JiraField field : fieldMetadataPort.fetchAllFields(access)) {
            byName.computeIfAbsent(field.name().toLowerCase(Locale.ROOT).trim(), key -> new ArrayList<>()).add(field);
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String name : names) {
            String lookupKey = name.toLowerCase(Locale.ROOT).trim();
            if (lookupKey.equals(TicketField.STORY_POINTS.displayName().toLowerCase(Locale.ROOT))
                    && !storyPointsFieldIdOverride.isEmpty()) {
                resolved.put(lookupKey, storyPointsFieldIdOverride);
                continue;
            }
            List<JiraField> candidates = byName.get(lookupKey);
            String id = candidates == null ? null : disambiguate(name, candidates);
            if (id == null) {
                log.debug("Optional Jira field '{}' not resolved on this site", name);
                continue;
            }
            resolved.put(lookupKey, id);
        }
        return resolved;
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
