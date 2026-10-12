package com.evision.releasemgmt.adapters.jira.devstatus;

import com.evision.releasemgmt.adapters.jira.JiraTimestamps;
import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.enrichment.AdoRepoRef;
import com.evision.releasemgmt.domain.enrichment.DevStatusLinks;
import com.evision.releasemgmt.domain.enrichment.DevStatusUnrecognisedException;
import com.evision.releasemgmt.domain.enrichment.LinkedCommit;
import com.evision.releasemgmt.domain.enrichment.LinkedPullRequest;
import com.evision.releasemgmt.domain.enrichment.TicketBranchRule;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.ports.JiraDevStatusPort;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// /rest/dev-status/* is undocumented and Atlassian states it may change without notice; see 02-DEVSTATUS-SPIKE.md.
@Component
public class JiraDevStatusAdapter implements JiraDevStatusPort {

    private static final String APPLICATION_TYPE = "AzureRepos";
    private static final String DETAIL_URL =
            "/rest/dev-status/1.0/issue/detail?issueId={id}&applicationType={app}&dataType={type}";

    private final RestClient restClient;

    public JiraDevStatusAdapter(RestClient jiraRestClient) {
        this.restClient = jiraRestClient;
    }

    // dev-status rejects the issue key and needs the numeric internal id, unlike every other v3 endpoint in this project.
    @Override
    public DevStatusLinks fetchLinks(JiraAccessContext access, String issueId, String ticketKey) {
        JsonNode summary = get(access, access.siteBaseUrl() + "/rest/dev-status/1.0/issue/summary?issueId={id}", issueId)
                .path("summary");
        boolean hasPullRequests = advertises(summary, "pullrequest");
        boolean hasBranches = advertises(summary, "branch");
        boolean hasRepositories = advertises(summary, "repository");
        if (!hasPullRequests && !hasBranches && !hasRepositories) {
            return DevStatusLinks.none();
        }

        List<LinkedPullRequest> pullRequests = new ArrayList<>();
        List<String> branches = new ArrayList<>();
        if (hasPullRequests || hasBranches) {
            for (JsonNode element : detail(access, issueId, "pullrequest")) {
                collectPullRequests(element, ticketKey, pullRequests);
                collectBranches(element, branches);
            }
        }
        Map<String, LinkedCommit> commits = new LinkedHashMap<>();
        if (hasRepositories) {
            for (JsonNode element : detail(access, issueId, "repository")) {
                collectCommits(element, commits);
            }
        }
        return new DevStatusLinks(pullRequests, new ArrayList<>(commits.values()), branches);
    }

    private static boolean advertises(JsonNode summary, String dataType) {
        return summary.path(dataType).path("byInstanceType").has(APPLICATION_TYPE);
    }

    private JsonNode detail(JiraAccessContext access, String issueId, String dataType) {
        JsonNode body = get(access, access.siteBaseUrl() + DETAIL_URL, issueId, APPLICATION_TYPE, dataType);
        JsonNode detail = body.path("detail");
        if (!detail.isArray() || detail.isEmpty()
                || !APPLICATION_TYPE.equals(detail.get(0).path("_instance").path("type").asString())) {
            throw new DevStatusUnrecognisedException();
        }
        return detail;
    }

    private static void collectPullRequests(JsonNode element, String ticketKey, List<LinkedPullRequest> out) {
        for (JsonNode pr : element.path("pullRequests")) {
            String url = pr.path("url").asString(null);
            if (!AdoRepoRef.isTrustedUrl(url)) {
                continue;
            }
            String sourceBranch = pr.path("source").path("branch").asString(null);
            out.add(new LinkedPullRequest(pr.path("repositoryId").asString(null), pr.path("id").asString(null),
                    pr.path("name").asString(null), pr.path("status").asString(null), url, sourceBranch,
                    pr.path("destination").path("branch").asString(null),
                    TicketBranchRule.isTicketBranch(sourceBranch, ticketKey), null));
        }
    }

    private static void collectBranches(JsonNode element, List<String> out) {
        for (JsonNode branch : element.path("branches")) {
            if (AdoRepoRef.isTrustedUrl(branch.path("url").asString(null)) && branch.hasNonNull("name")) {
                out.add(branch.get("name").asString());
            }
        }
    }

    private static void collectCommits(JsonNode element, Map<String, LinkedCommit> out) {
        for (JsonNode repository : element.path("repositories")) {
            for (JsonNode commit : repository.path("commits")) {
                String url = commit.path("url").asString(null);
                var authoredAt = JiraTimestamps.parse(commit.path("authorTimestamp").asString(null));
                if (!commit.hasNonNull("id") || !AdoRepoRef.isTrustedUrl(url) || authoredAt == null) {
                    continue;
                }
                String sha = commit.get("id").asString();
                out.putIfAbsent(sha, new LinkedCommit(sha, commit.path("message").asString(null),
                        commit.path("author").path("name").asString(null), authoredAt, url));
            }
        }
    }

    private JsonNode get(JiraAccessContext access, String uriTemplate, Object... uriVariables) {
        String basic = Base64.getEncoder().encodeToString(
                (access.email() + ":" + access.apiToken()).getBytes(StandardCharsets.UTF_8));
        JsonNode body;
        try {
            body = restClient.get()
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
        if (body == null) {
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
        }
        return body;
    }
}
