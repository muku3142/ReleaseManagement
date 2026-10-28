package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.briefing.BriefingQueries;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.domain.jira.TicketDetail;
import com.evision.releasemgmt.domain.jira.TicketField;
import com.evision.releasemgmt.ports.JiraTicketPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class JiraRestTicketAdapter implements JiraTicketPort {

    // A system field id is fixed across tenants, so it needs no FieldMetadataService resolution.
    private static final String UPDATED_FIELD = "updated";

    private final JiraSearchClient searchClient;
    private final AdfFlattener adfFlattener;

    public JiraRestTicketAdapter(RestClient jiraRestClient,
                                  @Value("${app.jira.search.page-size}") int pageSize,
                                  @Value("${app.jira.search.max-pages}") int maxPages) {
        this.searchClient = new JiraSearchClient(jiraRestClient, pageSize, maxPages);
        this.adfFlattener = new AdfFlattener();
    }

    @Override
    public List<TicketDetail> searchAll(JiraAccessContext access, String jql, Map<TicketField, String> fieldIds) {
        List<String> requestedIds = new ArrayList<>(fieldIds.values());
        if (!requestedIds.contains(UPDATED_FIELD)) {
            requestedIds.add(UPDATED_FIELD);
        }
        List<TicketDetail> results = new ArrayList<>();
        for (JsonNode issue : searchClient.search(access, jql, requestedIds, BriefingQueries.UNCAPPED)) {
            results.add(toTicketDetail(issue, fieldIds));
        }
        return results;
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

        String storyPointsId = fieldIds.get(TicketField.STORY_POINTS);
        boolean storyPointsRead = storyPointsId != null;
        JsonNode points = storyPointsRead ? fields.path(storyPointsId) : MissingNode.getInstance();
        BigDecimal storyPoints = points.isNumber() ? points.decimalValue() : null;

        return new TicketDetail(key, summary, issueType, status, resolution, assignee, reporter,
                fixVersions, component, description, comments, commentTotal,
                jiraIssueId, updatedAt(fields), statusCategory, storyPoints, storyPointsRead);
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
