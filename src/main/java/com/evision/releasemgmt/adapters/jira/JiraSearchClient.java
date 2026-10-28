package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

final class JiraSearchClient {

    private static final String UNKNOWN_KEY_MESSAGE = "An issue with key";

    private final RestClient restClient;
    private final int pageSize;
    private final int maxPages;

    JiraSearchClient(RestClient restClient, int pageSize, int maxPages) {
        this.restClient = restClient;
        this.pageSize = pageSize;
        this.maxPages = maxPages;
    }

    List<JsonNode> search(JiraAccessContext access, String jql, List<String> fieldIds, int maxIssues) {
        String basic = Base64.getEncoder()
                .encodeToString((access.email() + ":" + access.apiToken()).getBytes(StandardCharsets.UTF_8));
        int requestedPageSize = Math.min(pageSize, maxIssues);
        try {
            List<JsonNode> results = new ArrayList<>();
            String pageToken = null;
            int pagesFetched = 0;
            do {
                JqlSearchPage page = restClient.post()
                        .uri(access.siteBaseUrl() + "/rest/api/3/search/jql")
                        .header(HttpHeaders.AUTHORIZATION, "Basic " + basic)
                        .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(new JqlSearchRequest(jql, fieldIds, requestedPageSize, pageToken))
                        .retrieve()
                        .body(JqlSearchPage.class);
                if (page == null) {
                    throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
                }
                if (page.issues() != null) {
                    for (JsonNode issue : page.issues()) {
                        if (results.size() >= maxIssues) {
                            return results;
                        }
                        results.add(issue);
                    }
                }
                if (results.size() >= maxIssues) {
                    return results;
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
        } catch (HttpClientErrorException.BadRequest e) {
            // For `key = X` Jira answers an unknown key with 400, not an empty page; that one message means "no match".
            if (e.getResponseBodyAsString().contains(UNKNOWN_KEY_MESSAGE)) {
                return List.of();
            }
            throw new JiraTicketReadException(JiraAuthFailureReason.JIRA_ERROR);
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
}
