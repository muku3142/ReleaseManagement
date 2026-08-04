package com.evision.releasemgmt.adapters.jira;

import com.evision.releasemgmt.domain.auth.JiraAccessContext;
import com.evision.releasemgmt.domain.auth.JiraAuthFailureReason;
import com.evision.releasemgmt.domain.jira.JiraProjectSummary;
import com.evision.releasemgmt.domain.jira.JiraTicketReadException;
import com.evision.releasemgmt.domain.jira.JiraSprint;
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
import java.util.Comparator;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class JiraProjectAdapter implements JiraProjectPort {

    private static final int MAX_PROJECT_SEARCH_PAGES = 50;
    private static final int PROJECT_SEARCH_PAGE_SIZE = 50;
    private static final int MAX_AGILE_PAGES = 50;
    private static final int AGILE_PAGE_SIZE = 50;

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
    public List<JiraSprint> listSprints(JiraAccessContext access, String projectId) {
        try {
            List<JiraSprintPayload> collected = new ArrayList<>();
            Set<Long> seenSprintIds = new HashSet<>();
            for (Long boardId : scrumBoardIds(access, projectId)) {
                for (JiraSprintPayload sprint : sprintsOnBoard(access, boardId)) {
                    // A sprint is reachable from more than one board, so the same sprint can come
                    // back twice when a project has several boards.
                    if (sprint.id() != null && seenSprintIds.add(sprint.id())) {
                        collected.add(sprint);
                    }
                }
            }
            // Most recent first: a release is cut from recent work, so the sprint the release
            // manager wants is nearly always at the top. Sprints with no start date sort last.
            collected.sort(Comparator
                    .comparing(JiraSprintPayload::startDate,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(JiraSprintPayload::id, Comparator.reverseOrder()));
            List<JiraSprint> sprints = new ArrayList<>();
            for (JiraSprintPayload payload : collected) {
                sprints.add(new JiraSprint(String.valueOf(payload.id()), payload.name(), payload.state()));
            }
            return sprints;
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

    // Only scrum boards are asked for sprints: the Agile API rejects the sprint endpoint on a
    // kanban board, and filtering by board type here avoids provoking that error at all.
    private List<Long> scrumBoardIds(JiraAccessContext access, String projectId) {
        List<Long> boardIds = new ArrayList<>();
        int startAt = 0;
        for (int page = 0; page < MAX_AGILE_PAGES; page++) {
            JiraBoardPage body = restClient.get()
                    .uri(access.siteBaseUrl() + "/rest/agile/1.0/board?projectKeyOrId=" + projectId
                            + "&startAt=" + startAt + "&maxResults=" + AGILE_PAGE_SIZE)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth(access))
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(JiraBoardPage.class);
            List<JiraBoardPayload> values = body == null || body.values() == null ? List.of() : body.values();
            for (JiraBoardPayload board : values) {
                if (board.id() != null && "scrum".equalsIgnoreCase(board.type())) {
                    boardIds.add(board.id());
                }
            }
            if (values.isEmpty() || Boolean.TRUE.equals(body.isLast())) {
                return boardIds;
            }
            startAt += values.size();
        }
        throw new JiraTicketReadException(JiraAuthFailureReason.RESULT_SET_TOO_LARGE);
    }

    private List<JiraSprintPayload> sprintsOnBoard(JiraAccessContext access, Long boardId) {
        List<JiraSprintPayload> sprints = new ArrayList<>();
        int startAt = 0;
        for (int page = 0; page < MAX_AGILE_PAGES; page++) {
            JiraSprintPage body = restClient.get()
                    // Future sprints are excluded: they hold nothing shippable yet.
                    .uri(access.siteBaseUrl() + "/rest/agile/1.0/board/" + boardId
                            + "/sprint?state=active,closed&startAt=" + startAt
                            + "&maxResults=" + AGILE_PAGE_SIZE)
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + basicAuth(access))
                    .header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                    .retrieve()
                    .body(JiraSprintPage.class);
            List<JiraSprintPayload> values = body == null || body.values() == null ? List.of() : body.values();
            sprints.addAll(values);
            if (values.isEmpty() || Boolean.TRUE.equals(body.isLast())) {
                return sprints;
            }
            startAt += values.size();
        }
        throw new JiraTicketReadException(JiraAuthFailureReason.RESULT_SET_TOO_LARGE);
    }

    private String basicAuth(JiraAccessContext access) {
        return Base64.getEncoder().encodeToString(
                (access.email() + ":" + access.apiToken()).getBytes(StandardCharsets.UTF_8));
    }
}
