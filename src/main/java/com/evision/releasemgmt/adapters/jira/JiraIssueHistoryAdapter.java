package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.enrichment.StatusTransition;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.ports.JiraIssueHistoryPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Component
public class JiraIssueHistoryAdapter implements JiraIssueHistoryPort {

    private static final String STATUS_FIELD_ID = "status";
    private static final int PAGE_SIZE = 100;

    private final RestClient restClient;
    private final int maxPages;

    public JiraIssueHistoryAdapter(RestClient jiraRestClient, @Value("${app.jira.changelog.max-pages}") int maxPages) {
        this.restClient = jiraRestClient;
        this.maxPages = maxPages;
    }

    @Override
    public List<StatusTransition> statusTransitions(JiraAccessContext access, String issueKey) {
        return mapFailures(() -> {
            List<StatusTransition> transitions = new ArrayList<>();
            int startAt = 0;
            int pagesFetched = 0;
            boolean more;
            do {
                JsonNode page = get(access, access.siteBaseUrl() + "/rest/api/3/issue/" + issueKey
                        + "/changelog?startAt=" + startAt + "&maxResults=" + PAGE_SIZE);
                JsonNode values = page.path("values");
                for (JsonNode entry : values) {
                    collectStatusTransitions(entry, transitions);
                }
                startAt += values.size();
                more = !page.path("isLast").asBoolean(false) && !values.isEmpty()
                        && startAt < page.path("total").asInt(0);
                if (++pagesFetched >= maxPages && more) {
                    throw new JiraTicketReadException(JiraAuthFailureReason.RESULT_SET_TOO_LARGE);
                }
            } while (more);
            return transitions;
        });
    }

    @Override
    public Map<String, StatusCategory> statusCategories(JiraAccessContext access) {
        return mapFailures(() -> {
            Map<String, StatusCategory> categories = new LinkedHashMap<>();
            for (JsonNode status : get(access, access.siteBaseUrl() + "/rest/api/3/status")) {
                StatusCategory category = switch (status.path("statusCategory").path("key").asText("")) {
                    case "new" -> StatusCategory.TO_DO;
                    case "indeterminate" -> StatusCategory.IN_PROGRESS;
                    case "done" -> StatusCategory.DONE;
                    default -> null;
                };
                String id = status.path("id").asText(null);
                if (id != null && category != null) {
                    categories.put(id, category);
                }
            }
            return categories;
        });
    }

    private static void collectStatusTransitions(JsonNode entry, List<StatusTransition> out) {
        Instant at = createdAt(entry);
        for (JsonNode item : entry.path("items")) {
            if (!STATUS_FIELD_ID.equals(item.path("fieldId").asText(null))) {
                continue;
            }
            String to = item.path("to").asText(null);
            if (at == null || to == null || to.isBlank()) {
                continue;
            }
            String from = item.path("from").asText(null);
            out.add(new StatusTransition(at, from == null || from.isBlank() ? null : from, to));
        }
    }

    private static Instant createdAt(JsonNode entry) {
        try {
            return JiraTimestamps.parse(entry.path("created").asText(null));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private JsonNode get(JiraAccessContext access, String url) {
        String basic = Base64.getEncoder()
                .encodeToString((access.email() + ":" + access.apiToken()).getBytes(StandardCharsets.UTF_8));
        JsonNode body = restClient.get()
                .uri(url)
                .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .retrieve()
                .body(JsonNode.class);
        if (body == null) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
        }
        return body;
    }

    private static <T> T mapFailures(Supplier<T> call) {
        try {
            return call.get();
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.INVALID_CREDENTIALS);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_FORBIDDEN);
        } catch (HttpClientErrorException.TooManyRequests e) {
            // No retry: attempting again is what would worsen throttling.
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_RATE_LIMITED);
        } catch (HttpServerErrorException e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
        } catch (ResourceAccessException e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_UNREACHABLE);
        } catch (RestClientException e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
        }
    }
}
