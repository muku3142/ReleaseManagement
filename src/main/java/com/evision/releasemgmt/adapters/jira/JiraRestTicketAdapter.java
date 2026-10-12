package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketField;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.ports.JiraTicketPort;
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
import java.util.List;
import java.util.Map;

@Component
public class JiraRestTicketAdapter implements JiraTicketPort {

    // A system field id is fixed across tenants, so it needs no FieldMetadataService resolution.
    private static final String UPDATED_FIELD = "updated";

    private final RestClient restClient;
    private final int pageSize;
    private final int maxPages;
    private final AdfFlattener adfFlattener;

    public JiraRestTicketAdapter(RestClient jiraRestClient,
                                  @Value("${app.jira.search.page-size}") int pageSize,
                                  @Value("${app.jira.search.max-pages}") int maxPages) {
        this.restClient = jiraRestClient;
        this.pageSize = pageSize;
        this.maxPages = maxPages;
        this.adfFlattener = new AdfFlattener();
    }

    @Override
    public List<TicketDetail> searchAll(JiraAccessContext access, String jql, Map<TicketField, String> fieldIds) {
        String basic = Base64.getEncoder()
                .encodeToString((access.email() + ":" + access.apiToken()).getBytes(StandardCharsets.UTF_8));
        List<String> requestedIds = new ArrayList<>(fieldIds.values());
        if (!requestedIds.contains(UPDATED_FIELD)) {
            requestedIds.add(UPDATED_FIELD);
        }
        try {
            List<TicketDetail> results = new ArrayList<>();
            String pageToken = null;
            int pagesFetched = 0;
            do {
                JqlSearchPage page = restClient.post()
                        .uri(access.siteBaseUrl() + "/rest/api/3/search/jql")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new JqlSearchRequest(jql, requestedIds, pageSize, pageToken))
                        .retrieve()
                        .body(JqlSearchPage.class);
                if (page == null) {
                    throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
                }
                if (page.issues() != null) {
                    for (JsonNode issue : page.issues()) {
                        results.add(toTicketDetail(issue, fieldIds));
                    }
                }
                // Terminate on the ABSENCE of nextPageToken, never on isLast: community reports
                // across 2026 describe isLast never flipping true on this endpoint - it is
                // informational only.
                pageToken = page.nextPageToken();
                if (++pagesFetched >= maxPages && pageToken != null && !pageToken.isBlank()) {
                    throw new JiraTicketReadException(JiraAuthFailureReason.RESULT_SET_TOO_LARGE);
                }
            } while (pageToken != null && !pageToken.isBlank());
            return results;
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.INVALID_CREDENTIALS);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_FORBIDDEN);
        } catch (HttpClientErrorException.TooManyRequests e) {
            // Surfaced immediately, deliberately with no backoff loop of any kind: attempting
            // the call again is exactly what would worsen throttling against an already
            // rate-limited upstream.
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_RATE_LIMITED);
        } catch (HttpServerErrorException e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
        } catch (ResourceAccessException e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_UNREACHABLE);
        } catch (RestClientException e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
        }
    }

    private TicketDetail toTicketDetail(JsonNode issue, Map<TicketField, String> fieldIds) {
        JsonNode fields = issue.path("fields");
        String key = issue.path("key").asText(null);
        // summary, issue_type and status are NOT NULL in release_ticket_item. A field hidden by a
        // permission scheme, or a resolved field id that no longer matches, is exactly the schema
        // drift the stack notes tell us to expect - it must degrade to placeholder text, not to a
        // constraint violation at flush and a 500.
        String summary = field(fields, fieldIds, TicketField.SUMMARY).asText("");
        String issueType = field(fields, fieldIds, TicketField.ISSUE_TYPE).path("name").asText("Unknown");
        String status = field(fields, fieldIds, TicketField.STATUS).path("name").asText("Unknown");
        String resolution = field(fields, fieldIds, TicketField.RESOLUTION).path("name").asText(null);
        String assignee = field(fields, fieldIds, TicketField.ASSIGNEE).path("displayName").asText(null);
        String reporter = field(fields, fieldIds, TicketField.REPORTER).path("displayName").asText(null);

        List<String> fixVersions = new ArrayList<>();
        for (JsonNode version : field(fields, fieldIds, TicketField.FIX_VERSIONS)) {
            String name = version.path("name").asText(null);
            if (name != null) {
                fixVersions.add(name);
            }
        }

        // D-05-02: Jira's fields.components is an array of {id, name} - the same shape as
        // fixVersions - but the view-model carries a single component, so the first entry wins.
        // No placeholder default is substituted here: that belongs at the view-model layer
        // (D-05-03), so the stored snapshot records what Jira actually said, including nothing.
        String component = null;
        JsonNode componentsNode = field(fields, fieldIds, TicketField.COMPONENT);
        if (componentsNode.isArray() && !componentsNode.isEmpty()) {
            component = componentsNode.get(0).path("name").asText(null);
        }

        String description = adfFlattener.flatten(field(fields, fieldIds, TicketField.DESCRIPTION));

        JsonNode commentField = field(fields, fieldIds, TicketField.COMMENT);
        List<String> comments = new ArrayList<>();
        for (JsonNode comment : commentField.path("comments")) {
            comments.add(adfFlattener.flatten(comment.path("body")));
        }
        int commentTotal = commentField.path("total").asInt(comments.size());

        String jiraIssueId = issue.path("id").asText(null);
        StatusCategory statusCategory = switch (field(fields, fieldIds, TicketField.STATUS)
                .path("statusCategory").path("key").asText("")) {
            case "new" -> StatusCategory.TO_DO;
            case "indeterminate" -> StatusCategory.IN_PROGRESS;
            case "done" -> StatusCategory.DONE;
            default -> null;
        };

        return new TicketDetail(key, summary, issueType, status, resolution, assignee, reporter,
                fixVersions, component, description, comments, commentTotal,
                jiraIssueId, updatedAt(fields), statusCategory);
    }

    // A malformed timestamp must not fail the whole search; null makes the history read retry later.
    private static Instant updatedAt(JsonNode fields) {
        try {
            return JiraTimestamps.parse(fields.path(UPDATED_FIELD).asText(null));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static JsonNode field(JsonNode fields, Map<TicketField, String> ids, TicketField f) {
        return fields.path(ids.get(f));
    }
}
