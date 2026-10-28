package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.briefing.BriefingFieldIds;
import com.evision.releasemgmt.domain.briefing.BriefingIssue;
import com.evision.releasemgmt.domain.briefing.BriefingText;
import com.evision.releasemgmt.domain.jira.StatusCategory;
import com.evision.releasemgmt.ports.JiraBriefingPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.MissingNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class JiraBriefingAdapter implements JiraBriefingPort {

    private static final List<String> SYSTEM_FIELDS = List.of("summary", "issuetype", "status", "priority", "assignee",
            "created", "updated", "resolutiondate", "duedate", "parent", "subtasks", "description");

    private static final int DESCRIPTION_MAX_CHARS = 4000;

    private static final Pattern ACCEPTANCE_IN_DESCRIPTION =
            Pattern.compile("(?is)acceptance criteria|\\bgiven\\b.*\\bwhen\\b.*\\bthen\\b");

    private final JiraSearchClient searchClient;
    private final AdfFlattener adfFlattener = new AdfFlattener();

    public JiraBriefingAdapter(RestClient jiraRestClient,
                               @Value("${app.jira.search.page-size}") int pageSize,
                               @Value("${app.jira.search.max-pages}") int maxPages) {
        this.searchClient = new JiraSearchClient(jiraRestClient, pageSize, maxPages);
    }

    @Override
    public List<BriefingIssue> search(JiraAccessContext access, String jql, BriefingFieldIds fieldIds, int maxIssues) {
        List<String> requested = new ArrayList<>(SYSTEM_FIELDS);
        for (String id : new String[] {fieldIds.storyPoints(), fieldIds.flagged(), fieldIds.sprint(), fieldIds.acceptanceCriteria()}) {
            if (id != null) {
                requested.add(id);
            }
        }
        List<BriefingIssue> issues = new ArrayList<>();
        for (JsonNode node : searchClient.search(access, jql, requested, maxIssues)) {
            issues.add(toIssue(node, fieldIds));
        }
        return issues;
    }

    private BriefingIssue toIssue(JsonNode issue, BriefingFieldIds ids) {
        JsonNode fields = issue.path("fields");
        JsonNode status = fields.path("status");
        StatusCategory category = switch (status.path("statusCategory").path("key").asText("")) {
            case "new" -> StatusCategory.TO_DO;
            case "indeterminate" -> StatusCategory.IN_PROGRESS;
            case "done" -> StatusCategory.DONE;
            default -> null;
        };
        String description = BriefingText.clip(text(fields.path("description")), DESCRIPTION_MAX_CHARS);
        return new BriefingIssue(
                issue.path("key").asText(null),
                fields.path("summary").asText(""),
                fields.path("issuetype").path("name").asText("Unknown"),
                status.path("name").asText("Unknown"),
                category,
                fields.path("priority").path("name").asText(null),
                ids.flagged() != null && fields.path(ids.flagged()).isArray() && !fields.path(ids.flagged()).isEmpty(),
                fields.path("assignee").path("displayName").asText(null),
                instant(fields.path("created")),
                instant(fields.path("updated")),
                instant(fields.path("resolutiondate")),
                date(fields.path("duedate")),
                storyPoints(fields, ids.storyPoints()),
                fields.path("parent").path("key").asText(null),
                fields.path("subtasks").isArray() ? fields.path("subtasks").size() : 0,
                acceptanceCriteriaPresent(fields, ids.acceptanceCriteria(), description),
                description,
                sprints(fields, ids.sprint()));
    }

    private String text(JsonNode node) {
        if (node.isString()) {
            return node.asText();
        }
        return adfFlattener.flatten(node);
    }

    private boolean acceptanceCriteriaPresent(JsonNode fields, String fieldId, String description) {
        if (fieldId == null) {
            return ACCEPTANCE_IN_DESCRIPTION.matcher(description).find();
        }
        return !text(fields.path(fieldId)).isBlank();
    }

    private static BigDecimal storyPoints(JsonNode fields, String fieldId) {
        JsonNode points = fieldId == null ? MissingNode.getInstance() : fields.path(fieldId);
        return points.isNumber() ? points.decimalValue() : null;
    }

    private static List<BriefingIssue.Sprint> sprints(JsonNode fields, String fieldId) {
        List<BriefingIssue.Sprint> sprints = new ArrayList<>();
        JsonNode node = fieldId == null ? MissingNode.getInstance() : fields.path(fieldId);
        for (JsonNode sprint : node) {
            sprints.add(new BriefingIssue.Sprint(sprint.path("id").asLong(), sprint.path("name").asText(""),
                    sprint.path("state").asText(""), sprintInstant(sprint.path("startDate")), sprintInstant(sprint.path("endDate"))));
        }
        return sprints;
    }

    // A malformed value must not fail the whole briefing; the field degrades to null.
    private static Instant instant(JsonNode node) {
        try {
            return JiraTimestamps.parse(node.asText(null));
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // Sprint dates come from Jira Software in UTC ISO form ("...Z"), unlike the issue fields' offset form.
    private static Instant sprintInstant(JsonNode node) {
        String value = node.asText(null);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException e) {
            return instant(node);
        }
    }

    private static LocalDate date(JsonNode node) {
        try {
            String value = node.asText(null);
            return value == null || value.isBlank() ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
