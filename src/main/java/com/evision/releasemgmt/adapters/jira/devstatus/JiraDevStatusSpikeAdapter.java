package com.evision.releasemgmt.adapters.jira.devstatus;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
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
import java.util.Base64;

// /rest/dev-status/* is undocumented and Atlassian states it may change without notice; see 02-DEVSTATUS-SPIKE.md.
@Component
public class JiraDevStatusSpikeAdapter {

    private final RestClient restClient;

    public JiraDevStatusSpikeAdapter(RestClient jiraRestClient) {
        this.restClient = jiraRestClient;
    }

    // dev-status rejects the issue key and needs the numeric internal id, unlike every other v3 endpoint in this project.
    public String resolveNumericIssueId(JiraAccessContext access, String issueKey) {
        JsonNode body = get(access, access.siteBaseUrl() + "/rest/api/3/issue/{key}?fields=id", issueKey);
        if (body == null || !body.hasNonNull("id")) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
        }
        return body.get("id").asString();
    }

    public JsonNode fetchDevStatusDetail(JiraAccessContext access, String numericIssueId, String applicationType, String dataType) {
        JsonNode body = get(access,
                access.siteBaseUrl() + "/rest/dev-status/1.0/issue/detail?issueId={id}&applicationType={app}&dataType={type}",
                numericIssueId, applicationType, dataType);
        if (body == null) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
        }
        return body;
    }

    private JsonNode get(JiraAccessContext access, String uriTemplate, Object... uriVariables) {
        String basic = Base64.getEncoder().encodeToString(
                (access.email() + ":" + access.apiToken()).getBytes(StandardCharsets.UTF_8));
        try {
            return restClient.get()
                    .uri(uriTemplate, uriVariables)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.INVALID_CREDENTIALS);
        } catch (HttpClientErrorException.Forbidden e) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_FORBIDDEN);
        } catch (HttpClientErrorException.TooManyRequests e) {
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
