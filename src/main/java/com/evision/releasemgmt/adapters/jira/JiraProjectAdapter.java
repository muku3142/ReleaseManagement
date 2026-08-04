package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraProjectSummary;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.JiraVersion;
import com.evision.releasemgmt.ports.JiraProjectPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Component
public class JiraProjectAdapter implements JiraProjectPort {

    private static final int MAX_PROJECT_SEARCH_PAGES = 50;
    private static final int PROJECT_SEARCH_PAGE_SIZE = 50;

    private final RestClient restClient;

    public JiraProjectAdapter(RestClient jiraRestClient) {
        this.restClient = jiraRestClient;
    }

    @Override
    public List<JiraProjectSummary> listProjects(JiraAccessContext access) {
        try {
            List<JiraProjectSummary> projects = new ArrayList<>();
            int startAt = 0;
            // /project/search is offset-paginated, not cursor-paginated - it was not part of
            // the /search deprecation, so the cursor pattern used for ticket search does not apply here.
            for (int page = 0; page < MAX_PROJECT_SEARCH_PAGES; page++) {
                JiraProjectSearchPage body = restClient.get()
                        .uri(access.siteBaseUrl() + "/rest/api/3/project/search?startAt=" + startAt
                                + "&maxResults=" + PROJECT_SEARCH_PAGE_SIZE)
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth(access))
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .retrieve()
                        .body(JiraProjectSearchPage.class);
                List<JiraProjectPayload> values = body == null || body.values() == null
                        ? List.of()
                        : body.values();
                if (values.isEmpty()) {
                    return projects;
                }
                for (JiraProjectPayload payload : values) {
                    projects.add(new JiraProjectSummary(payload.id(), payload.key(), payload.name()));
                }
                startAt += values.size();
                if (Boolean.TRUE.equals(body.isLast())
                        || (body.total() != null && projects.size() >= body.total())) {
                    return projects;
                }
            }
            throw new JiraTicketReadException(JiraAuthFailureReason.RESULT_SET_TOO_LARGE);
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

    @Override
    public List<JiraVersion> listVersions(JiraAccessContext access, String projectId) {
        try {
            JiraVersionPayload[] body = restClient.get()
                    .uri(access.siteBaseUrl() + "/rest/api/3/project/" + projectId + "/versions")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth(access))
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(JiraVersionPayload[].class);
            if (body == null) {
                return List.of();
            }
            List<JiraVersion> versions = new ArrayList<>();
            for (JiraVersionPayload payload : body) {
                versions.add(new JiraVersion(payload.id(), payload.name()));
            }
            return versions;
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

    private String basicAuth(JiraAccessContext access) {
        return Base64.getEncoder().encodeToString(
                (access.email() + ":" + access.apiToken()).getBytes(StandardCharsets.UTF_8));
    }
}
