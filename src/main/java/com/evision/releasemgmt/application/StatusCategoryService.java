package com.evision.releasemgmt.application;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.ports.JiraIssueHistoryPort;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Service
public class StatusCategoryService {

    private final JiraIssueHistoryPort historyPort;

    private volatile Map<String, StatusCategory> categoriesById;

    public StatusCategoryService(JiraIssueHistoryPort historyPort) {
        this.historyPort = historyPort;
    }

    // Takes the already-resolved access context because it is called from enrichment worker threads, which have no
    // SecurityContext. The visible status set can differ per user, which the refresh-on-miss absorbs.
    public Map<String, StatusCategory> categoriesFor(JiraAccessContext access, Set<String> statusIds) {
        Map<String, StatusCategory> cache = categoriesById;
        if (cache == null || !cache.keySet().containsAll(statusIds)) {
            cache = refresh(access);
        }
        Map<String, StatusCategory> known = new HashMap<>();
        for (String id : statusIds) {
            StatusCategory category = cache.get(id);
            if (category != null) {
                known.put(id, category);
            }
        }
        return known;
    }

    private Map<String, StatusCategory> refresh(JiraAccessContext access) {
        Map<String, StatusCategory> built = Map.copyOf(historyPort.statusCategories(access));
        categoriesById = built;
        return built;
    }
}
